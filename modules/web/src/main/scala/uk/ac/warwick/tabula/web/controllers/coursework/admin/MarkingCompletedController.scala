package uk.ac.warwick.tabula.web.controllers.coursework.admin

import javax.validation.Valid

import org.springframework.stereotype.Controller
import org.springframework.validation.Errors
import org.springframework.web.bind.annotation.{ModelAttribute, PathVariable, RequestMapping}
import uk.ac.warwick.tabula.CurrentUser
import uk.ac.warwick.tabula.commands.{UserAware, Appliable, SelfValidating}
import uk.ac.warwick.tabula.commands.coursework.assignments.{CanProxy, NextMarkerFeedback, MarkingCompletedState, MarkingCompletedCommand}
import uk.ac.warwick.tabula.coursework.web.Routes
import uk.ac.warwick.tabula.web.controllers.coursework.CourseworkController
import uk.ac.warwick.tabula.data.Transactions._
import uk.ac.warwick.tabula.data.model.{Assignment, MarkerFeedback, Module}
import uk.ac.warwick.userlookup.User

import scala.collection.JavaConversions._

@Controller
@RequestMapping(value = Array("/coursework/admin/module/{module}/assignments/{assignment}/marker/{marker}/marking-completed"))
class MarkingCompletedController extends CourseworkController {

	validatesSelf[SelfValidating]
	type MarkingCompletedCommand = Appliable[Unit] with MarkingCompletedState with UserAware with NextMarkerFeedback
		with CanProxy

	@ModelAttribute("markingCompletedCommand")
	def command(
		@PathVariable module: Module,
		@PathVariable assignment: Assignment,
		@PathVariable marker: User,
		submitter: CurrentUser
	): MarkingCompletedCommand = MarkingCompletedCommand(mandatory(module), mandatory(assignment), marker, submitter)


	def RedirectBack(assignment: Assignment, command: MarkingCompletedCommand) = {
		if (command.onlineMarking) {
			Redirect(Routes.admin.assignment.markerFeedback.onlineFeedback(assignment, command.user))
		} else {
			Redirect(Routes.admin.assignment.markerFeedback(assignment, command.user))
		}
	}

	// shouldn't ever be called as a GET - if it is, just redirect back to the submission list
	@RequestMapping(method = Array(GET))
	def get(@PathVariable assignment: Assignment, @ModelAttribute("markingCompletedCommand") form: MarkingCompletedCommand) = RedirectBack(assignment, form)

	@RequestMapping(method = Array(POST), params = Array("!confirmScreen"))
	def showForm(
		@PathVariable module: Module,
		@PathVariable assignment: Assignment,
		@PathVariable marker: User,
		@ModelAttribute("markingCompletedCommand") form: MarkingCompletedCommand,
		errors: Errors
	) = {
		val isUserALaterMarker = form.markerFeedback.exists { markerFeedback =>
			def checkNextMarkerFeedbackForMarker(thisMarkerFeedback: MarkerFeedback): Boolean = {
				form.nextMarkerFeedback(thisMarkerFeedback).exists { mf =>
					if (mf.getMarkerUsercode.getOrElse("") == user.apparentId)
						true
					else
						checkNextMarkerFeedbackForMarker(mf)
				}
			}
			checkNextMarkerFeedbackForMarker(markerFeedback)
		}

		val nextStageRole = requestInfo
			.flatMap(_.requestParameters.get("nextStageRole"))
			.flatMap(_.headOption)

		Mav("coursework/admin/assignments/markerfeedback/marking-complete",
			"assignment" -> assignment,
			"onlineMarking" -> form.onlineMarking,
			"marker" -> form.user,
			"isUserALaterMarker" -> isUserALaterMarker,
			"nextStageRole" -> nextStageRole,
			"isProxying" -> form.isProxying,
			"proxyingAs" -> marker
		).crumbs(
			Breadcrumbs.Standard(s"Marking for ${assignment.name}", Some(Routes.admin.assignment.markerFeedback(assignment, marker)), "")
		)
	}

	@RequestMapping(method = Array(POST), params = Array("confirmScreen"))
	def submit(
		@PathVariable module: Module,
		@PathVariable assignment: Assignment,
		@PathVariable marker: User,
		@Valid @ModelAttribute("markingCompletedCommand") form: MarkingCompletedCommand,
		errors: Errors
	) = {
			if (errors.hasErrors)
				showForm(module,assignment, marker, form, errors)
			else {
				transactional() {
					form.apply()
					RedirectBack(assignment, form)
				}
			}
	}

}

// Redirects users trying to access a marking workflow using the old style URL
@Controller
@RequestMapping(value = Array("/coursework/admin/module/{module}/assignments/{assignment}/marker/marking-completed"))
class MarkingCompletedControllerCurrentUser extends CourseworkController {

	@RequestMapping
	def redirect(@PathVariable assignment: Assignment, currentUser: CurrentUser) = {
		Redirect(Routes.admin.assignment.markerFeedback.complete(assignment, currentUser.apparentUser))
	}

}