package uk.ac.warwick.tabula.services

import java.io.File
import java.io.FileNotFoundException
import scala.collection.JavaConversions._
import scala.collection.JavaConverters._
import org.apache.lucene.analysis._
import org.apache.lucene.document.Field._
import org.apache.lucene.document._
import org.apache.lucene.index.FieldInfo.IndexOptions
import org.apache.lucene.index._
import org.apache.lucene.queryparser.classic.QueryParser
import org.apache.lucene.search.BooleanClause.Occur
import org.apache.lucene.search._
import org.apache.lucene.store.FSDirectory
import org.apache.lucene.util.Version
import org.joda.time.DateTime
import org.joda.time.Duration
import org.springframework.beans.factory.annotation._
import org.springframework.beans.factory.InitializingBean
import org.springframework.stereotype.Component
import uk.ac.warwick.tabula.data.Transactions._
import uk.ac.warwick.tabula.JavaImports._
import uk.ac.warwick.tabula.data.model._
import uk.ac.warwick.tabula.helpers.Closeables._
import uk.ac.warwick.tabula.helpers.Stopwatches._
import uk.ac.warwick.tabula.helpers._
import uk.ac.warwick.tabula.helpers.DateTimeOrdering._
import uk.ac.warwick.userlookup.User
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.springframework.beans.factory.DisposableBean
import org.apache.lucene.analysis.core._
import org.apache.lucene.analysis.miscellaneous._
import org.apache.lucene.search.SearcherLifetimeManager.PruneByAge
import uk.ac.warwick.spring.Wire

case class PagedAuditEvents(val docs: Seq[AuditEvent], private val lastscore: Option[ScoreDoc], val token: Long, val total: Int) {
	// need this pattern matcher as brain-dead IndexSearcher.searchAfter returns an object containing ScoreDocs,
	// and expects a ScoreDoc in its method signature, yet in its implementation throws an exception unless you
	// pass a specific subclass of FieldDoc.
	def last: Option[FieldDoc] = lastscore match {
		case None => None
		case Some(f:FieldDoc) => Some(f)
		case _ => throw new ClassCastException("Lucene did not return an Option[FieldDoc] as expected")
	}
}

trait AuditEventNoteworthySubmissionsService {
	final val DefaultMaxEvents = 50
	
	def submissionsForModules(modules: Seq[Module], last: Option[ScoreDoc], token: Option[Long], max: Int = DefaultMaxEvents): PagedAuditEvents
	def noteworthySubmissionsForModules(modules: Seq[Module], last: Option[ScoreDoc], token: Option[Long], max: Int = DefaultMaxEvents): PagedAuditEvents
}

/**
 * Methods for querying stuff out of the index. Separated out from
 * the main index service into this trait so they're easier to find.
 * Possibly the indexer and the index querier should be separate classes
 * altogether.
 */
trait AuditEventQueryMethods extends AuditEventNoteworthySubmissionsService { self: AuditEventIndexService =>

	def student(user: User) = search(termQuery("students", user.getWarwickId))

	def findByUserId(usercode: String) = search(termQuery("userId", usercode))
	
	def findPublishFeedbackEvents(dept: Department) = {
		val searchResults = search(all(
			termQuery("eventType", "PublishFeedback"),
			termQuery("department", dept.code)))
			.transformAll(toParsedAuditEvents)
			.filterNot { _.hadError }

		searchResults
	}
		
	
	def submissionsForModules(modules: Seq[Module], last: Option[ScoreDoc], token: Option[Long], max: Int = DefaultMaxEvents): PagedAuditEvents = {
		val moduleTerms = for (module <- modules) yield termQuery("module", module.id)
		
		val searchResults = search(
			query = all(termQuery("eventType", "SubmitAssignment"),
						some(moduleTerms:_*)
					),
			max = max,
			sort = reverseDateSort,
			last = last,
			token = token)
			
		new PagedAuditEvents(parsedAuditEvents(searchResults.results), searchResults.last, searchResults.token, searchResults.total)
	}

	def submissionsForAssignment(assignment: Assignment): Seq[AuditEvent] = search(
		query = all(
			termQuery("eventType", "SubmitAssignment"),
			termQuery("assignment", assignment.id))
	).transformAll(toParsedAuditEvents)

	
	def publishFeedbackForStudent(assignment: Assignment, student: User): Seq[AuditEvent] = search(
		query = all(
			termQuery("eventType", "PublishFeedback"),
			termQuery("students", student.getWarwickId()),
			termQuery("assignment", assignment.id)), 
			sort = reverseDateSort
	).transformAll(toParsedAuditEvents)
	
	def submissionForStudent(assignment: Assignment, student: User): Seq[AuditEvent] = search(
		query = all(
			termQuery("eventType", "SubmitAssignment"),
			termQuery("masqueradeUserId", student.getUserId()),
			termQuery("assignment", assignment.id)),
			sort = reverseDateSort
	).transformAll(toParsedAuditEvents)
	
	

	def noteworthySubmissionsForModules(modules: Seq[Module], last: Option[ScoreDoc], token: Option[Long], max: Int = DefaultMaxEvents): PagedAuditEvents = {
		val moduleTerms = for (module <- modules) yield termQuery("module", module.id)
		
		val searchResults = search(
			query = all(termQuery("eventType", "SubmitAssignment"),
						termQuery("submissionIsNoteworthy", "true"),
						some(moduleTerms:_*)
					),
			max = max,
			sort = reverseDateSort,
			last = last,
			token = token)
		
		new PagedAuditEvents(parsedAuditEvents(searchResults.results), searchResults.last, searchResults.token, searchResults.total)
	}

	/**
	 * Work out which submissions have been downloaded from the admin interface
	 * based on the audit events.
	 */
	def adminDownloadedSubmissions(assignment: Assignment): Seq[Submission] = {
		val assignmentTerm = termQuery("assignment", assignment.id)

		// find events where you downloaded all available submissions
		val allDownloaded = parsedAuditEvents(
			search(
				query = all(
					assignmentTerm, 
					termQuery("eventType", "DownloadAllSubmissions")
				),
				max = 1, sort = reverseDateSort // we only want the most recent one
			)
		)
		
		// take most recent event and find submissions made before then.
		val submissions1: Seq[Submission] = allDownloaded.headOption match {
			case None => Nil
			case Some(event) => {
				val latestDate = event.eventDate
				assignment.submissions.filter { _.submittedDate isBefore latestDate }
			}
		}

		// find events where selected submissions were downloaded
		val someDownloaded = parsedAuditEvents(search(
			all(assignmentTerm, termQuery("eventType", "DownloadSubmissions"))))
		val submissions2 = someDownloaded.flatMap(_.submissionIds).flatMap(id => assignment.submissions.find(_.id == id))

		// find events where individual submissions were downloaded
		val individualDownloads = parsedAuditEvents(
				search(all(assignmentTerm, termQuery("eventType", "AdminGetSingleSubmission"))))
		val submissions3 = individualDownloads.flatMap(_.submissionId).flatMap(id => assignment.submissions.find((_.id == id)))
		(submissions1 ++ submissions2 ++ submissions3).distinct
	}

	def feedbackDownloads(assignment: Assignment) = {
		search(all(
			termQuery("eventType", "DownloadFeedback"),
			termQuery("assignment", assignment.id)))
			.transformAll(toItems)
			.filterNot { _.hadError }
			.map( whoDownloaded => {
				(whoDownloaded.masqueradeUserId, whoDownloaded.eventDate)
			})
	}
	
	def latestAssignmentEvent(assignment: Assignment, eventName: String) = {
		search(all(
			termQuery("eventType", "ViewOnlineFeedback"),
			termQuery("assignment", assignment.id)))
			.transformAll(toItems)
			.filterNot { _.hadError }
			.map { user => (user.masqueradeUserId, user.eventDate) }
			.groupBy { case (userId, _) => userId }
			.map { case (_, events) => events.maxBy { case (_, eventDate) => eventDate } }
			.toSeq
	}

	def latestOnlineFeedbackViews(assignment: Assignment) = latestAssignmentEvent(assignment, "ViewOnlineFeedback")
	def latestDownloadFeedbackAsPdf(assignment: Assignment) = latestAssignmentEvent(assignment, "DownloadFeedbackAsPdf")

	def latestGenericFeedbackAdded(assignment: Assignment): Option[DateTime] = {
		search( all(
			termQuery("eventType", "GenericFeedback"),
			termQuery("assignment", assignment.id)),
			max = 1, sort = reverseDateSort)
			.transformAll(toItems)
			.map(latestTime => latestTime.eventDate)
			.headOption
	}

	def latestOnlineFeedbackAdded(assignment: Assignment) =
		search(all(
			termQuery("eventType", "OnlineFeedback"),
			termQuery("assignment", assignment.id)))
			.transformAll(toParsedAuditEvents)
			.filterNot { _.hadError }
			.flatMap(auditEvent => auditEvent.students.map( student => (student, auditEvent.eventDate)))
			.groupBy( _._1)
			.map(x => (x._2.maxBy(_._2)))
			.toSeq
			
	def whoDownloadedFeedback(assignment: Assignment) =
		search(all(
			termQuery("eventType", "DownloadFeedback"),
			termQuery("assignment", assignment.id)))
			.transformAll(toItems)
			.filterNot { _.hadError }
			.map { _.masqueradeUserId }
			.filterNot { _ == null }
			.distinct

	def mapToAssignments(results: RichSearchResults) = 
		results.transformAll(toParsedAuditEvents)
		.flatMap(_.assignmentId)
		.flatMap(assignmentService.getAssignmentById)

	/**
	 * Return the most recently created assignment for this moodule
	 */
	def recentAssignment(module: Module): Option[Assignment] = {
		mapToAssignments(search(query = all(
			termQuery("eventType", "AddAssignment"),
			termQuery("module", module.id)),
			max = 1,
			sort = reverseDateSort)).headOption
	}

	def recentAssignment(department: Department): Option[Assignment] = {
		mapToAssignments(search(query = all(
			termQuery("eventType", "AddAssignment"),
			termQuery("department", department.code)),
			max = 1,
			sort = reverseDateSort)).headOption
	}

	def getAssignmentCreatedDate(assignment: Assignment): Option[DateTime] = {
		search(all(term("eventType" -> "AddAssignment"), term("assignment" -> assignment.id)))
			.transform { doc: Document =>
				doc.getField("eventDate") match {
					case field: StoredField if field.numericValue() != null => {
						Some(new DateTime(field.numericValue()))
					}
					case _ => {
						None
					}
				}
			}
			.headOption
	}
}

/**
 * Indexes audit events using Lucene, and does searches on it.
 *
 * TODO Split out indexing from searching, it's a big mess.
 *      Maybe put searches in AuditEventService.
 */
@Component
class AuditEventIndexService extends AbstractIndexService[AuditEvent] with AuditEventQueryMethods {
	// largest batch of event items we'll load in at once.
	final override val MaxBatchSize = 100000

	// largest batch of event items we'll load in at once during scheduled incremental index.
	final override val IncrementalBatchSize = 1000

	// Fields containing IDs and things that should be passed
	// around as-is.
	// NOTE: analyzer was switched to do token analysis by default,
	//    so this particular list is not used.
	val tokenFields = Set(
		"eventType",
		"department",
		"module",
		"assignment",
		"submission",
		"feedback",
		"studentId")

	// Fields that are a space-separated list of tokens.
	// A list of IDs needs to be in here or else the whole thing
	// will be treated as one value.
	val tokenListFields = Set(
		"students",
		"feedbacks",
		"submissions",
		"attachments")

	@Autowired var service: AuditEventService = _
	@Autowired var assignmentService: AssignmentService = _
	@Value("${filesystem.index.audit.dir}") override var indexPath: File = _
	@Value("${audit.index.weeksbacklog}") var weeksBacklog: Int = _

	override val analyzer = {
		//val standard = new StandardAnalyzer(LuceneVersion)
		val token = new KeywordAnalyzer()
		val whitespace: Analyzer = new WhitespaceAnalyzer(LuceneVersion)

		val tokenListMappings = tokenListFields.map(field => (field -> whitespace))
		//val tokenMappings = tokenFields.map(field=> (field -> token))
		val mappings = (tokenListMappings /* ++ tokenMappings*/ ).toMap.asJava

		new PerFieldAnalyzerWrapper(token, mappings)
	}
	
	override val IdField = "id"
	override def getId(item: AuditEvent) = item.id.toString
	
	override val UpdatedDateField = "eventDate"
	override def getUpdatedDate(item: AuditEvent) = item.eventDate
	
	override def listNewerThan(startDate: DateTime, batchSize: Int) =
		service.listNewerThan(startDate, batchSize).filter { _.eventStage == "before" }

	/**
	 * Convert a list of Lucene Documents to a list of AuditEvents.
	 * Any events not found in the database will be returned as placeholder
	 * events with whatever data we kept in the Document, just in case
	 * an event went missing and we'd like to see the data.
	 */
	protected def toItems(docs: Seq[Document]): Seq[AuditEvent] = {
		// Pair Documents up with the contained ID if present
		val docIds = docs.map { doc =>
			(doc -> documentValue(doc, IdField).map{ _.toLong })
		}
		val ids = docIds.flatMap {
			case (_, id) => id
		}
		// Most events should be in the DB....
		val eventsMap = service.getByIds(ids)
			.map { event => (event.id, event) }
			.toMap

		// But we will return placeholder items for any IDs that weren't.
		docIds.map {
			case (doc, id) =>
				id.flatMap(eventsMap.get).getOrElse(placeholderEventFromDoc(doc))
		}
	}

	/** A placeholder AuditEvent to display if a Document has no matching event in the DB. */
	private def placeholderEventFromDoc(doc: Document) = {
		val event = AuditEvent()
		event.eventStage = "before"
		event.data = "{}" // We can't restore this if it's not from the db
		documentValue(doc, IdField).foreach { id => event.id = id.toLong }
		documentValue(doc, "eventId").foreach { id => event.eventId = id }
		documentValue(doc, "userId").foreach { id => event.userId = id }
		documentValue(doc, "masqueradeUserId").foreach { id => event.masqueradeUserId = id }
		documentValue(doc, "eventType").foreach { typ => event.eventType = typ }
		documentValue(doc, UpdatedDateField).foreach { ts => event.eventDate = new DateTime(ts.toLong) }
		event
	}

	protected def toParsedAuditEvents(doc: Seq[Document]): Seq[AuditEvent] = toItems(doc).map { event =>
		event.parsedData = service.parseData(event.data)
		event.related.map { e =>
			e.parsedData = service.parseData(e.data)
		}
		event
	}

	protected def auditEvents(results: RichSearchResults) = results.transformAll(toItems)
	protected def parsedAuditEvents(results: RichSearchResults) = results.transformAll(toParsedAuditEvents)

	/**
	 * TODO reuse one Document and set of Fields for all items
	 */
	protected def toDocument(item: AuditEvent): Document = {
		val doc = new Document

		if (item.related == null || item.related.isEmpty) {
			service.addRelated(item)
		}

		doc add plainStringField(IdField, item.id.toString)
		if (item.eventId != null) { // null for old events
			doc add plainStringField("eventId", item.eventId)
		}
		if (item.userId != null) // system-run actions have no user
			doc add plainStringField("userId", item.userId)
		if (item.masqueradeUserId != null)
			doc add plainStringField("masqueradeUserId", item.masqueradeUserId)
		doc add plainStringField("eventType", item.eventType)

		// add data from all stages of the event, before and after.
		for (i <- item.related) {
			service.parseData(i.data) match {
				case None => // no valid JSON
				case Some(data) => addDataToDoc(data, doc)
			}
		}

		doc add dateField(UpdatedDateField, item.eventDate)
		doc
	}

	def openQuery(queryString: String, start: Int, count: Int) = {
		logger.info("Opening query: " + queryString)
		val query = parser.parse(queryString)
		val docs = search(query,
			sort = new Sort(new SortField(UpdatedDateField, SortField.Type.LONG, true)),
			offset = start,
			max = count)
		docs.transformAll(toItems)
	}
	
	private def addFieldToDoc(field: String, data: Map[String, Any], doc: Document) = data.get(field) match  {
		case Some(value: String) => {
			doc add plainStringField(field, value, isStored = false)
		}
		case Some(value: Boolean) => {
			doc add plainStringField(field, value.toString, isStored = false)
		}
		case _ => // missing or not a string
	}
	
	private def addSequenceToDoc(field: String, data: Map[String, Any], doc: Document) = data.get(field).collect {
		case ids: JList[_] => doc add seqField(field, ids.asScala)
		case ids: Seq[_] => doc add seqField(field, ids)
		case ids: Array[String] => doc add seqField(field, ids)
		case other: AnyRef => logger.warn("Collection field " + field + " was unexpected type: " + other.getClass.getName)
		case _ =>
	}
	
	val SingleDataFields = Seq("submission", "feedback", "assignment", "module", "department", "studentId", "submissionIsNoteworthy")
	val SequenceDataFields = Seq("students", "attachments")

	// pick items out of the auditevent JSON and add them as document fields.
	private def addDataToDoc(data: Map[String, Any], doc: Document) = {
		SingleDataFields.foreach(addFieldToDoc(_, data, doc))
		
		// sequence-type fields
		SequenceDataFields.foreach(addSequenceToDoc(_, data, doc))
	}
}

trait AuditEventIndexServiceComponent {
	def auditEventIndexService: AuditEventIndexService
}

trait AutowiringAuditEventIndexServiceComponent extends AuditEventIndexServiceComponent {
	var auditEventIndexService = Wire[AuditEventIndexService]
}