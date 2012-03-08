package uk.ac.warwick.courses.web.controllers

import scala.collection.JavaConversions._
import org.springframework.beans.factory.annotation.Configurable
import org.springframework.stereotype.Controller
import org.springframework.transaction.annotation.Transactional
import org.springframework.validation.Errors
import org.springframework.web.bind.annotation._
import org.springframework.web.bind.annotation.RequestMethod._
import javax.validation.Valid
import uk.ac.warwick.courses.actions.View
import uk.ac.warwick.courses.commands.assignments.SendSubmissionReceiptCommand
import uk.ac.warwick.courses.commands.assignments.SubmitAssignmentCommand
import uk.ac.warwick.courses.commands.assignments.SubmittedFieldsPropertyEditor
import uk.ac.warwick.courses.data.model.Assignment
import uk.ac.warwick.courses.data.model.Module
import uk.ac.warwick.courses.data.FeedbackDao
import uk.ac.warwick.courses.helpers.DateTimeOrdering.orderedDateTime
import uk.ac.warwick.courses.services.AssignmentService
import uk.ac.warwick.courses.web.Routes
import uk.ac.warwick.courses.CurrentUser
import uk.ac.warwick.courses.ItemNotFoundException
import uk.ac.warwick.courses.web.Mav

@Controller
@RequestMapping(Array("/module/{module}/"))
class ModuleController extends BaseController {
  
	@RequestMapping
	def viewModule(@PathVariable module:Module) = {
		mustBeAbleTo(View(mandatory(module)))
		Mav("submit/module", 
			"module"-> module,
			"assignments" -> module.assignments.sortBy{ _.closeDate }.reverse)
	}
	
}

@Configurable
@Controller
@RequestMapping(value=Array("/module/{module}/{assignment}/resend-receipt"), method=Array(POST))
class ResendSubmissionEmail extends AbstractAssignmentController {
	
	@RequestMapping def sendEmail(user:CurrentUser, form:SendSubmissionReceiptCommand) : Mav = {
		form.user = user
		mustBeLinked(mandatory(form.assignment),  mandatory(form.module))
		
		val submission = assignmentService.getSubmission(form.assignment, user.apparentId)
		val sent = submission match {
			case Some(submission) =>
				form.submission = submission
				form.apply()
				true
			case None => false
		}
		Mav("submit/receipt", 
				"submission" -> submission,
				"module"-> form.module,
				"assignment" -> form.assignment,
				"sent" -> sent
		)
		
	}
	
}

@Configurable
@Controller
@RequestMapping(Array("/module/{module}/{assignment}"))
class AssignmentController extends AbstractAssignmentController {
	
	hideDeletedItems
	
	validatesWith{ (cmd:SubmitAssignmentCommand,errors) => cmd.validate(errors) }
	
	@ModelAttribute def form(@PathVariable("module") module:Module, @PathVariable("assignment") assignment:Assignment, user:CurrentUser) = {  
		val cmd = new SubmitAssignmentCommand(assignment, user)
		cmd.module = module
		cmd
	}
	
	
	def checks(form:SubmitAssignmentCommand) = {
		mustBeLinked(mandatory(form.assignment),  mandatory(form.module))
	}
	
	@RequestMapping(method=Array(GET))
	def view(user:CurrentUser, form:SubmitAssignmentCommand, errors:Errors) = {
		val assignment = form.assignment
		val module = form.module
		form.onBind
		checks(form)
		
		val feedback = checkCanGetFeedback(assignment, user)
		val submission = assignmentService.getSubmission(assignment, user.apparentId)
		/*
		 * Submission values are an unordered set without any proper name, so
		 * match them up into an ordered sequence of pairs.
		 * 
		 * If a submission value is missing, the right hand is None.
		 * If any submission value doesn't match the assignment fields, it just isn't shown.
		 */
		val submissionValues = submission.map{ submission =>
			val values = submission.values
			assignment.fields.map { field =>
				(field, values.find(_.name == field.name))
			}
		}.getOrElse(Seq.empty)
		
		if (user.loggedIn) {
			Mav("submit/assignment",
				"module"-> module,
				"assignment" -> assignment,
				"feedback" -> feedback,
				"submission" -> submission
			)
		} else {
			RedirectToSignin() 
		}
	}
	
	@Transactional
	@RequestMapping(method=Array(POST))
	def submit(@PathVariable module:Module, user:CurrentUser, @Valid form:SubmitAssignmentCommand, errors:Errors) = {
		val assignment = form.assignment
		val module = form.module
		form.onBind
		checks(form)
		if (errors.hasErrors) {
			view(user,form,errors)
		} else {
			val submission = form.apply
			val sendReceipt = new SendSubmissionReceiptCommand(submission, user)
			sendReceipt.apply()
			Redirect(Routes.assignment(form.assignment))
		}
	}
			
}