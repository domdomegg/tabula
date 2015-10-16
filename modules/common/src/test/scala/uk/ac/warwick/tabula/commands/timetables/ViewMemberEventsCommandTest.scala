package uk.ac.warwick.tabula.commands.timetables

import org.joda.time.{Interval, LocalDate, LocalDateTime, LocalTime}
import uk.ac.warwick.tabula.data.model.StudentMember
import uk.ac.warwick.tabula.data.model.groups.DayOfWeek
import uk.ac.warwick.tabula.permissions.Permissions
import uk.ac.warwick.tabula.services.{TermServiceComponent, TermService}
import uk.ac.warwick.tabula.services.timetables._
import uk.ac.warwick.tabula.system.permissions.PermissionsChecking
import uk.ac.warwick.tabula.timetables.{EventOccurrence, TimetableEventType, TimetableEvent}
import uk.ac.warwick.tabula.{AcademicYear, CurrentUser, Mockito, TestBase}

import scala.util.Success

class ViewMemberEventsCommandTest extends TestBase with Mockito {

	private trait CommandTestSupport
		extends StudentTimetableEventSourceComponent
			with ScheduledMeetingEventSourceComponent
			with EventOccurrenceServiceComponent
			with TermServiceComponent {
		val studentTimetableEventSource = mock[StudentTimetableEventSource]
		val scheduledMeetingEventSource = mock[ScheduledMeetingEventSource]
		val termService = mock[TermService]
		val eventOccurrenceService = mock[EventOccurrenceService]
	}

	private trait Fixture {
		val testStudent = new StudentMember
		val user = mock[CurrentUser]

		val event = TimetableEvent("","","","",TimetableEventType.Induction,Nil,DayOfWeek.Monday,LocalTime.now, LocalTime.now,None,TimetableEvent.Parent(),None,Nil,Nil, AcademicYear(2012))
		val timetableEvents = Seq(event)

		val occurrence = EventOccurrence("","", "", "", TimetableEventType.Meeting, LocalDateTime.now, LocalDateTime.now, None, TimetableEvent.Parent(), None, Nil)
		val meetingOccurrences = Seq(occurrence)

		val earlierEvent = EventOccurrence("","","","",TimetableEventType.Induction,LocalDateTime.now.minusHours(1), LocalDateTime.now,None, TimetableEvent.Parent(), None, Nil )
		val laterEvent = EventOccurrence("","","","",TimetableEventType.Induction,LocalDateTime.now.plusHours(1), LocalDateTime.now.plusHours(1),None, TimetableEvent.Parent(), None, Nil )
		val eventOccurences = Seq(laterEvent,earlierEvent) // deliberately put them the wrong way round so we can check sorting

		val command = new ViewStudentEventsCommandInternal(testStudent, user) with CommandTestSupport

		command.start =  new LocalDate
		command.end = command.start.plusDays(2)
		command.studentTimetableEventSource.eventsFor(testStudent, user, TimetableEvent.Context.Student) returns Success(timetableEvents)
		command.scheduledMeetingEventSource.occurrencesFor(testStudent, user, TimetableEvent.Context.Student) returns Success(meetingOccurrences)
		command.eventOccurrenceService.fromTimetableEvent(any[TimetableEvent], any[Interval]) returns eventOccurences
	}

	@Test
	def fetchesEventsFromEventSource() { new Fixture {
		command.applyInternal()
		verify(command.studentTimetableEventSource, times(1)).eventsFor(testStudent, user, TimetableEvent.Context.Student)
	}}

	@Test
	def transformsEventsIntoOccurrences(){ new Fixture {
		command.applyInternal()
		verify(command.eventOccurrenceService, times(1)).fromTimetableEvent(event, new Interval(command.start.toDateTimeAtStartOfDay, command.end.toDateTimeAtStartOfDay))
	}}

	@Test
	def sortsOccurencesByDate(){ new Fixture {
		val sortedEvents = command.applyInternal()
		sortedEvents should be (Success(Seq(earlierEvent, occurrence, laterEvent)))
	}}

	@Test
	def requiresReadTimetablePermissions(){ new Fixture {
		val perms = new ViewMemberEventsPermissions with ViewMemberEventsState {
			val member = testStudent
		}

		val checking = mock[PermissionsChecking]
		perms.permissionsCheck(checking)
		verify(checking, times(1)).PermissionCheck(Permissions.Profiles.Read.Timetable, testStudent)
	}}

	@Test
	def mixesCorrectPermissionsIntoCommand(){ new Fixture {
		val composedCommand = ViewMemberEventsCommand(testStudent, user)
		composedCommand should be(anInstanceOf[ViewMemberEventsPermissions])
	}}

}
