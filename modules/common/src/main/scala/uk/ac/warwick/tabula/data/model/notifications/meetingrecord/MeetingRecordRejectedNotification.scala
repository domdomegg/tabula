package uk.ac.warwick.tabula.data.model.notifications.meetingrecord

import javax.persistence.{DiscriminatorValue, Entity}

import uk.ac.warwick.tabula.data.model.{FreemarkerModel, MeetingRecordApproval, Notification, NotificationPriority, SingleItemNotification}

@Entity
@DiscriminatorValue("meetingRecordRejected")
class MeetingRecordRejectedNotification
	extends Notification[MeetingRecordApproval, Unit]
	with MeetingRecordNotificationTrait
	with SingleItemNotification[MeetingRecordApproval] {

	priority = NotificationPriority.Critical

	def approval = item.entity
	def meeting = approval.meetingRecord
	def relationship = meeting.relationship

	def verb = "reject"
	def actionRequired = true

	def title = "Meeting record rejected"
	def content = FreemarkerModel(FreemarkerTemplate, Map(
		"actor" -> agent,
		"role"->agentRole,
		"dateFormatter" -> dateOnlyFormatter,
		"meetingRecord" -> approval.meetingRecord,
		"verbed" -> "rejected",
		"reason" -> approval.comments
	))

	def urlTitle = "edit the record and submit it for approval again"

	def recipients = Seq(approval.meetingRecord.creator.asSsoUser)
}