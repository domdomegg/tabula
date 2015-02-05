package uk.ac.warwick.tabula.coursework.commands.assignments

import org.joda.time.DateTime
import uk.ac.warwick.spring.Wire
import uk.ac.warwick.tabula.JavaImports._
import uk.ac.warwick.tabula.commands.{Command, Description}
import uk.ac.warwick.tabula.coursework.commands.feedback.GeneratesGradesFromMarks
import uk.ac.warwick.tabula.data.FileDao
import uk.ac.warwick.tabula.data.model.forms.SavedFormValue
import uk.ac.warwick.tabula.data.model.{Assignment, Feedback, MarkerFeedback}
import uk.ac.warwick.tabula.permissions.Permissions

import scala.collection.JavaConversions._

/**
 * Copies the appropriate MarkerFeedback item to its parent Feedback ready for processing by administrators
 */
class FinaliseFeedbackCommand(val assignment: Assignment, val markerFeedbacks:JList[MarkerFeedback], gradeGenerator: GeneratesGradesFromMarks)
	extends Command[Unit] {

	var fileDao = Wire.auto[FileDao]

	PermissionCheck(Permissions.Feedback.Create, assignment)

	def applyInternal() {
		markerFeedbacks.foreach { markerFeedback =>
			this.copyToFeedback(markerFeedback)
		}
	}

	override def describe(d: Description){
		d.assignment(assignment)
		d.property("updatedFeedback" -> markerFeedbacks.size)
	}

	def copyToFeedback(markerFeedback: MarkerFeedback): Feedback = {
		val parent = markerFeedback.feedback

		parent.clearCustomFormValues()

		// save custom fields
		parent.customFormValues.addAll(markerFeedback.customFormValues.map { formValue =>
			val newValue = new SavedFormValue()
			newValue.name = formValue.name
			newValue.feedback = formValue.markerFeedback.feedback
			newValue.value = formValue.value
			newValue
		}.toSet[SavedFormValue])


		parent.actualGrade = {
			if (assignment.module.adminDepartment.assignmentGradeValidation) {
				markerFeedback.mark.flatMap(mark => gradeGenerator.applyForMarks(Map(parent.universityId -> mark)).get(parent.universityId).flatten)
			} else {
				markerFeedback.grade
			}
		}
		parent.actualMark = markerFeedback.mark

		parent.updatedDate = DateTime.now

		// erase any existing attachments - these will be replaced
		parent.clearAttachments()

		markerFeedback.attachments.foreach(parent.addAttachment)
		parent
	}
}
