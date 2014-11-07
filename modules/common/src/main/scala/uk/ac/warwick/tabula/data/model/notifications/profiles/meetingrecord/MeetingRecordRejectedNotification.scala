package uk.ac.warwick.tabula.data.model.notifications.profiles.meetingrecord

import javax.persistence.{DiscriminatorValue, Entity}

import uk.ac.warwick.tabula.data.model.{FreemarkerModel, MeetingRecordApproval, Notification, NotificationPriority, SingleItemNotification}

@Entity
@DiscriminatorValue("meetingRecordRejected")
class MeetingRecordRejectedNotification
	extends Notification[MeetingRecordApproval, Unit]
	with MeetingRecordNotificationTrait
	with SingleItemNotification[MeetingRecordApproval] {

	priority = NotificationPriority.Warning

	def approval = item.entity
	def meeting = approval.meetingRecord
	def relationship = meeting.relationship

	def verb = "return"
	def actionRequired = true

	def title = {
		val name =
			if (meeting.creator.universityId == meeting.relationship.studentId) meeting.relationship.agentName
			else meeting.relationship.studentMember.flatMap { _.fullName }.getOrElse("student")

		s"${agentRole.capitalize} meeting record with $name returned with comments"
	}

	def content = FreemarkerModel(FreemarkerTemplate, Map(
		"actor" -> agent,
		"role"->agentRole,
		"dateFormatter" -> dateOnlyFormatter,
		"meetingRecord" -> approval.meetingRecord,
		"verbed" -> "returned",
		"reason" -> approval.comments
	))

	def urlTitle = "edit the record and submit it for approval again"

	def recipients = Seq(approval.meetingRecord.creator.asSsoUser)
}
