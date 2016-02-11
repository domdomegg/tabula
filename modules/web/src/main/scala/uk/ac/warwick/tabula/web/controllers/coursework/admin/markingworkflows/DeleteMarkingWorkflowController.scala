package uk.ac.warwick.tabula.web.controllers.coursework.admin.markingworkflows

import javax.validation.Valid

import org.springframework.stereotype.Controller
import org.springframework.validation.Errors
import org.springframework.web.bind.annotation._

import uk.ac.warwick.tabula.commands.coursework.markingworkflows.{DeleteMarkingWorkflowCommandState, DeleteMarkingWorkflowCommand}
import uk.ac.warwick.tabula.data.model._
import uk.ac.warwick.tabula.web.controllers.coursework.CourseworkController
import uk.ac.warwick.tabula.web.Mav
import uk.ac.warwick.tabula.coursework.web.Routes
import uk.ac.warwick.tabula.commands.{Appliable, SelfValidating}
import uk.ac.warwick.tabula.web.controllers.coursework.admin.markingworkflows.DeleteMarkingWorkflowController.DeleteMarkingWorkflowCommand

object DeleteMarkingWorkflowController {
	type DeleteMarkingWorkflowCommand = Appliable[Unit]
		with SelfValidating with DeleteMarkingWorkflowCommandState
}

@Controller
@RequestMapping(value=Array("/coursework/admin/department/{department}/markingworkflows/delete/{markingworkflow}"))
class DeleteMarkingWorkflowController extends CourseworkController {

	validatesSelf[SelfValidating]

	@ModelAttribute("command")
	def cmd(@PathVariable department: Department, @PathVariable markingworkflow: MarkingWorkflow): DeleteMarkingWorkflowCommand =
		DeleteMarkingWorkflowCommand(department, markingworkflow)

	@RequestMapping(method=Array(GET, HEAD))
	def form(@ModelAttribute("command") cmd: DeleteMarkingWorkflowCommand): Mav = {
		Mav("coursework/admin/markingworkflows/delete").noLayoutIf(ajax)
	}

	@RequestMapping(method=Array(POST))
	def submit(@Valid @ModelAttribute("command") cmd: DeleteMarkingWorkflowCommand, errors: Errors): Mav = {
		if (errors.hasErrors) {
			form(cmd)
		} else {
			cmd.apply()
			Redirect(Routes.admin.markingWorkflow.list(cmd.department))
		}
	}

}