package uk.ac.warwick.tabula.commands.attendance

import uk.ac.warwick.tabula.commands.attendance.old.GroupMonitoringPointsByTerm
import uk.ac.warwick.tabula.{Fixtures, AcademicYear, Mockito, TestBase}
import uk.ac.warwick.tabula.services._
import uk.ac.warwick.tabula.data.model.attendance.{MonitoringPointSet, MonitoringPoint}
import org.joda.time.{LocalDate, Interval, DateTimeConstants}
import uk.ac.warwick.util.termdates.Term
import uk.ac.warwick.tabula.services.Vacation
import uk.ac.warwick.tabula.data.model.groups.DayOfWeek

class GroupMonitoringPointsByTermTest extends TestBase with Mockito {

	trait TestSupport extends TermServiceComponent {
		val termService = mock[TermService]
	}

	trait Fixture extends TestSupport {
		val academicYear = AcademicYear(2013)
		val monitoringPoint1 = new MonitoringPoint
		monitoringPoint1.name = "Point 1"
		monitoringPoint1.validFromWeek = 5
		val monitoringPoint2 = new MonitoringPoint
		monitoringPoint2.name = "Point 2"
		monitoringPoint2.validFromWeek = 15
		val monitoringPoints = List(monitoringPoint1, monitoringPoint2)

		val week5StartDate = new LocalDate(academicYear.startYear, DateTimeConstants.NOVEMBER, 1).toDateTimeAtStartOfDay
		val week5EndDate = new LocalDate(academicYear.startYear, DateTimeConstants.NOVEMBER, 8).toDateTimeAtStartOfDay
		val week15StartDate = new LocalDate(academicYear.startYear, DateTimeConstants.DECEMBER, 1).toDateTimeAtStartOfDay
		val week15EndDate = new LocalDate(academicYear.startYear, DateTimeConstants.DECEMBER, 8).toDateTimeAtStartOfDay

		val week5pair = (new Integer(5), new Interval(week5StartDate, week5EndDate))
		val week15pair = (new Integer(15), new Interval(week15StartDate, week15EndDate))
		val weeksForYear = Seq(week5pair, week15pair)
		termService.getAcademicWeeksForYear(new LocalDate(academicYear.startYear, DateTimeConstants.NOVEMBER, 1).toDateTimeAtStartOfDay)	returns weeksForYear

		val autumnTerm = mock[Term]
		autumnTerm.getTermTypeAsString returns "Autumn"
		val christmasVacation = mock[Vacation]
		christmasVacation.getTermTypeAsString returns "Christmas vacation"
		termService.getTermFromDateIncludingVacations(week5StartDate.withDayOfWeek(DayOfWeek.Thursday.getAsInt)) returns autumnTerm
		termService.getTermFromDateIncludingVacations(week15StartDate.withDayOfWeek(DayOfWeek.Thursday.getAsInt)) returns christmasVacation
	}

	@Test
	def pointsByTerm() {
		new Fixture with GroupMonitoringPointsByTerm {
			val pointsByTerm = groupByTerm(monitoringPoints, academicYear)

			pointsByTerm("Autumn").size should be (1)
			pointsByTerm("Autumn").head.name should be (monitoringPoint1.name)
			pointsByTerm("Christmas vacation").size should be (1)
			pointsByTerm("Christmas vacation").head.name should be (monitoringPoint2.name)
		}
	}

	trait GroupedPointsFixture extends TestSupport {
		val academicYear = AcademicYear(2013)

		val route1 = Fixtures.route("test1")
		val route2 = Fixtures.route("test2")
		val route3 = Fixtures.route("test3")
		val route4 = Fixtures.route("test4")
		val pointSet1 = new MonitoringPointSet
		pointSet1.route = route1
		val pointSet2 = new MonitoringPointSet
		pointSet2.route = route2
		val pointSet3 = new MonitoringPointSet
		pointSet3.route = route3
		val pointSet4 = new MonitoringPointSet
		pointSet4.route = route4

		val commonPoint1 = Fixtures.monitoringPoint("Common name", 1, 2)
		commonPoint1.pointSet = pointSet1
		val commonPoint2 = Fixtures.monitoringPoint("Common name", 1, 2)
		commonPoint2.pointSet = pointSet2
		val differentCaseButCommonPoint = Fixtures.monitoringPoint("common name", 1, 2)
		differentCaseButCommonPoint.pointSet = pointSet3
		val sameNameButDifferentWeekPoint = Fixtures.monitoringPoint("Common name", 1, 1)
		sameNameButDifferentWeekPoint.pointSet = pointSet4
		val duplicatePoint = Fixtures.monitoringPoint("Common name", 1, 2)
		duplicatePoint.pointSet = pointSet1
		val monitoringPoints = List(commonPoint1, commonPoint2, differentCaseButCommonPoint, sameNameButDifferentWeekPoint, duplicatePoint)

		val week1StartDate = new LocalDate(academicYear.startYear, DateTimeConstants.NOVEMBER, 1).toDateTimeAtStartOfDay
		val week1EndDate = new LocalDate(academicYear.startYear, DateTimeConstants.NOVEMBER, 8).toDateTimeAtStartOfDay
		val week2StartDate = new LocalDate(academicYear.startYear, DateTimeConstants.NOVEMBER, 15).toDateTimeAtStartOfDay
		val week2EndDate = new LocalDate(academicYear.startYear, DateTimeConstants.NOVEMBER, 22).toDateTimeAtStartOfDay
		val week1pair = (new Integer(1), new Interval(week1StartDate, week2EndDate))
		val week2pair = (new Integer(2), new Interval(week2StartDate, week2EndDate))
		val weeksForYear = Seq(week1pair, week2pair)
		termService.getAcademicWeeksForYear(new LocalDate(academicYear.startYear, DateTimeConstants.NOVEMBER, 1).toDateTimeAtStartOfDay)	returns weeksForYear
		val autumnTerm = mock[Term]
		autumnTerm.getTermTypeAsString returns "Autumn"
		termService.getTermFromDateIncludingVacations(week1StartDate.withDayOfWeek(DayOfWeek.Thursday.getAsInt)) returns autumnTerm
		termService.getTermFromDateIncludingVacations(week2StartDate.withDayOfWeek(DayOfWeek.Thursday.getAsInt)) returns autumnTerm
	}

	@Test
	def groupedPointsByTermAllRoutesInDept() {
		new GroupedPointsFixture with GroupMonitoringPointsByTerm {
			val groupedPointsByTerm = groupSimilarPointsByTerm(monitoringPoints, Seq(route1, route2, route3, route4), academicYear)

			val autumnPoints = groupedPointsByTerm("Autumn")
			autumnPoints.size should be (2)
			val groupedPoint = autumnPoints(1)
			groupedPoint should not be null
			groupedPoint.routes.size should be (3)
			groupedPoint.routes.head._2 should be (true)
		}
	}

	@Test
	def groupedPointsByTermOneRouteInDept() {
		new GroupedPointsFixture with GroupMonitoringPointsByTerm {
			val groupedPointsByTerm = groupSimilarPointsByTerm(monitoringPoints, Seq(route1), academicYear)

			val autumnPoints = groupedPointsByTerm("Autumn")
			autumnPoints.size should be (2)
			val groupedPoint = autumnPoints(1)
			groupedPoint should not be null
			groupedPoint.routes.size should be (3)
			val option = groupedPoint.routes.find{case(r, b) => r == route2}
			option should be (Option(route2, false))
		}
	}

}
