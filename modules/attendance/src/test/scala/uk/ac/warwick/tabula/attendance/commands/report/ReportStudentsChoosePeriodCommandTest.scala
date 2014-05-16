package uk.ac.warwick.tabula.attendance.commands.report

import uk.ac.warwick.tabula.{AcademicYear, Mockito, TestBase}
import org.springframework.validation.BindException
import uk.ac.warwick.tabula.data.model.Department
import uk.ac.warwick.tabula.services.{ModuleAndDepartmentService, CourseAndRouteService, ProfileService, MonitoringPointService, TermService}
import uk.ac.warwick.tabula.data.{SitsStatusDao, ModeOfAttendanceDao}

class ReportStudentsChoosePeriodCommandTest extends TestBase with Mockito {

	trait CommandTestSupport extends ReportStudentsChoosePeriodCommandValidation with ReportStudentsState {
		val termService = mock[TermService]
		val monitoringPointService = mock[MonitoringPointService]
		val profileService = mock[ProfileService]
		val courseAndRouteService = mock[CourseAndRouteService]
		val modeOfAttendanceDao = mock[ModeOfAttendanceDao]
		val sitsStatusDao = mock[SitsStatusDao]
		val moduleAndDepartmentService = mock[ModuleAndDepartmentService]
	}

	trait Fixture {
		val cmd = new ReportStudentsChoosePeriodCommand(new Department(), new AcademicYear(2009) ) with CommandTestSupport
		val availablePeriod = "Autumnal Term"
		val unavailablePeriod = "Spring"
		val unspecifiedPeriod = "Summer!"
		cmd.availablePeriods = Seq((availablePeriod, true), (unavailablePeriod, false))
	}

	@Test
	def validPeriod () {
		new Fixture {
			var errors = new BindException(cmd, "command")
			cmd.period = availablePeriod
			cmd.validate(errors)

			errors.hasErrors should be (false)
			errors.hasFieldErrors should be (false)
		}
	}

	@Test
	def unavailablePeriod () {
		new Fixture {
			var errors = new BindException(cmd, "command")
			cmd.period = unavailablePeriod
			cmd.validate(errors)

			errors.hasErrors should be(true)
			errors.hasFieldErrors should be (true)
			errors.getErrorCount should be (1)
			errors.getFieldErrors("period").size() should be (1)
		}
	}

	@Test
	def periodNotInAvailablePeriods() {
		new Fixture {
			var errors = new BindException(cmd, "command")
			cmd.period = unspecifiedPeriod
			cmd.validate(errors)

			errors.getFieldErrorCount should be (1)
			errors.hasFieldErrors should be (true)
			errors.getFieldErrors("period").size() should be (1)
		}
	}

}
