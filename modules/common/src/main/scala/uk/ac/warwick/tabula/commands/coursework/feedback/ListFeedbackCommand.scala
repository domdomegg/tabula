package uk.ac.warwick.tabula.commands.coursework.feedback

import uk.ac.warwick.spring.Wire
import uk.ac.warwick.tabula.permissions._
import uk.ac.warwick.tabula.commands._
import uk.ac.warwick.tabula.data.model.{FeedbackForSits, Assignment, Module, Feedback}
import uk.ac.warwick.tabula.helpers.Futures._
import uk.ac.warwick.tabula.services.AuditEventIndexService
import uk.ac.warwick.tabula.services.UserLookupService
import org.joda.time.DateTime
import uk.ac.warwick.userlookup.User

import scala.concurrent.Await
import scala.concurrent.duration._

class ListFeedbackCommand(val module: Module, val assignment: Assignment) extends Command[ListFeedbackResult] with ReadOnly with Unaudited {
	mustBeLinked(assignment, module)
	PermissionCheck(Permissions.AssignmentFeedback.Read, assignment)

	var auditIndexService = Wire[AuditEventIndexService]
	var userLookup = Wire[UserLookupService]

	override def applyInternal() = Await.result(
		for {
			downloads <- auditIndexService.feedbackDownloads(assignment).map(_.map(userIdToUser))
			latestOnlineViews <- auditIndexService.latestOnlineFeedbackViews(assignment).map(_.map(userIdToUser))
			latestOnlineAdded <- auditIndexService.latestOnlineFeedbackAdded(assignment).map(_.map(warwickIdToUser))
			latestGenericFeedback <- auditIndexService.latestGenericFeedbackAdded(assignment)
		} yield ListFeedbackResult(
			downloads,
			latestOnlineViews,
			latestOnlineAdded,
			latestGenericFeedback
		)
	, 15.seconds)

	def userIdToUser(tuple: (String, DateTime)) = tuple match {
		case (id, date) => (userLookup.getUserByUserId(id), date)
	}

	def warwickIdToUser(tuple: (String, DateTime)) = tuple match {
		case (id, date) => (userLookup.getUserByWarwickUniId(id), date)
	}

	override def describe(d: Description) =	d.assignment(assignment)
}

case class ListFeedbackResult(
	downloads: Seq[(User, DateTime)],
	latestOnlineViews: Seq[(User, DateTime)],
	latestOnlineAdded: Seq[(User, DateTime)],
  latestGenericFeedback: Option[DateTime]
)

case class FeedbackListItem(feedback: Feedback, downloaded: Boolean, onlineViewed: Boolean, feedbackForSits: FeedbackForSits)