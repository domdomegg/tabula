package uk.ac.warwick.tabula.coursework.commands.assignments

import collection.JavaConversions._
import uk.ac.warwick.tabula.commands.{Description, SelfValidating, Command}
import uk.ac.warwick.tabula.data.Daoisms
import reflect.BeanProperty
import uk.ac.warwick.tabula.helpers.ArrayList
import uk.ac.warwick.tabula.data.model._
import uk.ac.warwick.tabula.data.model.MarkingMethod._
import org.springframework.validation.Errors
import uk.ac.warwick.tabula.CurrentUser
import uk.ac.warwick.spring.Wire
import uk.ac.warwick.tabula.services.StateService
import uk.ac.warwick.tabula.permissions.Permissions

class MarkingCompletedCommand(val module: Module, val assignment: Assignment, currentUser: CurrentUser, val firstMarker:Boolean)
	extends Command[Unit] with SelfValidating with Daoisms {

	var stateService = Wire.auto[StateService]

	@BeanProperty var students: JList[String] = ArrayList()
	@BeanProperty var markerFeedbacks: JList[MarkerFeedback] = ArrayList()

	@BeanProperty var noMarks: JList[MarkerFeedback] = ArrayList()
	@BeanProperty var noFeedback: JList[MarkerFeedback] = ArrayList()
	@BeanProperty var releasedFeedback: JList[MarkerFeedback] = ArrayList()

	@BeanProperty var confirm: Boolean = false

	mustBeLinked(assignment, module)
	PermissionCheck(Permissions.Feedback.Create, assignment)


	def onBind() {
		markerFeedbacks = students.flatMap(assignment.getMarkerFeedback(_, currentUser.apparentUser))
	}

	def applyInternal() {
		// do not update previously released feedback
		val feedbackForRelease = markerFeedbacks -- releasedFeedback
		feedbackForRelease.foreach(stateService.updateState(_, MarkingState.MarkingCompleted))

		def finaliseFeedback(){
			val finaliseFeedbackCommand = new FinaliseFeedbackCommand(assignment, feedbackForRelease)
			finaliseFeedbackCommand.apply()
		}

		def createSecondMarkerFeedback(){
			feedbackForRelease.foreach{ mf =>
				val parentFeedback = mf.feedback
				val secondMarkerFeedback = parentFeedback.retrieveSecondMarkerFeedback
				stateService.updateState(secondMarkerFeedback, MarkingState.ReleasedForMarking)
				session.saveOrUpdate(parentFeedback)
			}
		}

		assignment.markingWorkflow.markingMethod match {
			case StudentsChooseMarker => finaliseFeedback()
			case SeenSecondMarking if firstMarker => createSecondMarkerFeedback()
			case SeenSecondMarking if !firstMarker => finaliseFeedback()
			case _ => // do nothing
		}
	}

	override def describe(d: Description){
		d.assignment(assignment)
			.property("students" -> students)
	}

	override def describeResult(d: Description){
		d.assignment(assignment)
			.property("numFeedbackUpdated" -> markerFeedbacks.size())
	}

	def preSubmitValidation() {
		noMarks = markerFeedbacks.filter(!_.hasMark)
		noFeedback = markerFeedbacks.filter(!_.hasFeedback)
		releasedFeedback = markerFeedbacks.filter(_.state == MarkingState.MarkingCompleted)
	}

	def validate(errors: Errors) {
		if (!confirm) errors.rejectValue("confirm", "markers.finishMarking.confirm")
	}

}
