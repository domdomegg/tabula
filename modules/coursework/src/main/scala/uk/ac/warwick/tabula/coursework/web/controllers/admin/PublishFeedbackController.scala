package uk.ac.warwick.tabula.coursework.web.controllers.admin
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.RequestMapping
import uk.ac.warwick.tabula.coursework.web.controllers.CourseworkController
import uk.ac.warwick.tabula.web.Mav
import uk.ac.warwick.tabula.coursework.commands.feedback.{PublishFeedbackCommandState, PublishFeedbackCommand}
import org.springframework.validation.Errors
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.ModelAttribute
import uk.ac.warwick.tabula.data.model.Module
import uk.ac.warwick.tabula.data.model.Assignment
import uk.ac.warwick.tabula.CurrentUser
import uk.ac.warwick.tabula.commands.{SelfValidating, Appliable}
import javax.validation.Valid

@RequestMapping(value = Array("/admin/module/{module}/assignments/{assignment}/publish"))
@Controller
class PublishFeedbackController extends CourseworkController {

	type PublishFeedbackCommand = Appliable[PublishFeedbackCommand.PublishFeedbackResults] with PublishFeedbackCommandState
	validatesSelf[SelfValidating]
	
	@ModelAttribute("publishFeedbackCommand") def cmd(@PathVariable("module") module: Module, @PathVariable("assignment") assignment: Assignment, user: CurrentUser) = {
		PublishFeedbackCommand(module, assignment, user)
	}

	@RequestMapping(method = Array(HEAD, GET), params = Array("!confirm"))
	def confirmation(@ModelAttribute("publishFeedbackCommand") command: PublishFeedbackCommand, errors: Errors): Mav = {
		if (errors.hasErrors) command.prevalidate(errors)
		Mav("admin/assignments/publish/form",
			"assignment" -> command.assignment)
	}

	@RequestMapping(method = Array(POST))
	def submit(@Valid @ModelAttribute("publishFeedbackCommand") command: PublishFeedbackCommand, errors: Errors): Mav = {
		if (errors.hasErrors) {
			confirmation(command, errors)
		} else {
			command.apply()
			Mav("admin/assignments/publish/done", "assignment" -> command.assignment)
		}
	}

}