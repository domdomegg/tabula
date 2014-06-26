package uk.ac.warwick.tabula.attendance.web.controllers.view.old

import javax.validation.Valid

import org.springframework.stereotype.Controller
import org.springframework.validation.Errors
import org.springframework.web.bind.annotation.{ModelAttribute, PathVariable, RequestMapping, RequestParam}
import uk.ac.warwick.tabula.AcademicYear
import uk.ac.warwick.tabula.attendance.commands.GroupedMonitoringPoint
import uk.ac.warwick.tabula.attendance.commands.view.old.{ViewMonitoringPointsCommand, ViewMonitoringPointsState}
import uk.ac.warwick.tabula.attendance.web.controllers.AttendanceController
import uk.ac.warwick.tabula.commands.{Appliable, SelfValidating}
import uk.ac.warwick.tabula.data.model.Department
import uk.ac.warwick.tabula.data.model.attendance.MonitoringPoint

@Controller
@RequestMapping(value=Array("/view/{department}/2013/points"))
class OldViewMonitoringPointsController extends AttendanceController {

	validatesSelf[SelfValidating]

	@ModelAttribute("command")
	def command(@PathVariable department: Department) =
		ViewMonitoringPointsCommand(department, Option(AcademicYear(2013)), user)

	@RequestMapping
	def filter(
		@Valid @ModelAttribute("command") cmd: Appliable[Map[String, Seq[GroupedMonitoringPoint]]] with ViewMonitoringPointsState,
		errors: Errors,
		@RequestParam(value="updatedMonitoringPoint", required = false) updatedMonitoringPoint: MonitoringPoint
	) = {
		if (errors.hasErrors) {
			if (ajax)
				Mav("home/view_points_results").noLayout()
			else
				Mav("home/view_points_filter", "updatedMonitoringPoint" -> updatedMonitoringPoint).crumbs(Breadcrumbs.Old.ViewDepartment(cmd.department))
		} else {
			val results = cmd.apply()

			if (ajax)
				Mav("home/view_points_results", "pointsMap" -> results).noLayout()
			else
				Mav("home/view_points_filter",
					"pointsMap" -> results,
					"updatedMonitoringPoint" -> updatedMonitoringPoint
				).crumbs(Breadcrumbs.Old.ViewDepartment(cmd.department))
		}
	}

}