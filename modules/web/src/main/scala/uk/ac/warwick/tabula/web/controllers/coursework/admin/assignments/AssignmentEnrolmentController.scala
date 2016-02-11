package uk.ac.warwick.tabula.web.controllers.coursework.admin.assignments

import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation._
import org.springframework.web.bind.WebDataBinder

import uk.ac.warwick.tabula.commands.coursework.assignments._
import uk.ac.warwick.tabula.web.controllers.coursework.CourseworkController
import uk.ac.warwick.tabula.data.model._
import uk.ac.warwick.tabula.commands.{UpstreamGroupPropertyEditor, UpstreamGroup}
import uk.ac.warwick.tabula.helpers.Logging
import uk.ac.warwick.tabula.AcademicYear


/**
 * Controller to populate the user listing for editing, without persistence
 */
@Controller
@RequestMapping(value = Array("/coursework/admin/module/{module}/assignments/enrolment/{academicYear}"))
class AssignmentEnrolmentController extends CourseworkController with Logging{

	validatesSelf[EditAssignmentEnrolmentCommand]

	@ModelAttribute def formObject(@PathVariable module: Module, @PathVariable academicYear: AcademicYear) = {
		val cmd = new EditAssignmentEnrolmentCommand(mandatory(module), academicYear)
		cmd.upstreamGroups.clear()
		cmd
	}

	@RequestMapping
	def showForm(form: EditAssignmentEnrolmentCommand, openDetails: Boolean = false) = {
		form.afterBind()

		logger.info(s"Assignment Enrolment includeCount: ${form.membershipInfo.includeCount}")
		Mav("coursework/admin/assignments/enrolment",
			"department" -> form.module.adminDepartment,
			"module" -> form.module,
			"academicYear" -> form.academicYear,
			"availableUpstreamGroups" -> form.availableUpstreamGroups,
			"linkedUpstreamAssessmentGroups" -> form.linkedUpstreamAssessmentGroups,
			"assessmentGroups" -> form.assessmentGroups,
			"openDetails" -> openDetails)
			.noLayout()
	}

	@InitBinder
	def upstreamGroupBinder(binder: WebDataBinder) {
		binder.registerCustomEditor(classOf[UpstreamGroup], new UpstreamGroupPropertyEditor)
	}
}