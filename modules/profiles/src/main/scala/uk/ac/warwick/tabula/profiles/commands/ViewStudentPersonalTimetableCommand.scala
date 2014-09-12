package uk.ac.warwick.tabula.profiles.commands

import org.joda.time.{Interval, LocalDate}
import uk.ac.warwick.tabula.CurrentUser
import uk.ac.warwick.tabula.commands.{Appliable, Command, CommandInternal, ComposableCommand, ReadOnly, Unaudited}
import uk.ac.warwick.tabula.data.model.{Member, StudentMember}
import uk.ac.warwick.tabula.permissions.Permissions
import uk.ac.warwick.tabula.profiles.services.timetables._
import uk.ac.warwick.tabula.services._
import uk.ac.warwick.tabula.system.permissions.{PermissionsChecking, Public, RequiresPermissionsChecking}
import uk.ac.warwick.tabula.timetables.{EventOccurrence, TimetableEvent}
// Do not remove
// Should be import uk.ac.warwick.tabula.helpers.DateTimeOrdering
import uk.ac.warwick.tabula.helpers.DateTimeOrdering

trait PersonalTimetableCommandState {
	var start: LocalDate = LocalDate.now.minusMonths(12)
	var end: LocalDate = start.plusMonths(13)
	def member: Member
}

trait ViewStudentPersonalTimetableCommandState extends PersonalTimetableCommandState {
	val student: StudentMember
	lazy val member = student
}

/*
 * If you want to add new sources of events to the calendar, here's where to do it:
 *
 *  - if your events recur throughout the academic year, and can be described in terms of "on this day, at this time,
 *  in these academic weeks", then implement TimetableEventSource, and register your source with
 *  CombinedStudentTimetableEventSource.
 *
 *  - if your events are one-off, but still described in terms of day, time, and academic week, implement a
 *  TimetableEventSource as above (producing a Seq of size 1) and plumb it in as above; it will save you having to
 *  write code to infer a proper calendar date.
 *
 *  - If your events already have a calendar date associated with them, then you should implement a method which
 *  produces a Seq[EventOccurrence]. Invoke that within this class's applyInternal, and add the result to the
 *  "occurrences" list, before the list is sorted.
 *
 *  - If there are several sources that fit the last category, then it would make sense to wrap them all into a
 *  per-student "NonRecurringEventSource", add a cache, and pass that into this class's constructor alongside
 *  the StudentTimetableEventSource
 *
 */
class ViewStudentPersonalTimetableCommandImpl(
	studentTimetableEventSource: StudentTimetableEventSource,
	scheduledMeetingEventSource: ScheduledMeetingEventSource,
	val student: StudentMember,
	val currentUser: CurrentUser
) extends CommandInternal[Seq[EventOccurrence]] with ViewStudentPersonalTimetableCommandState {
	this: EventOccurrenceServiceComponent =>

	def eventsToOccurrences: TimetableEvent => Seq[EventOccurrence] =
		eventOccurrenceService.fromTimetableEvent(_, new Interval(start.toDateTimeAtStartOfDay, end.toDateTimeAtStartOfDay))

	def applyInternal(): Seq[EventOccurrence] = {
		val timetableEvents = studentTimetableEventSource.eventsFor(student)
		val occurrences =
			timetableEvents.flatMap(eventsToOccurrences) ++
			scheduledMeetingEventSource.occurrencesFor(student, currentUser, TimetableEvent.Context.Student)

		// Converter to make localDates sortable
		import uk.ac.warwick.tabula.helpers.DateTimeOrdering._
		occurrences.sortBy(_.start)
	}
}

trait ViewStudentTimetablePermissions extends RequiresPermissionsChecking{
	this:ViewStudentPersonalTimetableCommandState =>
	def permissionsCheck(p: PermissionsChecking) {
		p.PermissionCheck(Permissions.Profiles.Read.Timetable, student)
	}
}

object ViewStudentPersonalTimetableCommand {


	// mmm, cake.
	// have to pass in the student in the constructor so that we have enough data for the permissions check to work

	def apply(
		studentTimetableEventSource:StudentTimetableEventSource,
		scheduledMeetingEventSource: ScheduledMeetingEventSource,
		student: StudentMember,
		currentUser: CurrentUser
	): Appliable[Seq[EventOccurrence]] with PersonalTimetableCommandState =
		new ViewStudentPersonalTimetableCommandImpl(studentTimetableEventSource, scheduledMeetingEventSource, student, currentUser)
			with ComposableCommand[Seq[EventOccurrence]]
			with ViewStudentTimetablePermissions
			with ReadOnly with Unaudited
			with AutowiringTermBasedEventOccurrenceServiceComponent
			with TermAwareWeekToDateConverterComponent
			with AutowiringTermServiceComponent
			with AutowiringProfileServiceComponent
			with ViewStudentPersonalTimetableCommandState
}

object PublicStudentPersonalTimetableCommand {

	def apply(
		studentTimetableEventSource:StudentTimetableEventSource,
		scheduledMeetingEventSource: ScheduledMeetingEventSource,
		student: StudentMember,
		currentUser: CurrentUser
	): Appliable[Seq[EventOccurrence]] with PersonalTimetableCommandState =
		new ViewStudentPersonalTimetableCommandImpl(studentTimetableEventSource, scheduledMeetingEventSource, student, currentUser)
			with Command[Seq[EventOccurrence]]
			with Public
			with ReadOnly with Unaudited
			with AutowiringTermBasedEventOccurrenceServiceComponent
			with TermAwareWeekToDateConverterComponent
			with AutowiringTermServiceComponent
			with AutowiringProfileServiceComponent
			with ViewStudentPersonalTimetableCommandState
}


