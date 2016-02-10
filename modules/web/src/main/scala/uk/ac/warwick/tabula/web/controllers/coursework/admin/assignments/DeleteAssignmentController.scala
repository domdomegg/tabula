package uk.ac.warwick.tabula.web.controllers.coursework.admin.assignments

import javax.validation.Valid

import org.springframework.stereotype.Controller
import org.springframework.validation.Errors
import org.springframework.web.bind.annotation._
import uk.ac.warwick.tabula.commands.coursework.assignments._
import uk.ac.warwick.tabula.coursework.web.Routes
import uk.ac.warwick.tabula.web.controllers.coursework.CourseworkController
import uk.ac.warwick.tabula.data.model._
import uk.ac.warwick.tabula.services.turnitinlti.TurnitinLtiService

@Controller
@RequestMapping(value = Array("/coursework/admin/module/{module}/assignments/{assignment}/delete"))
class DeleteAssignmentController extends CourseworkController {

	validatesSelf[DeleteAssignmentCommand]

	@ModelAttribute
	def formObject(@PathVariable module: Module, @PathVariable assignment: Assignment) =
		new DeleteAssignmentCommand(module, mandatory(assignment))

	@RequestMapping(method = Array(RequestMethod.GET, RequestMethod.HEAD))
	def showForm(form: DeleteAssignmentCommand) = {
		val (module, assignment) = (form.module, form.assignment)

		Mav("coursework/admin/assignments/delete",
			"department" -> module.adminDepartment,
			"module" -> module,
			"assignment" -> assignment,
			"maxWordCount" -> Assignment.MaximumWordCount,
			"turnitinFileSizeLimit" -> TurnitinLtiService.maxFileSizeInMegabytes
		).crumbs(Breadcrumbs.Department(module.adminDepartment), Breadcrumbs.Module(module))
	}

	@RequestMapping(method = Array(RequestMethod.POST))
	def submit(@Valid form: DeleteAssignmentCommand, errors: Errors) = {
		if (errors.hasErrors) {
			showForm(form)
		} else {
			form.apply()
			Redirect(Routes.admin.module(form.module))
		}

	}

}
