package uk.ac.warwick.tabula.profiles.commands

import uk.ac.warwick.tabula.data.model.notifications.meetingrecord.EditedMeetingRecordApprovalNotification
import uk.ac.warwick.tabula.data.model.{Notification, MeetingRecord}
import org.springframework.validation.BindingResult
import uk.ac.warwick.tabula.data.Transactions._
import uk.ac.warwick.tabula.data.model.forms.FormattedHtml
import scala.language.implicitConversions

class EditMeetingRecordCommand(meetingRecord: MeetingRecord)
	extends ModifyMeetingRecordCommand(meetingRecord.creator, meetingRecord.relationship) with FormattedHtml {

	val meeting = meetingRecord

	override def onBind(result:BindingResult) = transactional() {
		file.onBind(result)
		copyToCommand(meetingRecord)
	}

	def copyToCommand(meetingRecord: MeetingRecord) {
		implicit def toOption[T](x:T) : Option[T] = Option(x)

		title = title.getOrElse(meetingRecord.title)
		description = description.getOrElse(meetingRecord.description)
		meetingRecord.isRealTime match {
			case true => meetingDateTime = meetingDateTime.getOrElse(meetingRecord.meetingDate)
			case false => meetingDate = meetingDate.getOrElse(meetingRecord.meetingDate.toLocalDate)
		}
		format = format.getOrElse(meetingRecord.format)
		isRealTime = meetingRecord.isRealTime

		attachedFiles = if(posted){
			// we posted so attachments must have been removed
			attachedFiles.getOrElse(JList())
		} else{
			// we didn't post so attachments must be fetched
			attachedFiles.getOrElse(meetingRecord.attachments)
		}
	}

	def emit(meeting: MeetingRecord) = Seq(
		Notification.init(new EditedMeetingRecordApprovalNotification, creator.asSsoUser, Seq(meeting), relationship)
	)
}