package uk.ac.warwick.tabula.coursework.web.controllers.admin

import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.{ModelAttribute, PathVariable, RequestMapping}
import uk.ac.warwick.tabula.coursework.web.controllers.CourseworkController
import uk.ac.warwick.tabula.data.model.Assignment
import uk.ac.warwick.tabula.coursework.commands.assignments.ListMarkerFeedbackCommand
import uk.ac.warwick.tabula.web.Mav
import uk.ac.warwick.tabula.CurrentUser

@Controller
@RequestMapping(value = Array("/admin/module/{module}/assignments/{assignment}/marker/list"))
class ListMarkerFeedbackController  extends CourseworkController {

	@ModelAttribute def command(@PathVariable assignment: Assignment, user: CurrentUser) =
		new ListMarkerFeedbackCommand(assignment, user, true) //TODO switch flag depending on which marker this is

	@RequestMapping(method = Array(HEAD, GET))
	def list(@ModelAttribute command: ListMarkerFeedbackCommand): Mav = {
		val markerFeedbackItems = command.apply()
		Mav("admin/assignments/markerfeedback/list",
			"items" -> markerFeedbackItems)
	}

}
