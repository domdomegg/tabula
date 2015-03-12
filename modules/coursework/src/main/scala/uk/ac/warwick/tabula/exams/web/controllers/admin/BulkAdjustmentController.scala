package uk.ac.warwick.tabula.exams.web.controllers.admin

import javax.validation.Valid

import org.springframework.stereotype.Controller
import org.springframework.validation.Errors
import org.springframework.web.bind.annotation.{ModelAttribute, PathVariable, RequestMapping}
import uk.ac.warwick.tabula.commands.{Appliable, SelfValidating}
import uk.ac.warwick.tabula.coursework.commands.feedback.GenerateGradesFromMarkCommand
import uk.ac.warwick.tabula.data.model.{Mark, Module, Exam}
import uk.ac.warwick.tabula.exams.commands.{BulkAdjustmentTemplateCommand, BulkAdjustmentCommand}
import uk.ac.warwick.tabula.exams.web.Routes
import uk.ac.warwick.tabula.exams.web.controllers.ExamsController
import uk.ac.warwick.tabula.helpers.SpreadsheetHelpers
import uk.ac.warwick.tabula.web.views.ExcelView

@Controller
@RequestMapping(Array("/exams/admin/module/{module}/{academicYear}/exams/{exam}/feedback/bulk-adjustment"))
class BulkAdjustmentController extends ExamsController {

	validatesSelf[SelfValidating]

	@ModelAttribute("command")
	def command(@PathVariable module: Module, @PathVariable exam: Exam) =
		BulkAdjustmentCommand(
			mandatory(exam),
			GenerateGradesFromMarkCommand(mandatory(module), mandatory(exam)),
			SpreadsheetHelpers,
			user
		)

	@RequestMapping(method = Array(GET, HEAD))
	def form = {
		Mav("exams/admin/adjustments/bulk/form",
			"StudentIdHeader" -> BulkAdjustmentCommand.StudentIdHeader,
			"MarkHeader" -> BulkAdjustmentCommand.MarkHeader,
			"GradeHeader" -> BulkAdjustmentCommand.GradeHeader
		)
	}

	@RequestMapping(method = Array(POST))
	def upload(@Valid @ModelAttribute("command") cmd: Appliable[Seq[Mark]], errors: Errors) = {
		if (errors.hasFieldErrors("file"))
			form
		else
			Mav("exams/admin/adjustments/bulk/preview")
	}

	@RequestMapping(method = Array(POST), params = Array("confirmStep=true"))
	def confirm(
		@Valid @ModelAttribute("command") cmd: Appliable[Seq[Mark]], errors: Errors,
		@PathVariable exam: Exam
	) = {
		if (errors.hasFieldErrors("defaultReason") || errors.hasFieldErrors("defaultComment")) {
			upload(cmd, errors)
		} else {
			cmd.apply()
			Redirect(Routes.admin.exam(exam))
		}
	}

}

@Controller
@RequestMapping(Array("/exams/admin/module/{module}/{academicYear}/exams/{exam}/feedback/bulk-adjustment/template"))
class BulkAdjustmentTemplateController extends ExamsController {

	@ModelAttribute("command")
	def command(@PathVariable module: Module, @PathVariable exam: Exam): Appliable[ExcelView] =
		BulkAdjustmentTemplateCommand(mandatory(exam))

	@RequestMapping(method = Array(GET, HEAD))
	def home(@ModelAttribute("command") cmd: Appliable[ExcelView]) = {
		cmd.apply()
	}

}
