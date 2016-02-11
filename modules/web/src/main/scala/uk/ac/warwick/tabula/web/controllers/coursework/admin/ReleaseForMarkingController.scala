package uk.ac.warwick.tabula.web.controllers.coursework.admin

import javax.validation.Valid

import org.springframework.stereotype.Controller
import org.springframework.validation.Errors
import org.springframework.web.bind.annotation.{ModelAttribute, PathVariable, RequestMapping}
import uk.ac.warwick.tabula.CurrentUser
import uk.ac.warwick.tabula.commands.coursework.assignments.{ReleaseForMarkingCommand, ReleaseForMarkingState}
import uk.ac.warwick.tabula.commands.{Appliable, SelfValidating}
import uk.ac.warwick.tabula.coursework.web.Routes
import uk.ac.warwick.tabula.web.controllers.coursework.CourseworkController
import uk.ac.warwick.tabula.data.Transactions._
import uk.ac.warwick.tabula.data.model.{Assignment, Feedback, Module}

@Controller
@RequestMapping(value = Array("/coursework/admin/module/{module}/assignments/{assignment}/submissionsandfeedback/release-submissions"))
class ReleaseForMarkingController extends CourseworkController {

	type ReleaseForMarkingCommand = Appliable[List[Feedback]] with ReleaseForMarkingState

	@ModelAttribute("releaseForMarkingCommand")
	def command(@PathVariable module: Module,
				@PathVariable assignment: Assignment,
				user: CurrentUser
				): ReleaseForMarkingCommand = ReleaseForMarkingCommand(module, assignment, user.apparentUser)

	validatesSelf[SelfValidating]

	def confirmView(assignment: Assignment) = Mav("coursework/admin/assignments/submissionsandfeedback/release-submission",
		"assignment" -> assignment)
		.crumbs(Breadcrumbs.Department(assignment.module.adminDepartment), Breadcrumbs.Module(assignment.module))

	def RedirectBack(assignment: Assignment) = Redirect(Routes.admin.assignment.submissionsandfeedback(assignment))

	// shouldn't ever be called as a GET - if it is, just redirect back to the submission list
	@RequestMapping(method = Array(GET))
	def get(@PathVariable assignment: Assignment) = RedirectBack(assignment)

	@RequestMapping(method = Array(POST), params = Array("!confirmScreen"))
	def showForm(@ModelAttribute("releaseForMarkingCommand") cmd: ReleaseForMarkingCommand, errors: Errors) = {
		confirmView(cmd.assignment)
	}

	@RequestMapping(method = Array(POST), params = Array("confirmScreen"))
	def submit(@Valid @ModelAttribute("releaseForMarkingCommand") cmd: ReleaseForMarkingCommand, errors: Errors) = {
		transactional() {
			if (errors.hasErrors)
				showForm(cmd, errors)
			else {
				cmd.apply()
				RedirectBack(cmd.assignment)
			}
		}
	}

}