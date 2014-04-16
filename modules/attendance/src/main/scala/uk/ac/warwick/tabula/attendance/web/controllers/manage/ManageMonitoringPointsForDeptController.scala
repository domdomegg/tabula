package uk.ac.warwick.tabula.attendance.web.controllers.manage

import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.{ModelAttribute, RequestParam, PathVariable, RequestMapping}
import uk.ac.warwick.tabula.AcademicYear
import uk.ac.warwick.tabula.data.model.Department
import uk.ac.warwick.tabula.commands.Appliable
import uk.ac.warwick.tabula.data.model.attendance.MonitoringPointSetTemplate
import uk.ac.warwick.tabula.attendance.commands.manage.ManageMonitoringPointSetCommand
import uk.ac.warwick.tabula.attendance.web.controllers.AttendanceController

/**
 * Displays the screen for creating and editing monitoring point sets
 */
@Controller
@RequestMapping(Array("/manage/{dept}"))
class ManageMonitoringPointsForDeptController extends AttendanceController {

	@ModelAttribute("command")
	def createCommand(@PathVariable dept: Department, @RequestParam(value="academicYear", required = false) academicYear: AcademicYear) =
			ManageMonitoringPointSetCommand(user, dept, Option(academicYear))

	@RequestMapping
	def home(
		@ModelAttribute("command") cmd: Appliable[Seq[MonitoringPointSetTemplate]],
		@RequestParam(value="created", required = false) createdCount: Integer
	) = {
		val templates = cmd.apply()
		Mav("manage/manage", "templates" -> templates, "createdCount" -> createdCount)
	}

}