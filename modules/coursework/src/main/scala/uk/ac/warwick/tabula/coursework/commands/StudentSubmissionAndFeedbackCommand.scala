package uk.ac.warwick.tabula.coursework.commands

import uk.ac.warwick.tabula.CurrentUser
import uk.ac.warwick.tabula.commands._
import uk.ac.warwick.tabula.coursework.commands.StudentSubmissionAndFeedbackCommand._
import uk.ac.warwick.tabula.data.HibernateHelpers
import uk.ac.warwick.tabula.data.model._
import uk.ac.warwick.tabula.data.model.forms.Extension
import uk.ac.warwick.tabula.data.model.notifications.coursework.{FeedbackPublishedNotification, FeedbackChangeNotification}
import uk.ac.warwick.tabula.events.NotificationHandling
import uk.ac.warwick.tabula.permissions.{CheckablePermission, Permissions}
import uk.ac.warwick.tabula.services.{AutowiringFeedbackServiceComponent, AutowiringSubmissionServiceComponent, FeedbackServiceComponent, SubmissionServiceComponent}
import uk.ac.warwick.tabula.system.permissions.{PermissionsChecking, PermissionsCheckingMethods, RequiresPermissionsChecking}
import uk.ac.warwick.userlookup.User

import scala.collection.JavaConverters._

object StudentSubmissionAndFeedbackCommand {
	case class StudentSubmissionInformation(
		submission: Option[Submission],
		feedback: Option[Feedback],
		extension: Option[Extension],
		isExtended: Boolean,
		extensionRequested: Boolean,
		canSubmit: Boolean,
		canReSubmit: Boolean
  )

	def apply(module: Module, assignment: Assignment, member: Member, viewingUser: CurrentUser) =
		new StudentMemberSubmissionAndFeedbackCommandInternal(module, assignment, member, viewingUser)
			with StudentMemberSubmissionAndFeedbackCommandPermissions
			with AutowiringFeedbackServiceComponent
			with AutowiringSubmissionServiceComponent
			with ComposableCommand[StudentSubmissionInformation]
			with Unaudited with ReadOnly

	def apply(module: Module, assignment: Assignment, user: CurrentUser) =
		new CurrentUserSubmissionAndFeedbackCommandInternal(module, assignment, user)
			with CurrentUserSubmissionAndFeedbackCommandPermissions
			with CurrentUserSubmissionAndFeedbackNotificationCompletion
			with AutowiringFeedbackServiceComponent
			with AutowiringSubmissionServiceComponent
			with ComposableCommand[StudentSubmissionInformation]
			with Unaudited with ReadOnly
}

trait StudentSubmissionAndFeedbackCommandState {
	self: FeedbackServiceComponent with SubmissionServiceComponent =>

	def module: Module
	def assignment: Assignment
	def studentUser: User
	def viewer: User

	lazy val feedback = feedbackService.getAssignmentFeedbackByUniId(assignment, studentUser.getWarwickId).filter(_.released)
	lazy val submission = submissionService.getSubmissionByUniId(assignment, studentUser.getWarwickId).filter { _.submitted }
}

trait StudentMemberSubmissionAndFeedbackCommandState extends StudentSubmissionAndFeedbackCommandState {
	self: FeedbackServiceComponent with SubmissionServiceComponent =>

	def studentMember: Member
	def currentUser: CurrentUser

	final lazy val studentUser = studentMember.asSsoUser
	final lazy val viewer = currentUser.apparentUser
}

trait CurrentUserSubmissionAndFeedbackCommandState extends StudentSubmissionAndFeedbackCommandState {
	self: FeedbackServiceComponent with SubmissionServiceComponent =>

	def currentUser: CurrentUser

	final lazy val studentUser = currentUser.apparentUser
	final lazy val viewer = currentUser.apparentUser
}

abstract class StudentMemberSubmissionAndFeedbackCommandInternal(module: Module, assignment: Assignment, val studentMember: Member, val currentUser: CurrentUser)
	extends StudentSubmissionAndFeedbackCommandInternal(module, assignment) with StudentMemberSubmissionAndFeedbackCommandState {
	self: FeedbackServiceComponent with SubmissionServiceComponent =>
}

abstract class CurrentUserSubmissionAndFeedbackCommandInternal(module: Module, assignment: Assignment, val currentUser: CurrentUser)
	extends StudentSubmissionAndFeedbackCommandInternal(module, assignment) with CurrentUserSubmissionAndFeedbackCommandState {
	self: FeedbackServiceComponent with SubmissionServiceComponent =>
}

abstract class StudentSubmissionAndFeedbackCommandInternal(val module: Module, val assignment: Assignment)
	extends CommandInternal[StudentSubmissionInformation] with StudentSubmissionAndFeedbackCommandState {
	self: FeedbackServiceComponent with SubmissionServiceComponent =>

	def applyInternal() = {
		val extension = assignment.extensions.asScala.find(_.isForUser(studentUser))

		// Log a ViewOnlineFeedback event if the student itself is viewing
		feedback.filter { _.universityId == viewer.getWarwickId }.foreach { feedback =>
			ViewOnlineFeedbackCommand(feedback).apply()
		}

		StudentSubmissionInformation(
			submission = submission,
			feedback = HibernateHelpers.initialiseAndUnproxy(feedback),
			extension = extension,

			isExtended = assignment.isWithinExtension(studentUser),
			extensionRequested = extension.isDefined && !extension.get.isManual,

			canSubmit = assignment.submittable(studentUser),
			canReSubmit = assignment.resubmittable(studentUser)
		)
	}

}

trait StudentMemberSubmissionAndFeedbackCommandPermissions extends RequiresPermissionsChecking with PermissionsCheckingMethods {
	self: StudentMemberSubmissionAndFeedbackCommandState =>

	def permissionsCheck(p: PermissionsChecking) {
		mustBeLinked(mandatory(assignment), mandatory(module))

		p.PermissionCheck(Permissions.Submission.Read, mandatory(studentMember))
		p.PermissionCheck(Permissions.Feedback.Read, mandatory(studentMember))
	}
}

trait CurrentUserSubmissionAndFeedbackCommandPermissions extends RequiresPermissionsChecking with PermissionsCheckingMethods {
	self: CurrentUserSubmissionAndFeedbackCommandState =>

	def permissionsCheck(p: PermissionsChecking) {
		mustBeLinked(mandatory(assignment), mandatory(module))

		var perms = collection.mutable.MutableList[CheckablePermission]()

		submission.foreach { submission => perms += CheckablePermission(Permissions.Submission.Read, Some(submission)) }
		feedback.foreach { feedback => perms += CheckablePermission(Permissions.Feedback.Read, Some(feedback)) }

		perms += CheckablePermission(Permissions.Submission.Create, Some(assignment))

		p.PermissionCheckAny(perms)
	}
}

trait CurrentUserSubmissionAndFeedbackNotificationCompletion extends CompletesNotifications[StudentSubmissionInformation] {

	self: NotificationHandling with StudentSubmissionAndFeedbackCommandState =>

	def notificationsToComplete(commandResult: StudentSubmissionInformation): CompletesNotificationsResult = {
		commandResult.feedback match {
			case Some(feedbackResult: AssignmentFeedback) =>
				CompletesNotificationsResult(
					notificationService.findActionRequiredNotificationsByEntityAndType[FeedbackPublishedNotification](feedbackResult) ++
						notificationService.findActionRequiredNotificationsByEntityAndType[FeedbackChangeNotification](feedbackResult),
					viewer
				)
			case _ =>
				EmptyCompletesNotificationsResult
		}
	}

}