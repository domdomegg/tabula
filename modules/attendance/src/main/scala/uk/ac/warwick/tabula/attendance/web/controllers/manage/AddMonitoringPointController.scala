package uk.ac.warwick.tabula.attendance.web.controllers.manage

import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.{PathVariable, ModelAttribute, RequestMapping}
import uk.ac.warwick.tabula.commands.Appliable
import javax.validation.Valid
import org.springframework.validation.Errors
import uk.ac.warwick.tabula.data.model.Department
import uk.ac.warwick.tabula.data.model.attendance.MonitoringPoint
import uk.ac.warwick.tabula.attendance.commands.manage.AddMonitoringPointCommand

@Controller
@RequestMapping(Array("/manage/{dept}/sets/add/points/add"))
class AddMonitoringPointController extends AbstractManageMonitoringPointController {

	@ModelAttribute("command")
	def createCommand(@PathVariable dept: Department) = AddMonitoringPointCommand(dept)

	@RequestMapping(method=Array(POST), params = Array("form"))
	def form(@ModelAttribute("command") cmd: Appliable[MonitoringPoint]) = {
		Mav("manage/point/add_form").noLayoutIf(ajax)
	}

	@RequestMapping(method=Array(POST))
	def submitModal(@Valid @ModelAttribute("command") cmd: Appliable[MonitoringPoint], errors: Errors) = {
		if (errors.hasErrors) {
			form(cmd)
		} else {
			cmd.apply()
			Mav("manage/set/_monitoringPoints").noLayoutIf(ajax)
		}
	}

}
