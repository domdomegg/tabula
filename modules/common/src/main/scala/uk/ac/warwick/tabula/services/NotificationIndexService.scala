package uk.ac.warwick.tabula.services

import uk.ac.warwick.tabula.data.model.Notification
import java.io.File
import org.apache.lucene.analysis.Analyzer
import org.joda.time.DateTime
import org.apache.lucene.document.Document
import uk.ac.warwick.tabula.data.NotificationDao
import uk.ac.warwick.spring.Wire
import org.apache.lucene.analysis.standard.StandardAnalyzer
import uk.ac.warwick.userlookup.User
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.hibernate.ObjectNotFoundException
import javax.persistence.DiscriminatorValue
import org.apache.lucene.search.{FieldDoc, SortField, Sort, TermQuery}
import org.apache.lucene.index.Term
import uk.ac.warwick.tabula.JavaImports._

class RecipientNotification(val notification: Notification[_,_], val recipient: User) {
	def id = s"${notification.id}-${recipient.getUserId}"
}

trait NotificationIndexService {
	def indexFrom(startDate: DateTime): Unit
}

trait NotificationQueryMethods { self: NotificationIndexServiceImpl =>
	def userStream(req: ActivityStreamRequest): PagingSearchResultItems[Notification[_,_]] = {
		val user = req.user
		val query = new TermQuery(new Term("recipient", user.getUserId))
		val sort = new Sort(new SortField(UpdatedDateField, SortField.Type.LONG, true))
		val fieldDoc = req.pagination.map { p => new FieldDoc(p.lastDoc, Float.NaN, Array(p.lastField:JLong)) }
		val token = req.pagination.map { _.token }
		val result = search(query, req.max, sort, fieldDoc, token)
		result.transformAll[Notification[_,_]] {
			docs => docs.flatMap(toNotification)
		}
	}
}

/**
 */
@Service
class NotificationIndexServiceImpl extends AbstractIndexService[RecipientNotification] with NotificationIndexService with NotificationQueryMethods {
	var dao = Wire[NotificationDao]
	var userLookup = Wire[UserLookupService]

	@Value("${filesystem.index.notifications.dir}") override var indexPath: File = _

	override val IdField: String = "id"
	override val UpdatedDateField: String = "created"
	override val analyzer: Analyzer = createAnalyzer
	override val IncrementalBatchSize: Int = 5000
	override val MaxBatchSize: Int = 1000000

	private def createAnalyzer = new StandardAnalyzer(LuceneVersion)

	protected def toNotification(doc: Document): Option[Notification[_,_]] =
		for {
			id <- documentValue(doc, "notification")
			notification <- dao.getById(id)
		}	yield notification

	override protected def toItems(docs: Seq[Document]): Seq[RecipientNotification] =
		for {
			doc <- docs
			notification <- toNotification(doc)
			userId <- documentValue(doc, "recipient")
			recipient <- Option(userLookup.getUserByUserId(userId))
		}	yield new RecipientNotification(notification, recipient)

	override protected def toDocuments(item: RecipientNotification): Seq[Document] = {
		val recipient = item.recipient
		val notification = item.notification
		val doc = new Document

		val notificationType = notification.getClass.getAnnotation(classOf[DiscriminatorValue]).value()

		doc.add(plainStringField(IdField, item.id))
		doc.add(plainStringField("notification", notification.id))
		doc.add(plainStringField("recipient", recipient.getUserId))
		doc.add(plainStringField("notificationType", notificationType))
		doc.add(dateField(UpdatedDateField, notification.created))
		Seq(doc)
	}

	override protected def getId(item: RecipientNotification) = item.id

	override protected def getUpdatedDate(item: RecipientNotification) = item.notification.created

	override protected def listNewerThan(startDate: DateTime, batchSize: Int) =
		dao.recent(startDate).take(batchSize).flatMap { notification =>
			try {
				notification.recipients.map { user => new RecipientNotification(notification, user) }
			} catch {
				// Can happen if reference to an entity has since been deleted, e.g.
				// a submission is resubmitted and the old submission is removed. Skip this notification.
				case onf: ObjectNotFoundException => Nil
			}
		}

}