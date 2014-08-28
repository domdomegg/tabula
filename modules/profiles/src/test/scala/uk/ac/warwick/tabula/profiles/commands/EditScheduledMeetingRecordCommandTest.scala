package uk.ac.warwick.tabula.profiles.commands

import uk.ac.warwick.tabula.data.model._
import org.springframework.validation.BindException
import uk.ac.warwick.tabula.{Mockito, TestBase}
import uk.ac.warwick.tabula.services.{FileAttachmentService, FileAttachmentServiceComponent, MeetingRecordServiceComponent, MeetingRecordService}
import org.joda.time.DateTime

class EditScheduledMeetingRecordCommandTest  extends TestBase with Mockito {

	trait Fixture {
		val relationship: StudentRelationship = mock[StudentRelationship]

		val mockMeetingRecordService: MeetingRecordService = mock[MeetingRecordService]
		mockMeetingRecordService.listScheduled(Set(relationship), Some(creator)) returns Seq()

		var creator: StaffMember = _
		var scheduledMeetingRecord: ScheduledMeetingRecord = new ScheduledMeetingRecord()
		scheduledMeetingRecord.meetingDate = new DateTime().minusDays(1)
		scheduledMeetingRecord.relationship = relationship
		val command = new EditScheduledMeetingRecordCommand(creator, scheduledMeetingRecord) with EditScheduledMeetingRecordCommandValidation with EditScheduledMeetingRecordCommandSupport with MeetingRecordServiceComponent {
			val meetingRecordService: MeetingRecordService = mockMeetingRecordService
		}
	}

	@Test
	def applyNotRescheduled() { new Fixture {
		val sameDate: DateTime = new DateTime().plusMonths(1)
		scheduledMeetingRecord.meetingDate = sameDate

		scheduledMeetingRecord.lastUpdatedDate.isAfter(scheduledMeetingRecord.creationDate) should be (false)

		val newTitle: String = "new title"
		command.title = newTitle
		command.meetingDate = sameDate

		Thread.sleep(2) // otherwise sometimes the last updated date is not after created date and that assertion fails

		val result: ScheduledMeetingRecordResult = command.applyInternal()

		result.meetingRecord.title should be (newTitle)
		result.meetingRecord.lastUpdatedDate.isAfter(result.meetingRecord.creationDate) should be (true)
		result.isRescheduled should be (false)

		there was one(mockMeetingRecordService).saveOrUpdate(scheduledMeetingRecord)

	}}

	@Test
	def applyRescheduled() { new Fixture {

		scheduledMeetingRecord.lastUpdatedDate == scheduledMeetingRecord.creationDate should be (true)

		val newTitle: String = "new title"
		val newDescription: String = "new description"
		val newFormat = MeetingFormat.VideoConference
		val newMeetingDate = new DateTime().plusMinutes(1)
		command.title = newTitle
		command.description = newDescription
		command.format = newFormat
		command.meetingDate = newMeetingDate

		Thread.sleep(2) // otherwise sometimes the last updated date is not after created date and that assertion fails

		val result: ScheduledMeetingRecordResult = command.applyInternal()

		result.meetingRecord.title should be (newTitle)
		result.meetingRecord.description should be (newDescription)
		result.meetingRecord.lastUpdatedDate.isAfter(result.meetingRecord.creationDate) should be (true)
		result.meetingRecord.format should be (newFormat)
		result.meetingRecord.meetingDate should be (newMeetingDate)
		result.isRescheduled should be (true)

		there was one(mockMeetingRecordService).saveOrUpdate(scheduledMeetingRecord)

	}}

	@Test
	def validMeeting() { new Fixture {
		val errors = new BindException(command, "command")
		command.title = "title"
		command.format = MeetingFormat.FaceToFace
		command.meetingDate = new DateTime().plusDays(1)
		command.validate(errors)
		errors.hasErrors should be (false)
	}}

	@Test
	def noTitle() { new Fixture {
		val errors = new BindException(command, "command")
		command.format = MeetingFormat.FaceToFace
		command.meetingDate = new DateTime().plusDays(1)
		command.validate(errors)
		errors.hasErrors should be (true)
		errors.getFieldErrorCount should be(1)
		errors.getFieldErrors("title").size should be(1)
	}}

	@Test
	def noFormat() { new Fixture {
		val errors = new BindException(command, "command")
		command.title = "A Meeting"
		command.meetingDate = new DateTime().plusHours(1)
		command.validate(errors)
		errors.hasErrors should be (true)
		errors.getFieldErrorCount should be(1)
		errors.getFieldErrors("format").size should be(1)
	}}

	@Test
	def scheduleInPast() { new Fixture {
		val errors = new BindException(command, "command")
		command.format = MeetingFormat.FaceToFace
		command.title = "A Title"
		command.meetingDate = new DateTime().minusDays(1)
		command.validate(errors)
		errors.hasErrors should be (true)
		errors.getFieldErrorCount should be(1)
		errors.getFieldErrors("meetingDate").size should be(1)
	}}

	@Test
	def scheduleDuplicateDate() { new Fixture {

		val meetingTime: DateTime = new DateTime().plusWeeks(1)

		val meetingWithDupeDate: ScheduledMeetingRecord = new ScheduledMeetingRecord
		meetingWithDupeDate.meetingDate = meetingTime
		meetingWithDupeDate.id = "A"

		command.meetingRecord.id = "B"

		mockMeetingRecordService.listScheduled(Set(relationship), Some(creator)) returns Seq(meetingWithDupeDate)

		val errors = new BindException(command, "command")
		command.format = MeetingFormat.FaceToFace
		command.title = "A Title"
		command.meetingDate = meetingTime
		command.validate(errors)
		errors.hasErrors should be (true)
		errors.getFieldErrorCount should be(1)
		errors.getFieldErrors("meetingDate").size should be(1)
	}}

	@Test
	def noInput() { new Fixture {
		val errors = new BindException(command, "command")
		command.validate(errors)
		errors.hasErrors should be (true)
	}}

	trait EditScheduledMeetingRecordCommandSupport extends FileAttachmentServiceComponent {
		def fileAttachmentService = mock[FileAttachmentService]
	}

}
