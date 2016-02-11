package uk.ac.warwick.tabula.web.controllers.attendance.view

import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.{ModelAttribute, PathVariable, RequestMapping}
import uk.ac.warwick.tabula.web.controllers.attendance.{HasMonthNames, AttendanceController}
import uk.ac.warwick.tabula.AcademicYear
import uk.ac.warwick.tabula.data.model.{StudentMember, Department}
import uk.ac.warwick.tabula.commands.attendance.view.ViewStudentAttendanceCommand
import uk.ac.warwick.tabula.commands.Appliable
import uk.ac.warwick.tabula.data.model.attendance.{AttendanceMonitoringCheckpoint, AttendanceMonitoringPoint}

@Controller
@RequestMapping(Array("/attendance/view/{department}/{academicYear}/students/{student}"))
class ViewStudentAttendanceController extends AttendanceController with HasMonthNames {

	@ModelAttribute("command")
	def command(@PathVariable department: Department, @PathVariable academicYear: AcademicYear, @PathVariable student: StudentMember) =
		ViewStudentAttendanceCommand(mandatory(department), mandatory(academicYear), mandatory(student))

	@RequestMapping
	def home(
		@PathVariable department: Department,
		@PathVariable academicYear: AcademicYear,
		@ModelAttribute("command") cmd: Appliable[Map[String, Seq[(AttendanceMonitoringPoint, AttendanceMonitoringCheckpoint)]]]
	) = {
		Mav("attendance/view/student",
			"groupedPointMap" -> cmd.apply()
		).crumbs(
			Breadcrumbs.View.Home,
			Breadcrumbs.View.Department(department),
			Breadcrumbs.View.DepartmentForYear(department, academicYear),
			Breadcrumbs.View.Students(department, academicYear)
		)
	}

}