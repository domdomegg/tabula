package uk.ac.warwick.tabula.profiles.web.controllers

import org.joda.time.DateTime
import org.springframework.mock.web.{MockHttpServletResponse, MockHttpServletRequest}
import uk.ac.warwick.tabula.services.TermService
import uk.ac.warwick.tabula.{AcademicYear, Mockito, Fixtures, TestBase}
import uk.ac.warwick.tabula.commands.Appliable
import uk.ac.warwick.tabula.data.model.Member
import uk.ac.warwick.tabula.profiles.commands.PersonalTimetableCommandState
import uk.ac.warwick.tabula.profiles.web.views.FullCalendarEvent
import uk.ac.warwick.tabula.timetables.EventOccurrence
import uk.ac.warwick.util.termdates.Term.TermType
import uk.ac.warwick.util.termdates.TermImpl
import uk.ac.warwick.tabula.JavaImports._

class TimetableControllerTest extends TestBase with Mockito {

	@Test
	def colouriseBasedOnModule(){
		// create a bunch events spread over 3 modules, with blank colours
		val events = for {
			i<- 1 until 21
		  j = i%3}
		yield {
			FullCalendarEvent(s"Test event $i", "", allDay = false, 0L,0L,"","","","","","","","","","","",s"module$j","")
		}

		// Add a Busy event at the end
		val coloured = new TimetableController().colourEvents(events :+ FullCalendarEvent("Busy", "", allDay = false, 0L,0L,"","","","","","","","","","","", "", ""))

		// every event should have a colour
		coloured.find(_.backgroundColor=="") should not be 'defined

		// there should be 3 colours in use
		coloured.map(_.backgroundColor).distinct.size should be(4)

		coloured.last.backgroundColor should be ("#bbb")
	}

	@Test
	def emptyTimetableValidates() {
		val termService = smartMock[TermService]
		val autumnTerm = new TermImpl(null, DateTime.now, null, TermType.autumn)
		val academicYear = AcademicYear.guessSITSAcademicYearByDate(DateTime.now)
		termService.getTermFromAcademicWeek(1, academicYear) returns autumnTerm
		termService.getTermFromAcademicWeek(1, academicYear + 1) returns autumnTerm
		val controller = new TimetableICalController
		val command = new Appliable[Seq[EventOccurrence]] with PersonalTimetableCommandState {
			override def apply(): Seq[EventOccurrence] = Seq()
			override def member: Member = Fixtures.staff("1234")
		}
		command.start = DateTime.now.toLocalDate
		command.end = DateTime.now.toLocalDate
		controller.termService = termService
		val icalMav = controller.getIcalFeed(command)
		icalMav.view.render(JMap("filename" -> "foo"), new MockHttpServletRequest, new MockHttpServletResponse)
		// doesn't throw an exception
	}

}
