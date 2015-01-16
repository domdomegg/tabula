package uk.ac.warwick.tabula.coursework.web.controllers.admin

import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.{RequestMapping, PathVariable, ModelAttribute}
import uk.ac.warwick.tabula.coursework.web.Routes
import uk.ac.warwick.tabula.coursework.web.controllers.CourseworkController
import uk.ac.warwick.tabula.coursework.commands.feedback.GenericFeedbackCommand
import uk.ac.warwick.tabula.data.model.{Assignment, Module}
import org.springframework.validation.Errors
import uk.ac.warwick.tabula.web.Mav
import javax.validation.Valid

@Controller
@RequestMapping(Array("/admin/module/{module}/assignments/{assignment}/feedback/generic"))
class GenericFeedbackController extends CourseworkController {

	@ModelAttribute("command")
	def command(@PathVariable module: Module, @PathVariable assignment: Assignment) =
		GenericFeedbackCommand(module, assignment)

	@RequestMapping(method = Array(GET, HEAD))
	def showForm(@PathVariable assignment: Assignment,
							 @ModelAttribute("command") command: GenericFeedbackCommand,
							 errors: Errors): Mav = {

		Mav("admin/assignments/feedback/generic_feedback",
			"command" -> command, "ajax" -> ajax).noLayoutIf(ajax).crumbs(
				Breadcrumbs.Department(assignment.module.adminDepartment),
				Breadcrumbs.Module(assignment.module)
			)
	}

	@RequestMapping(method = Array(POST))
	def submit(@PathVariable assignment: Assignment,
						 @ModelAttribute("command") @Valid command: GenericFeedbackCommand,
						 errors: Errors): Mav = {
		command.apply()
		if(ajax)
			Mav("ajax_success")
		else
			Redirect(Routes.admin.assignment.submissionsandfeedback.summary(assignment))
	}

}