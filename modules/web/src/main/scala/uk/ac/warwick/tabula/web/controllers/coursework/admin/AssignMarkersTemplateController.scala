package uk.ac.warwick.tabula.web.controllers.coursework.admin

import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.{PathVariable, ModelAttribute, RequestMapping}
import uk.ac.warwick.tabula.commands.Appliable
import uk.ac.warwick.tabula.commands.coursework.assignments.AssignMarkersTemplateCommand
import uk.ac.warwick.tabula.data.model.{Exam, Assignment}
import javax.validation.Valid
import uk.ac.warwick.tabula.web.views.ExcelView

@Controller
@RequestMapping(value=Array("/coursework/admin/module/{module}/assignments/{assignment}/assign-markers/template"))
class AssignMarkersTemplateController {


	@ModelAttribute("command")
	def command(@PathVariable assignment: Assignment) = AssignMarkersTemplateCommand(assignment)

	@RequestMapping
	def getTemplate(@Valid @ModelAttribute("command") cmd: Appliable[ExcelView]) = {
		cmd.apply()
	}

}