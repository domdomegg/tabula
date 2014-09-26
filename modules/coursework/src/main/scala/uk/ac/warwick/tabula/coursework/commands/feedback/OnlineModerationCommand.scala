package uk.ac.warwick.tabula.coursework.commands.feedback

import org.joda.time.DateTime

import scala.collection.JavaConverters._
import uk.ac.warwick.tabula.data.model._
import uk.ac.warwick.tabula.CurrentUser
import uk.ac.warwick.tabula.commands.{UserAware, Notifies, Appliable, CommandInternal, ComposableCommand}
import uk.ac.warwick.tabula.services._
import uk.ac.warwick.tabula.data.AutowiringSavedFormValueDaoComponent
import org.springframework.validation.Errors
import uk.ac.warwick.tabula.data.model.{Notification, MarkerFeedback}
import uk.ac.warwick.tabula.data.model.MarkingState.{MarkingCompleted, Rejected}
import uk.ac.warwick.tabula.helpers.StringUtils._
import uk.ac.warwick.tabula.coursework.commands.assignments.FinaliseFeedbackCommand
import uk.ac.warwick.tabula.helpers.Logging
import uk.ac.warwick.userlookup.User
import uk.ac.warwick.tabula.data.model.notifications.ModeratorRejectedNotification

object OnlineModerationCommand {
	def apply(module: Module, assignment: Assignment, student: User, currentUser: CurrentUser) =
		new OnlineModerationCommand(module, assignment, student, currentUser)
			with ComposableCommand[MarkerFeedback]
			with OnlineFeedbackFormPermissions
			with MarkerFeedbackStateCopy
			with CopyFromFormFields
			with WriteToFormFields
			with ModerationRejectedNotifier
			with FinaliseFeedbackComponentImpl
			with AutowiringFeedbackServiceComponent
			with AutowiringFileAttachmentServiceComponent
			with AutowiringZipServiceComponent
			with AutowiringSavedFormValueDaoComponent
			with AutowiringUserLookupComponent
			with OnlineFeedbackFormDescription[MarkerFeedback] {
				override lazy val eventName = "OnlineMarkerFeedback"
			}
}

abstract class OnlineModerationCommand(module: Module, assignment: Assignment, student: User, currentUser: CurrentUser)
	extends AbstractOnlineFeedbackFormCommand(module, assignment, student, currentUser)
	with CommandInternal[MarkerFeedback] with Appliable[MarkerFeedback] with ModerationState with UserAware {

	self: FeedbackServiceComponent with FileAttachmentServiceComponent with ZipServiceComponent with MarkerFeedbackStateCopy
		with FinaliseFeedbackComponent =>

	val user = currentUser.apparentUser

	def markerFeedback = assignment.getMarkerFeedback(student.getWarwickId, user, SecondFeedback)

	copyState(markerFeedback, copyModerationFieldsFrom)

	def applyInternal(): MarkerFeedback = {

		// find the parent feedback or make a new one
		val parentFeedback = assignment.feedbacks.asScala.find(_.universityId == student.getWarwickId).getOrElse({
			val newFeedback = new Feedback
			newFeedback.assignment = assignment
			newFeedback.uploaderId = user.getWarwickId
			newFeedback.universityId = student.getWarwickId
			newFeedback.released = false
			newFeedback.createdDate = DateTime.now
			newFeedback
		})

		val firstMarkerFeedback = parentFeedback.retrieveFirstMarkerFeedback
		secondMarkerFeedback = parentFeedback.retrieveSecondMarkerFeedback
		val markerFeedback = Seq(firstMarkerFeedback, secondMarkerFeedback)

		// if the second-marker feedback is already rejected then do nothing - UI should prevent this
		if(secondMarkerFeedback.state != Rejected){
			if (approved) {
				finaliseFeedback(assignment, firstMarkerFeedback)
				secondMarkerFeedback.state = MarkingCompleted
			} else {
				markerFeedback.foreach(_.state = Rejected)
				copyModerationFieldsTo(secondMarkerFeedback)
			}
			parentFeedback.updatedDate = DateTime.now
			feedbackService.saveOrUpdate(parentFeedback)
			markerFeedback.foreach(feedbackService.save)
		}
		firstMarkerFeedback
	}

	def copyModerationFieldsFrom(markerFeedback: MarkerFeedback) {

		copyFrom(markerFeedback)

		if(markerFeedback.rejectionComments.hasText) {
			rejectionComments = markerFeedback.rejectionComments
		}

		approved = markerFeedback.state != Rejected
	}

	def copyModerationFieldsTo(markerFeedback: MarkerFeedback) {

		copyTo(markerFeedback)

		if(rejectionComments.hasText) {
			markerFeedback.rejectionComments = rejectionComments
		}
	}

	override def validate(errors: Errors){
		super.validate(errors)

		if (!Option(approved).isDefined)
			errors.rejectValue("approved", "markers.moderation.approved.notDefined")
		else if(!approved && !rejectionComments.hasText)
			errors.rejectValue("rejectionComments", "markers.moderation.rejectionComments.empty")
	}

}

trait FinaliseFeedbackComponent {
	def finaliseFeedback(assignment: Assignment, markerFeedback: MarkerFeedback)
}

trait FinaliseFeedbackComponentImpl extends FinaliseFeedbackComponent {
	def finaliseFeedback(assignment: Assignment, firstMarkerFeedback: MarkerFeedback) {
		val finaliseFeedbackCommand = new FinaliseFeedbackCommand(assignment, Seq(firstMarkerFeedback).asJava)
		finaliseFeedbackCommand.apply()
	}
}

trait ModerationState {
	var approved: Boolean = true
	var secondMarkerFeedback: MarkerFeedback = _
}

trait ModerationRejectedNotifier extends Notifies[MarkerFeedback, MarkerFeedback] {

	this: ModerationState with UserAware with UserLookupComponent with Logging =>

	def emit(rejectedFeedback: MarkerFeedback): Seq[Notification[MarkerFeedback, Unit]] = approved match {
		case false => Seq (
			Notification.init(new ModeratorRejectedNotification, user, Seq(secondMarkerFeedback))
		)
		case true => Nil
	}
}
