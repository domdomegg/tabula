package uk.ac.warwick.tabula.attendance.web.controllers

import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.{ModelAttribute, PathVariable, RequestParam, RequestMapping}
import uk.ac.warwick.tabula.data.model.{Department, StudentMember}
import uk.ac.warwick.tabula.commands.{TaskBenchmarking, Appliable, CommandInternal, Unaudited, ReadOnly, ComposableCommand}
import uk.ac.warwick.tabula.system.permissions.{PermissionsChecking, RequiresPermissionsChecking}
import uk.ac.warwick.tabula.permissions.Permissions
import uk.ac.warwick.tabula.AcademicYear
import uk.ac.warwick.tabula.attendance.commands.{BuildStudentPointsData, StudentPointsData, GroupMonitoringPointsByTerm}
import uk.ac.warwick.tabula.services.{AutowiringUserLookupComponent, AutowiringProfileServiceComponent, AutowiringMonitoringPointServiceComponent, AutowiringTermServiceComponent}
import org.joda.time.DateTime

object StudentViewCommand {
	def apply(student: StudentMember, academicYearOption: Option[AcademicYear]) =
		new StudentViewCommand(student, academicYearOption)
			with ComposableCommand[StudentPointsData]
			with StudentViewPermissions
			with ReadOnly with Unaudited
			with AutowiringTermServiceComponent
			with AutowiringMonitoringPointServiceComponent
			with AutowiringUserLookupComponent
			with GroupMonitoringPointsByTerm
}

abstract class StudentViewCommand(val student: StudentMember, val academicYearOption: Option[AcademicYear])
	extends CommandInternal[StudentPointsData] with TaskBenchmarking with BuildStudentPointsData with StudentViewCommandState {

	def applyInternal() = {
		benchmarkTask("Build data") { buildData(Seq(student), academicYear).head }
	}

}

trait StudentViewPermissions extends RequiresPermissionsChecking {
	this: StudentViewCommandState =>

	def permissionsCheck(p: PermissionsChecking) {
		p.PermissionCheck(Permissions.MonitoringPoints.View, student)
	}
}

trait StudentViewCommandState {
	def student: StudentMember
	def academicYearOption: Option[AcademicYear]
	val thisAcademicYear = AcademicYear.guessByDate(DateTime.now())
	val academicYear = academicYearOption.getOrElse(thisAcademicYear)
}

@Controller
@RequestMapping(Array("/view/{department}/2013/students/{student}"))
class StudentViewController extends AttendanceController {

	@ModelAttribute("command")
	def command(@PathVariable student: StudentMember) = {
		StudentViewCommand(student, Option(AcademicYear(2013)))
	}

	@RequestMapping
	def home(
		@ModelAttribute("command") cmd: Appliable[StudentPointsData] with StudentViewCommandState,
		@PathVariable department: Department
	) = {
		Mav("home/student",
			"student" -> cmd.student,
			"pointsByTerm" -> cmd.apply().pointsByTerm,
			"department" ->  department
		).crumbs(Breadcrumbs.ViewDepartmentStudents(department))
	}

}