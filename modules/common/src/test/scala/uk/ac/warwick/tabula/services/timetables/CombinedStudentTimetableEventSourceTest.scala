package uk.ac.warwick.tabula.services.timetables

import org.joda.time.LocalTime
import uk.ac.warwick.tabula.data.model.StudentMember
import uk.ac.warwick.tabula.data.model.groups.DayOfWeek
import uk.ac.warwick.tabula.timetables.{TimetableEvent, TimetableEventType}
import uk.ac.warwick.tabula.{AcademicYear, Mockito, TestBase}
import uk.ac.warwick.userlookup.User

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._

class CombinedStudentTimetableEventSourceTest extends TestBase with Mockito{

	val student = new StudentMember
	student.universityId = "university ID"
	val user = new User()

	val ttEvent= TimetableEvent("", "From Timetable","", "",TimetableEventType.Induction,Nil,DayOfWeek.Monday,LocalTime.now, LocalTime.now,None,TimetableEvent.Parent(),None,Nil,Nil,AcademicYear(2013))
	val timetableEvents = Seq(ttEvent)

	val sgEvent= TimetableEvent("", "From Group","", "",TimetableEventType.Induction,Nil,DayOfWeek.Monday,LocalTime.now, LocalTime.now,None,TimetableEvent.Parent(),None,Nil,Nil,AcademicYear(2013))
	val groupEvents = Seq(sgEvent)

	val source = new CombinedStudentTimetableEventSourceComponent
		with StaffAndStudentTimetableFetchingServiceComponent
		with SmallGroupEventTimetableEventSourceComponent{
		val staffGroupEventSource = mock[StaffTimetableEventSource]
		val studentGroupEventSource = mock[StudentTimetableEventSource]
		val timetableFetchingService = mock[CompleteTimetableFetchingService]
	}

	source.timetableFetchingService.getTimetableForStudent(student.universityId) returns Future.successful(timetableEvents)
	source.studentGroupEventSource.eventsFor(student, currentUser, TimetableEvent.Context.Student) returns Future.successful(groupEvents)

	@Test
	def callsBothServicesAndAggregatesTheResult(){
		Await.result(source.studentTimetableEventSource.eventsFor(student, currentUser, TimetableEvent.Context.Student), 1.second) should be (timetableEvents ++ groupEvents)
	}


}
