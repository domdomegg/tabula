package uk.ac.warwick.tabula.coursework.commands.assignments

import collection.JavaConversions._
import uk.ac.warwick.tabula.JavaImports._
import uk.ac.warwick.tabula.data.model.{Feedback, MarkerFeedback, Assignment}
import uk.ac.warwick.tabula.commands.{Description, Command}
import uk.ac.warwick.tabula.data.{FileDao, Daoisms}
import uk.ac.warwick.tabula.actions.UploadMarkerFeedback
import uk.ac.warwick.spring.Wire

/**
 * Copies the appropriate MarkerFeedback item to it's parent Feedback ready for processing by administrators
 */
class FinaliseFeedbackCommand(val assignment: Assignment, val markerFeedbacks:JList[MarkerFeedback])
	extends Command[Unit] with Daoisms {

	var fileDao = Wire.auto[FileDao]

	PermissionsCheck(UploadMarkerFeedback(assignment))

	def applyInternal() {
		markerFeedbacks.foreach { markerFeedback =>
			val feedback = copyToFeedback(markerFeedback)
			session.saveOrUpdate(feedback)
		}
	}

	override def describe(d: Description){
		d.assignment(assignment)
		d.property("updatedFeedback" -> markerFeedbacks.size)
	}

	def copyToFeedback(markerFeedback: MarkerFeedback): Feedback = {
		val parent = markerFeedback.feedback
		parent.actualGrade = markerFeedback.grade
		parent.actualMark = markerFeedback.mark
		markerFeedback.attachments.foreach(parent.addAttachment(_))
		parent
	}
}
