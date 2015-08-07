package uk.ac.warwick.tabula.profiles.commands

import org.joda.time.{DateTime, LocalDate}
import org.springframework.validation.Errors
import org.springframework.validation.ValidationUtils.rejectIfEmptyOrWhitespace
import uk.ac.warwick.spring.Wire
import uk.ac.warwick.tabula.commands._
import uk.ac.warwick.tabula.data.model.MeetingApprovalState.Pending
import uk.ac.warwick.tabula.data.model._
import uk.ac.warwick.tabula.data.model.forms.FormattedHtml
import uk.ac.warwick.tabula.data.{Daoisms, FileDao, MeetingRecordDao}
import uk.ac.warwick.tabula.permissions.Permissions
import uk.ac.warwick.tabula.services.MonitoringPointMeetingRelationshipTermService
import uk.ac.warwick.tabula.services.attendancemonitoring.AttendanceMonitoringMeetingRecordService
import uk.ac.warwick.tabula.system.BindListener

import scala.collection.JavaConverters._

abstract class ModifyMeetingRecordCommand(val creator: Member, var relationship: StudentRelationship, val considerAlternatives: Boolean = false)
	extends Command[MeetingRecord] with Notifies[MeetingRecord, MeetingRecord]
	with SchedulesNotifications[MeetingRecord, MeetingRecord]
	with SelfValidating with FormattedHtml
	with BindListener with Daoisms {

	var meetingRecordDao = Wire.auto[MeetingRecordDao]
	var fileDao = Wire.auto[FileDao]
	var monitoringPointMeetingRelationshipTermService = Wire.auto[MonitoringPointMeetingRelationshipTermService]
	var attendanceMonitoringMeetingRecordService = Wire.auto[AttendanceMonitoringMeetingRecordService]

	var title: String = _
	var description: String = _
	var meetingDateTime: DateTime = _
	var meetingDate: LocalDate = _
	var format: MeetingFormat = _
	var isRealTime: Boolean = _

	var file: UploadedFile = new UploadedFile
	var attachedFiles:JList[FileAttachment] = _

	var attachmentTypes = Seq[String]()

	var posted: Boolean = false

	PermissionCheck(Permissions.Profiles.MeetingRecord.Manage(relationship.relationshipType), mandatory(relationship.studentMember))

	val meeting: MeetingRecord

	override def applyInternal() = {

		def persistAttachments(meeting: MeetingRecord) {
			// delete attachments that have been removed

			if (meeting.attachments != null) {
				val filesToKeep = Option(attachedFiles).map(_.asScala.toList).getOrElse(List())
				val filesToRemove = meeting.attachments.asScala -- filesToKeep
				meeting.attachments = JArrayList[FileAttachment](filesToKeep)
				filesToRemove.foreach(session.delete(_))
			}

			file.attached.asScala map(attachment => {
				attachment.meetingRecord = meeting
				meeting.attachments.add(attachment)
				fileDao.savePermanent(attachment)
			})
		}

		meeting.title = title
		meeting.description = description
		meeting.isRealTime match {
			case true => meeting.meetingDate = meetingDateTime
			case false => meeting.meetingDate = meetingDate.toDateTimeAtStartOfDay.withHourOfDay(MeetingRecord.DefaultMeetingTimeOfDay)
		}
		meeting.format = format
		meeting.lastUpdatedDate = DateTime.now
		meeting.relationship = relationship
		persistAttachments(meeting)

		// persist the meeting record
		meetingRecordDao.saveOrUpdate(meeting)

		if (features.meetingRecordApproval) {
			updateMeetingApproval(meeting)
		}

		if (features.attendanceMonitoringMeetingPointType) {
			monitoringPointMeetingRelationshipTermService.updateCheckpointsForMeeting(meeting)
			if (features.attendanceMonitoringVersion2)
				attendanceMonitoringMeetingRecordService.updateCheckpoints(meeting)
		}

		meeting
	}

	def updateMeetingApproval(meetingRecord: MeetingRecord) : Option[MeetingRecordApproval] = {

		def getMeetingRecord(approver: Member) : MeetingRecordApproval = {

			val meetingRecordApproval = meetingRecord.approvals.asScala.find(_.approver == approver).getOrElse{
				val newMeetingRecordApproval = new MeetingRecordApproval()
				newMeetingRecordApproval.approver = approver
				newMeetingRecordApproval.meetingRecord = meetingRecord
				meetingRecord.approvals.add(newMeetingRecordApproval)
				newMeetingRecordApproval
			}

			meetingRecordApproval.state = Pending
			session.saveOrUpdate(meetingRecordApproval)
			meetingRecordApproval
		}

		val approver = Seq(relationship.agentMember, relationship.studentMember).flatten.find(_ != creator)
		approver.map(getMeetingRecord)

	}

	override def validate(errors: Errors) {
		rejectIfEmptyOrWhitespace(errors, "title", "NotEmpty")
		if (title.length > MeetingRecord.MaxTitleLength){
			errors.rejectValue("title", "meetingRecord.title.long", new Array(MeetingRecord.MaxTitleLength), "")
		}

		rejectIfEmptyOrWhitespace(errors, "relationship", "NotEmpty")
		rejectIfEmptyOrWhitespace(errors, "format", "NotEmpty")

		val dateToCheck: DateTime = isRealTime match {
			case true => meetingDateTime
			case false => meetingDate.toDateTimeAtStartOfDay
		}

		if (dateToCheck == null) {
			errors.rejectValue("meetingDate", "meetingRecord.date.missing")
			errors.rejectValue("meetingDateTime", "meetingRecord.date.missing")
		} else {
			if (dateToCheck.isAfter(DateTime.now)) {
				errors.rejectValue("meetingDate", "meetingRecord.date.future")
				errors.rejectValue("meetingDateTime", "meetingRecord.date.future")
			} else if (dateToCheck.isBefore(DateTime.now.minusYears(MeetingRecord.MeetingTooOldThresholdYears))) {
				errors.rejectValue("meetingDate", "meetingRecord.date.prehistoric")
				errors.rejectValue("meetingDateTime", "meetingRecord.date.prehistoric")
			}
		}
	}

	def describe(d: Description) {
		relationship.studentMember.map { d.member(_) }
		d.properties(
			"creator" -> creator.universityId,
			"relationship" -> relationship.relationshipType.toString()
		)
	}

	override def describeResult(d: Description, meeting: MeetingRecord) {
		relationship.studentMember.map { d.member(_) }
		d.properties(
			"creator" -> creator.universityId,
			"relationship" -> relationship.relationshipType.toString(),
			"meeting" -> meeting.id
		)
		d.fileAttachments(meeting.attachments.asScala)
	}

}