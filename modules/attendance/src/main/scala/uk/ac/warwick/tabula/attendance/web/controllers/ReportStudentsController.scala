package uk.ac.warwick.tabula.attendance.web.controllers

import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.{PathVariable, ModelAttribute, RequestMapping}
import uk.ac.warwick.tabula.commands.{SelfValidating, Appliable}
import uk.ac.warwick.tabula.AcademicYear
import uk.ac.warwick.tabula.data.model.Department
import uk.ac.warwick.tabula.attendance.commands.report.{ReportStudentsState, ReportStudentsConfirmCommand, ReportStudentsChoosePeriodCommand}
import javax.validation.Valid
import org.springframework.validation.Errors
import uk.ac.warwick.tabula.data.model.attendance.MonitoringPointReport
import uk.ac.warwick.tabula.attendance.web.Routes
import uk.ac.warwick.tabula.attendance.commands.report.ReportStudentsChoosePeriodCommand.StudentReportStatus

@Controller
@RequestMapping(Array("/report/{department}"))
class ReportStudentsChoosePeriodController extends AttendanceController {

	validatesSelf[SelfValidating]

	@ModelAttribute("command")
	def command(@PathVariable department: Department) =
		ReportStudentsChoosePeriodCommand(department, mandatory(AcademicYear(2013)))

	@RequestMapping(method = Array(GET, HEAD))
	def form(@ModelAttribute("command") cmd: Appliable[Seq[StudentReportStatus]]) = {
		Mav("report/periods")
	}

	@RequestMapping(method = Array(POST))
	def submit(@Valid @ModelAttribute("command") cmd: Appliable[Seq[StudentReportStatus]], errors: Errors) = {
		if(errors.hasErrors) {
			form(cmd)
		} else {
			val studentReportStatuses = cmd.apply()
			Mav("report/students", "studentReportStatuses" -> studentReportStatuses, "unrecordedStudentsCount" -> studentReportStatuses.count(_.unrecorded > 0))
		}
	}

}

@Controller
@RequestMapping(Array("/report/{department}/confirm"))
class ReportStudentsConfirmController extends AttendanceController {

	validatesSelf[SelfValidating]

	@ModelAttribute("command")
	def command(@PathVariable department: Department) =
		ReportStudentsConfirmCommand(department, user)

	@RequestMapping(method = Array(GET, HEAD))
	def form(@ModelAttribute("command") cmd: Appliable[Seq[MonitoringPointReport]]) = {
		Mav("report/confirm")
	}

	@RequestMapping(method = Array(POST))
	def submit(
		@Valid @ModelAttribute("command") cmd: Appliable[Seq[MonitoringPointReport]] with ReportStudentsState,
		errors: Errors,
		@PathVariable department: Department
	) = {
		if(errors.hasErrors) {
			form(cmd)
		} else {
			val reports = cmd.apply()
			val redirectObjects = Map("reports" -> reports.size, "monitoringPeriod" -> cmd.period, "academicYear" -> cmd.academicYear) ++ cmd.filterMap
			Redirect(Routes.department.viewStudents(department), redirectObjects)
		}
	}

}
