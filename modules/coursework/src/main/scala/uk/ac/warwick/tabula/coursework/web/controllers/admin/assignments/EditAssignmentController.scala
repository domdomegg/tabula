package uk.ac.warwick.tabula.coursework.web.controllers.admin.assignments

import javax.validation.Valid

import org.springframework.stereotype.Controller
import org.springframework.validation.BeanPropertyBindingResult
import org.springframework.validation.Errors
import org.springframework.web.bind.annotation._
import org.springframework.web.bind.WebDataBinder

import uk.ac.warwick.tabula.commands.coursework.assignments._
import uk.ac.warwick.tabula.data.model._
import uk.ac.warwick.tabula.coursework.web.controllers.CourseworkController
import uk.ac.warwick.tabula.coursework.web.Routes
import uk.ac.warwick.tabula.commands.UpstreamGroup
import uk.ac.warwick.tabula.commands.UpstreamGroupPropertyEditor
import uk.ac.warwick.tabula.{AutowiringFeaturesComponent, CurrentUser}
import uk.ac.warwick.tabula.services.turnitin.Turnitin
import uk.ac.warwick.tabula.services.turnitinlti.TurnitinLtiService

@Controller
@RequestMapping(value = Array("/admin/module/{module}/assignments/{assignment}/edit"))
class EditAssignmentController extends CourseworkController with AutowiringFeaturesComponent {

	validatesSelf[EditAssignmentCommand]

	@ModelAttribute def formObject(@PathVariable("module") module: Module, @PathVariable("assignment") assignment: Assignment, user: CurrentUser) = {
		new EditAssignmentCommand(module, mandatory(assignment), user)
	}

	@RequestMapping
	def showForm(form: EditAssignmentCommand, openDetails: Boolean = false) = {
		form.afterBind()

		val (module, assignment) = (form.module, form.assignment)
		form.copyGroupsFrom(assignment)

		val couldDelete = canDelete(module, assignment)
		Mav("admin/assignments/edit",
			"department" -> module.adminDepartment,
			"module" -> module,
			"assignment" -> assignment,
			"academicYear" -> assignment.academicYear,
			"canDelete" -> couldDelete,
			"availableUpstreamGroups" -> form.availableUpstreamGroups,
			"linkedUpstreamAssessmentGroups" -> form.linkedUpstreamAssessmentGroups,
			"assessmentGroups" -> form.assessmentGroups,
			"maxWordCount" -> Assignment.MaximumWordCount,
			"openDetails" -> openDetails,
			"turnitinFileSizeLimit" -> (if (features.turnitinLTI) TurnitinLtiService.maxFileSize else Turnitin.maxFileSize)
		).crumbs(Breadcrumbs.Department(module.adminDepartment), Breadcrumbs.Module(module))
	}

	@RequestMapping(method = Array(RequestMethod.POST), params = Array("action=submit"))
	def submit(@Valid form: EditAssignmentCommand, errors: Errors) = {
		form.afterBind()
		if (errors.hasErrors) {
			showForm(form)
		} else {
			form.apply()
			Redirect(Routes.admin.module(form.module))
		}

	}

	@RequestMapping(method = Array(RequestMethod.POST), params = Array("action=update"))
	def update(@Valid form: EditAssignmentCommand, errors: Errors) = {
		form.afterBind()
		if (!errors.hasErrors) {
			form.apply()
		}

		showForm(form, openDetails = true)
	}

	@InitBinder
	def upstreamGroupBinder(binder: WebDataBinder) {
		binder.registerCustomEditor(classOf[UpstreamGroup], new UpstreamGroupPropertyEditor)
	}

	private def canDelete(module: Module, assignment: Assignment): Boolean = {
		val cmd = new DeleteAssignmentCommand(module, assignment)
		val errors = new BeanPropertyBindingResult(cmd, "cmd")
		cmd.prechecks(errors)
		!errors.hasErrors
	}

}
