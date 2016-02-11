package uk.ac.warwick.tabula.web.controllers.coursework.admin

import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.{PathVariable, ModelAttribute, RequestMapping}
import uk.ac.warwick.tabula.CurrentUser
import uk.ac.warwick.tabula.web.controllers.coursework.CourseworkController
import uk.ac.warwick.tabula.data.model.{Module, Assignment}
import uk.ac.warwick.tabula.commands.coursework.assignments.AddMarkerFeedbackCommand
import org.springframework.validation.Errors
import uk.ac.warwick.tabula.web.Mav
import uk.ac.warwick.tabula.coursework.web.Routes
import uk.ac.warwick.userlookup.User

@Controller
@RequestMapping(value = Array("/coursework/admin/module/{module}/assignments/{assignment}/marker/{marker}/feedback"))
class AddMarkerFeedbackController extends CourseworkController {

	@ModelAttribute def command(@PathVariable module: Module,
															@PathVariable assignment: Assignment,
															@PathVariable marker: User,
															user: CurrentUser) =
		new AddMarkerFeedbackCommand(module, assignment, marker, user)

	@RequestMapping(method = Array(HEAD, GET))
	def uploadForm(@PathVariable module: Module,
								 @PathVariable assignment: Assignment,
								 @PathVariable marker: User,
								 @ModelAttribute cmd: AddMarkerFeedbackCommand): Mav = {
		Mav("coursework/admin/assignments/markerfeedback/form",
			"isProxying" -> cmd.isProxying,
			"proxyingAs" -> marker
		).crumbs(
			Breadcrumbs.Standard(s"Marking for ${assignment.name}", Some(Routes.admin.assignment.markerFeedback(assignment, marker)), "")
		)
	}

	@RequestMapping(method = Array(POST), params = Array("!confirm"))
	def confirmUpload(@PathVariable module: Module,
										@PathVariable assignment: Assignment,
										@PathVariable marker: User,
										@ModelAttribute cmd: AddMarkerFeedbackCommand,
										errors: Errors): Mav = {
		cmd.preExtractValidation(errors)
		if (errors.hasErrors) {
			uploadForm(module, assignment, marker, cmd)
		} else {
			cmd.postExtractValidation(errors)
			cmd.processStudents()
			Mav("coursework/admin/assignments/markerfeedback/preview",
				"isProxying" -> cmd.isProxying,
				"proxyingAs" -> marker
			).crumbs(
				Breadcrumbs.Standard(s"Marking for ${assignment.name}", Some(Routes.admin.assignment.markerFeedback(assignment, marker)), "")
			)
		}
	}

	@RequestMapping(method = Array(POST), params = Array("confirm=true"))
	def doUpload(@PathVariable module: Module,
							 @PathVariable assignment: Assignment,
							 @PathVariable marker: User,
							 @ModelAttribute cmd: AddMarkerFeedbackCommand,
							 errors: Errors): Mav = {

		cmd.preExtractValidation(errors)
		cmd.postExtractValidation(errors)

		if (errors.hasErrors) {
			confirmUpload(module, assignment, marker, cmd, errors)
		} else {
			// do apply, redirect back
			cmd.apply()
			Redirect(Routes.admin.assignment.markerFeedback(assignment, marker))
		}
	}

}

@Controller
@RequestMapping(value = Array("/coursework/admin/module/{module}/assignments/{assignment}/marker/feedback"))
class AddMarkerFeedbackControllerCurrentUser extends CourseworkController {

	@RequestMapping
	def redirect(@PathVariable assignment: Assignment, currentUser: CurrentUser) = {
		Redirect(Routes.admin.assignment.markerFeedback.feedback(assignment, currentUser.apparentUser))
	}
}