package uk.ac.warwick.tabula.profiles.commands

import uk.ac.warwick.tabula.data.model._
import org.springframework.validation.BindException
import uk.ac.warwick.tabula.{Mockito, TestBase}
import uk.ac.warwick.tabula.services.{MeetingRecordServiceComponent, MeetingRecordService}
import org.joda.time.DateTime

class ScheduledMeetingRecordMissedCommandTest  extends TestBase with Mockito {

	trait Fixture {
		val mockMeetingRecordService = mock[MeetingRecordService]

		var scheduledMeetingRecord: ScheduledMeetingRecord = new ScheduledMeetingRecord()
		scheduledMeetingRecord.meetingDate = new DateTime().minusDays(1)

		val command = new ScheduledMeetingRecordMissedCommand(scheduledMeetingRecord) with ScheduledMeetingRecordMissedValidation with MeetingRecordServiceComponent {
			val meetingRecordService: MeetingRecordService = mockMeetingRecordService
		}
	}

	@Test
	def apply() { new Fixture {
		val missedReason = "no reason"
		command.missedReason = missedReason
		val scheduledMeeting = command.applyInternal()

		there was one(mockMeetingRecordService).saveOrUpdate(scheduledMeetingRecord)
		scheduledMeeting.missed should be (true)
		scheduledMeeting.missedReason should be (missedReason)

	}}

	@Test
	def valid() { new Fixture {
		val errors = new BindException(command, "command")
		command.validate(errors)
		errors.hasErrors should be (false)
	}}

	@Test
	def meetingInFuture() { new Fixture {
		val errors = new BindException(command, "command")
		scheduledMeetingRecord.meetingDate = new DateTime().plusDays(1)
		command.validate(errors)
		errors.hasErrors should be (true)
		errors.getErrorCount should be (1)
	}}

	@Test
	def alreadyMissed() { new Fixture {
		val errors = new BindException(command, "command")
		scheduledMeetingRecord.missed = true
		command.validate(errors)
		errors.hasErrors should be (true)
		errors.getErrorCount should be (1)
	}}
}