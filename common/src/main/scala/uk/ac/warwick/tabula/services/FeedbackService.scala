package uk.ac.warwick.tabula.services
import org.hibernate.FetchMode

import scala.collection.JavaConverters._
import org.springframework.stereotype.Service
import uk.ac.warwick.tabula.data.{AutowiringFeedbackDaoComponent, Daoisms, FeedbackDaoComponent}
import uk.ac.warwick.tabula.data.Transactions._
import uk.ac.warwick.tabula.data.model._
import uk.ac.warwick.tabula.helpers.ExecutionContexts.global
import uk.ac.warwick.tabula.helpers.{Futures, Logging}
import uk.ac.warwick.userlookup.User
import uk.ac.warwick.spring.Wire
import uk.ac.warwick.tabula.helpers.cm2.FeedbackMetadata
import uk.ac.warwick.tabula.services.elasticsearch.{AuditEventQueryServiceComponent, AutowiringAuditEventQueryServiceComponent}

import scala.concurrent.Await
import scala.concurrent.duration._

trait FeedbackService {
	def getStudentFeedback(assessment: Assessment, usercode: String): Option[Feedback]
	def loadFeedbackForAssignment(assignment: Assignment): Seq[AssignmentFeedback]
	def countPublishedFeedback(assignment: Assignment): Int
	def getUsersForFeedback(assignment: Assignment): Seq[(String, User)]
	def getAssignmentFeedbackByUsercode(assignment: Assignment, usercode: String): Option[AssignmentFeedback]
	def getAssignmentFeedbackById(feedbackId: String): Option[AssignmentFeedback]
	def getMarkerFeedbackById(markerFeedbackId: String): Option[MarkerFeedback]
	def getRejectedMarkerFeedbackByFeedback(feedback: Feedback): Seq[MarkerFeedback]
	def saveOrUpdate(feedback: Feedback)
	def saveOrUpdate(mark: Mark)
	def delete(feedback: Feedback)
	def save(feedback: MarkerFeedback)
	def delete(feedback: MarkerFeedback)
	def getExamFeedbackMap(exam: Exam, users: Seq[User]): Map[User, ExamFeedback]
	def addAnonymousIds(feedbacks: Seq[AssignmentFeedback]): Seq[AssignmentFeedback]
	def getFeedbackMetadata(assignment: Assignment): FeedbackMetadata
	def getFeedbackMetadata(assignment: Assignment, students: Set[User]): FeedbackMetadata
}


abstract class AbstractFeedbackService extends FeedbackService with Daoisms with Logging {

	self: FeedbackDaoComponent with UserLookupComponent with AuditEventQueryServiceComponent with TaskSchedulerServiceComponent =>

	/* get users whose feedback is not published and who have not submitted work suspected
	 * of being plagiarised */
	def getUsersForFeedback(assignment: Assignment): Seq[(String, User)] = {
		val plagiarisedSubmissions = assignment.submissions.asScala.filter { submission => submission.suspectPlagiarised }
		val plagiarisedIds = plagiarisedSubmissions.map { _.usercode }
		val unreleasedIds = assignment.unreleasedFeedback.map { _.usercode }
		val unplagiarisedUnreleasedIds = unreleasedIds.filter { usercode => !plagiarisedIds.contains(usercode) }
		userLookup.getUsersByUserIds(unplagiarisedUnreleasedIds).toSeq
	}

	def getStudentFeedback(assessment: Assessment, usercode: String): Option[Feedback] = {
		assessment.findFullFeedback(usercode)
	}

	def loadFeedbackForAssignment(assignment: Assignment): Seq[AssignmentFeedback] =
		session.newCriteria[AssignmentFeedback]
  		.add(is("assignment", assignment))
			.setFetchMode("_marks", FetchMode.JOIN)
			.setFetchMode("markerFeedback", FetchMode.JOIN)
			.setFetchMode("markerFeedback.attachments", FetchMode.JOIN)
			.setFetchMode("markerFeedback.customFormValues", FetchMode.JOIN)
			.setFetchMode("outstandingStages", FetchMode.JOIN)
			.setFetchMode("customFormValues", FetchMode.JOIN)
			.setFetchMode("attachments", FetchMode.JOIN)
			.distinct
  		.seq

	def countPublishedFeedback(assignment: Assignment): Int = {
		session.createSQLQuery("""select count(*) from feedback where assignment_id = :assignmentId and released = 1""")
			.setString("assignmentId", assignment.id)
			.uniqueResult
			.asInstanceOf[Number].intValue
	}

	def getAssignmentFeedbackByUsercode(assignment: Assignment, usercode: String): Option[AssignmentFeedback] = transactional(readOnly = true) {
		feedbackDao.getAssignmentFeedbackByUsercode(assignment, usercode)
	}

	def getAssignmentFeedbackById(feedbackId: String): Option[AssignmentFeedback] = {
		feedbackDao.getAssignmentFeedback(feedbackId)
	}

	def getMarkerFeedbackById(markerFeedbackId: String): Option[MarkerFeedback] = {
		feedbackDao.getMarkerFeedback(markerFeedbackId)
	}

	def getRejectedMarkerFeedbackByFeedback(feedback: Feedback): Seq[MarkerFeedback] = {
		feedbackDao.getRejectedMarkerFeedbackByFeedback(feedback)
	}

	def delete(feedback: Feedback): Unit = transactional() {
		feedbackDao.delete(feedback)
	}

	def saveOrUpdate(feedback:Feedback){
		session.saveOrUpdate(feedback)
	}

	def saveOrUpdate(mark: Mark) {
		session.saveOrUpdate(mark)
	}

	def save(feedback: MarkerFeedback): Unit = transactional() {
		feedbackDao.save(feedback)
	}

	def delete(markerFeedback: MarkerFeedback): Unit = transactional() {
		// remove link to parent
		val parentFeedback = markerFeedback.feedback
		if (markerFeedback == parentFeedback.firstMarkerFeedback) parentFeedback.firstMarkerFeedback = null
		else if (markerFeedback == parentFeedback.secondMarkerFeedback) parentFeedback.secondMarkerFeedback = null
		else if (markerFeedback == parentFeedback.thirdMarkerFeedback) parentFeedback.thirdMarkerFeedback = null
		parentFeedback.markerFeedback.remove(markerFeedback)
		saveOrUpdate(parentFeedback)
		feedbackDao.delete(markerFeedback)
	}

	def getExamFeedbackMap(exam: Exam, users: Seq[User]): Map[User, ExamFeedback] =
		feedbackDao.getExamFeedbackMap(exam, users)

	def addAnonymousIds(feedbacks: Seq[AssignmentFeedback]): Seq[AssignmentFeedback] = transactional() {
		val assignments = feedbacks.map(_.assignment).distinct
		if(assignments.length > 1) throw new IllegalArgumentException("Can only generate IDs for feedback from the same assignment")
		assignments.headOption.foreach(assignment => {
			val nextIndex = feedbackDao.getLastAnonIndex(assignment) + 1

			// add IDs to any feedback that doesn't already have one
			for((feedback, i) <- feedbacks.filter(_.anonymousId.isEmpty).zipWithIndex) {
				feedback.anonymousId = Some(nextIndex + i)
				feedbackDao.save(feedback)
			}
		})
		feedbacks
	}

	def getFeedbackMetadata(assignment: Assignment): FeedbackMetadata = getFeedbackMetadata(assignment, Set())

	def getFeedbackMetadata(assignment: Assignment, students: Set[User]): FeedbackMetadata = {

		val allFeedback = if(students.nonEmpty) {
			val usercodes = students.map(_.getUserId)
			assignment.allFeedback.filter(f => usercodes.contains(f.usercode))
		} else {
			assignment.allFeedback
		}

		// The time to wait for a query to complete
		val timeout = 15.seconds

		// Wrap each future in Future.optionalTimeout, which will return None if it times out early
		val downloads = Futures.optionalTimeout(auditEventQueryService.feedbackDownloads(assignment, allFeedback), timeout)
		val latestOnlineViews = Futures.optionalTimeout(auditEventQueryService.latestOnlineFeedbackViews(assignment, allFeedback), timeout)
		val latestOnlineAdded = Futures.optionalTimeout(auditEventQueryService.latestOnlineFeedbackAdded(assignment), timeout)
		val latestGenericFeedback = Futures.optionalTimeout(auditEventQueryService.latestGenericFeedbackAdded(assignment), timeout)

		val result = for {
			downloads <- downloads
			latestOnlineViews <- latestOnlineViews
			latestOnlineAdded <- latestOnlineAdded
			latestGenericFeedback <- latestGenericFeedback
		} yield FeedbackMetadata(
			downloads.getOrElse(Nil),
			latestOnlineViews.getOrElse(Map.empty),
			latestOnlineAdded.getOrElse(Map.empty),
			latestGenericFeedback.flatten
		)

		// We arbitrarily wait a longer time for the result, safe in the knowledge that if they don't return in a reasonable
		// time then we've messed up.
		Await.result(result, timeout * 2)
	}

}

@Service(value = "feedbackService")
class FeedbackServiceImpl extends AbstractFeedbackService
	with AutowiringUserLookupComponent with AutowiringFeedbackDaoComponent with AutowiringAuditEventQueryServiceComponent with AutowiringTaskSchedulerServiceComponent

trait FeedbackServiceComponent {
	def feedbackService: FeedbackService
}

trait AutowiringFeedbackServiceComponent extends FeedbackServiceComponent {
	var feedbackService: FeedbackService = Wire[FeedbackService]
}
