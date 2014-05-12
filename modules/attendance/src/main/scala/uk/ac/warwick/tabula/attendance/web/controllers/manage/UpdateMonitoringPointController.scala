package uk.ac.warwick.tabula.attendance.web.controllers.manage

import org.springframework.web.bind.annotation.{PathVariable, ModelAttribute, RequestMapping}
import uk.ac.warwick.tabula.data.model.attendance.{MonitoringPoint, MonitoringPointSet}
import uk.ac.warwick.tabula.commands.Appliable
import javax.validation.Valid
import org.springframework.validation.Errors
import org.springframework.stereotype.Controller
import uk.ac.warwick.tabula.attendance.commands.manage.UpdateMonitoringPointCommand

@Controller
@RequestMapping(Array("/manage/{dept}/2013/sets/{set}/edit/points/{point}/edit"))
class UpdateMonitoringPointController extends AbstractManageMonitoringPointController {

	@ModelAttribute("command")
	def createCommand(
		@PathVariable set: MonitoringPointSet,
		@PathVariable point: MonitoringPoint
	) =
		UpdateMonitoringPointCommand(set, point)

	@RequestMapping(method=Array(GET, HEAD))
	def form(@ModelAttribute("command") cmd: Appliable[MonitoringPoint]) = {
		Mav("manage/point/update_form").noLayoutIf(ajax)
	}

	@RequestMapping(method=Array(POST))
	def submitModal(@Valid @ModelAttribute("command") cmd: Appliable[MonitoringPoint], errors: Errors) = {
		if (errors.hasErrors) {
			form(cmd)
		} else {
			cmd.apply()
			Mav("manage/set/_monitoringPointsPersisted").noLayoutIf(ajax)
		}
	}

}


