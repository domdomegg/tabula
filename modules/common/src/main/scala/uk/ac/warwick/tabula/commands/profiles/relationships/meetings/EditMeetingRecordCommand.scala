package uk.ac.warwick.tabula.commands.profiles.relationships.meetings

import org.joda.time.DateTime
import uk.ac.warwick.tabula.commands._
import uk.ac.warwick.tabula.data.model._
import uk.ac.warwick.tabula.data.model.notifications.profiles.meetingrecord.{EditedMeetingRecordApprovalNotification, MeetingRecordRejectedNotification}
import uk.ac.warwick.tabula.events.NotificationHandling
import uk.ac.warwick.tabula.services._
import uk.ac.warwick.tabula.services.attendancemonitoring.{AttendanceMonitoringMeetingRecordServiceComponent, AutowiringAttendanceMonitoringMeetingRecordServiceComponent}
import uk.ac.warwick.tabula.{AutowiringFeaturesComponent, FeaturesComponent}

import scala.collection.JavaConverters._

object EditMeetingRecordCommand {
	def apply(meetingRecord: MeetingRecord) =
		new EditMeetingRecordCommandInternal(meetingRecord)
			with AutowiringMeetingRecordServiceComponent
			with AutowiringFeaturesComponent
			with AutowiringAttendanceMonitoringMeetingRecordServiceComponent
			with AutowiringFileAttachmentServiceComponent
			with ComposableCommand[MeetingRecord]
			with MeetingRecordCommandBindListener
			with ModifyMeetingRecordValidation
			with EditMeetingRecordDescription
			with ModifyMeetingRecordPermissions
			with EditMeetingRecordCommandState
			with MeetingRecordCommandRequest
			with EditMeetingRecordCommandNotifications
			with PopulateMeetingRecordCommand
}


class EditMeetingRecordCommandInternal(val meetingRecord: MeetingRecord)
	extends AbstractModifyMeetingRecordCommand {

	self: MeetingRecordCommandRequest with EditMeetingRecordCommandState
		with MeetingRecordServiceComponent with FeaturesComponent
		with AttendanceMonitoringMeetingRecordServiceComponent with FileAttachmentServiceComponent =>

	override def applyInternal() = {
		applyCommon(meetingRecord)
	}

}

trait PopulateMeetingRecordCommand extends PopulateOnForm {

	self: MeetingRecordCommandRequest with EditMeetingRecordCommandState =>

	override def populate(): Unit = {
		title = meetingRecord.title
		description = meetingRecord.description
		isRealTime = meetingRecord.isRealTime
		meetingRecord.isRealTime match {
			case true => meetingDateTime = meetingRecord.meetingDate
			case false => meetingDate = meetingRecord.meetingDate.toLocalDate
		}
		format = meetingRecord.format
		attachedFiles = meetingRecord.attachments
	}

}

trait EditMeetingRecordDescription extends ModifyMeetingRecordDescription {

	self: ModifyMeetingRecordCommandState =>

	override lazy val eventName = "EditMeetingRecord"

}

trait EditMeetingRecordCommandState extends ModifyMeetingRecordCommandState {
	def meetingRecord: MeetingRecord
	override def creator: Member = meetingRecord.creator
	override def relationship: StudentRelationship = meetingRecord.relationship
}

trait EditMeetingRecordCommandNotifications extends Notifies[MeetingRecord, MeetingRecord]
	with SchedulesNotifications[MeetingRecord, MeetingRecord] with CompletesNotifications[MeetingRecord] {

	self: EditMeetingRecordCommandState with NotificationHandling =>

	override def emit(meeting: MeetingRecord) = Seq(
		Notification.init(new EditedMeetingRecordApprovalNotification, creator.asSsoUser, Seq(meeting), relationship)
	)

	override def transformResult(meetingRecord: MeetingRecord) = Seq(meetingRecord)

	override def scheduledNotifications(result: MeetingRecord) = Seq(
		new ScheduledNotification[MeetingRecord]("editedMeetingRecordApproval", result, DateTime.now.plusWeeks(1))
	)

	override def notificationsToComplete(commandResult: MeetingRecord): CompletesNotificationsResult = {
		CompletesNotificationsResult(
			commandResult.approvals.asScala.flatMap(approval =>
				notificationService.findActionRequiredNotificationsByEntityAndType[MeetingRecordRejectedNotification](approval)
			),
			creator.asSsoUser
		)
	}

}