package uk.ac.warwick.tabula.web.controllers.attendance.manage

import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.{RequestParam, ModelAttribute, PathVariable, RequestMapping}
import uk.ac.warwick.tabula.data.model.Department
import uk.ac.warwick.tabula.web.controllers.attendance.{HasMonthNames, AttendanceController}
import uk.ac.warwick.tabula.AcademicYear
import uk.ac.warwick.tabula.data.model.attendance.{AttendanceMonitoringPointStyle, AttendanceMonitoringPointType}
import uk.ac.warwick.tabula.commands.Appliable
import uk.ac.warwick.tabula.commands.attendance.manage.FindPointsCommand
import org.springframework.beans.factory.annotation.Autowired
import uk.ac.warwick.tabula.JavaImports._
import uk.ac.warwick.tabula.commands.attendance.manage.FindPointsResult
import uk.ac.warwick.tabula.services.attendancemonitoring.AttendanceMonitoringService

@Controller
@RequestMapping(Array("/attendance/manage/{department}/{academicYear}/editpoints"))
class SelectAttendancePointsToEditController extends AttendanceController with HasMonthNames {

	@Autowired var attendanceMonitoringService: AttendanceMonitoringService = _

	@ModelAttribute("findCommand")
	def findCommand(@PathVariable department: Department, @PathVariable academicYear: AcademicYear) =
		FindPointsCommand(mandatory(department), mandatory(academicYear), None)

	@ModelAttribute("allSchemes")
	def allSchemes(@PathVariable department: Department, @PathVariable academicYear: AcademicYear) =
		attendanceMonitoringService.listSchemes(department, academicYear)

	@RequestMapping
	def home(
		@ModelAttribute("findCommand") findCommand: Appliable[FindPointsResult],
		@PathVariable department: Department,
		@PathVariable academicYear: AcademicYear,
		@RequestParam(required = false) points: JInteger,
		@RequestParam(required = false) actionCompleted: String
	) = {
		val findCommandResult = findCommand.apply()
		Mav("attendance/manage/editpoints",
			"findResult" -> findCommandResult,
			"allTypes" -> AttendanceMonitoringPointType.values,
			"allStyles" -> AttendanceMonitoringPointStyle.values,
			"newPoints" -> Option(points).getOrElse(0),
			"actionCompleted" -> actionCompleted
		).crumbs(
			Breadcrumbs.Manage.Home,
			Breadcrumbs.Manage.Department(department),
			Breadcrumbs.Manage.DepartmentForYear(department, academicYear)
		)

	}

}
