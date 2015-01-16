package uk.ac.warwick.tabula.coursework.commands.feedback

import collection.JavaConverters._
import uk.ac.warwick.tabula.helpers.StringUtils._
import uk.ac.warwick.tabula.data.model.{Submission, Assignment, Module}
import uk.ac.warwick.tabula.system.BindListener
import uk.ac.warwick.tabula.commands.SelfValidating
import org.springframework.validation.{Errors, BindingResult}
import uk.ac.warwick.userlookup.User

abstract class AbstractOnlineFeedbackFormCommand(val module: Module, val assignment: Assignment, val student: User, val marker: User)
	extends OnlineFeedbackState with OnlineFeedbackStudentState with SubmissionState with BindListener with SelfValidating {

	def submission = assignment.findSubmission(student.getWarwickId)

	override def onBind(result:BindingResult) {
		if (fields != null) {
			for ((key, field) <- fields.asScala) {
				field.onBind(result)
			}
		}
		file.onBind(result)
	}

	override def validate(errors: Errors) {
		if(!hasContent) {
			errors.reject("feedback.empty")
		}

		fieldValidation(errors)
	}

	def fieldValidation(errors:Errors) {
		// Individually validate all the custom fields
		if(fields != null){
			assignment.feedbackFields.foreach { field =>
				errors.pushNestedPath("fields[%s]".format(field.id))
				fields.asScala.get(field.id).map { field.validate(_, errors) }
				errors.popNestedPath()
			}
		}

		if (mark.hasText) {
			try {
				val asInt = mark.toInt
				if (asInt < 0 || asInt > 100) {
					errors.rejectValue("mark", "actualMark.range")
				}
			} catch {
				case _ @ (_: NumberFormatException | _: IllegalArgumentException) =>
					errors.rejectValue("mark", "actualMark.format")
			}
		}
	}
}

trait SubmissionState {
	def assignment: Assignment
	def submission: Option[Submission]
	def student: User

	def submissionState = {
		submission match {
			case Some(s) if s.isAuthorisedLate => "workflow.Submission.authorisedLate"
			case Some(s) if s.isLate => "workflow.Submission.late"
			case Some(_) => "workflow.Submission.onTime"
			case None if !assignment.isClosed => "workflow.Submission.unsubmitted.withinDeadline"
			case None if assignment.extensions.asScala.exists(e => e.universityId == student.getWarwickId && e.expiryDate.isBeforeNow)
				=> "workflow.Submission.unsubmitted.withinExtension"
			case None => "workflow.Submission.unsubmitted.late"
		}
	}
}