package uk.ac.warwick.tabula.web.controllers.coursework.admin

import javax.validation.Valid

import org.springframework.stereotype.Controller
import org.springframework.validation.Errors
import org.springframework.web.bind.annotation.{PathVariable, ModelAttribute, RequestMapping}
import uk.ac.warwick.tabula.CurrentUser
import uk.ac.warwick.tabula.commands.{SelfValidating, Appliable}
import uk.ac.warwick.tabula.commands.coursework.assignments.{BulkModerationApprovalState, BulkModerationApprovalCommand}
import uk.ac.warwick.tabula.commands.coursework.feedback.GenerateGradesFromMarkCommand
import uk.ac.warwick.tabula.coursework.web.Routes
import uk.ac.warwick.tabula.web.controllers.coursework.CourseworkController
import uk.ac.warwick.tabula.data.model.{Module, Assignment}
import uk.ac.warwick.tabula.web.Mav
import uk.ac.warwick.userlookup.User

@Controller
@RequestMapping(Array("/coursework/admin/module/{module}/assignments/{assignment}/marker/{marker}/moderation/bulk-approve"))
class BulkModerationApprovalController extends CourseworkController {

	validatesSelf[SelfValidating]

	@ModelAttribute("command")
	def command(
		@PathVariable module: Module,
		@PathVariable assignment: Assignment,
		@PathVariable marker: User,
		submitter: CurrentUser
	) = BulkModerationApprovalCommand(mandatory(assignment), marker, submitter, GenerateGradesFromMarkCommand(mandatory(module), mandatory(assignment)))

	@RequestMapping(method = Array(POST), params = Array("!confirmScreen"))
	def showForm(@ModelAttribute("command") command: Appliable[Unit], errors: Errors): Mav = {
		Mav("coursework/admin/assignments/markerfeedback/bulk-approve")
	}

	@RequestMapping(method = Array(POST), params = Array("confirmScreen"))
	def submit(
							@PathVariable assignment: Assignment,
							@ModelAttribute("command") @Valid command: Appliable[Unit] with BulkModerationApprovalState,
							errors: Errors): Mav =
	{
		if (errors.hasErrors) {
			showForm(command, errors)
		} else {
			command.apply()
			Redirect(Routes.admin.assignment.markerFeedback(assignment, command.marker))
		}
	}

}

@Controller
@RequestMapping(value = Array("/coursework/admin/module/{module}/assignments/{assignment}/marker/feedback/online/moderation/bulk"))
class BulkModerationApprovalControllerCurrentUser extends CourseworkController {

	@RequestMapping
	def redirect(@PathVariable assignment: Assignment, currentUser: CurrentUser) = {
		Redirect(Routes.admin.assignment.markerFeedback.bulkApprove(assignment, currentUser.apparentUser))
	}
}