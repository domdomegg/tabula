package uk.ac.warwick.tabula.coursework.commands.markerfeedback

import collection.JavaConversions._
import uk.ac.warwick.tabula.AppContextTestBase

import uk.ac.warwick.tabula.data.model._
import uk.ac.warwick.tabula.coursework.commands.assignments.ListMarkerFeedbackCommand


class ListMarkerFeedbackTest extends AppContextTestBase with MarkingWorkflowWorld {

	@Test
	def firstMarkerTest = transactional { tx =>
		withUser("cuslaj") {
			val command =
				new ListMarkerFeedbackCommand(assignment, assignment.module, currentUser, assignment.isFirstMarker(currentUser.apparentUser))
			val markerFeedbackItems = command.apply()

			command.completedFeedback.size should be (0)
			markerFeedbackItems.size should be (3)
		}
		withUser("cuscav") {
			val command =
				new ListMarkerFeedbackCommand(assignment, assignment.module, currentUser, assignment.isFirstMarker(currentUser.apparentUser))
			val markerFeedbackItems = command.apply()

			command.completedFeedback.size should be (0)
			markerFeedbackItems.size should be (2)
		}
	}

	@Test
	def secondMarkerTest = transactional { tx =>
		assignment.feedbacks.foreach{feedback =>
			val smFeedback = feedback.retrieveSecondMarkerFeedback
			smFeedback.state = MarkingState.ReleasedForMarking
		}

		withUser("cuslat") {
			val command =
				new ListMarkerFeedbackCommand(assignment, assignment.module, currentUser, assignment.isFirstMarker(currentUser.apparentUser))
			val markerFeedbackItems = command.apply()

			command.completedFeedback.size should be (0)
			markerFeedbackItems.size should be (3)
		}
		withUser("cuday") {
			val command =
				new ListMarkerFeedbackCommand(assignment, assignment.module, currentUser, assignment.isFirstMarker(currentUser.apparentUser))
			val markerFeedbackItems = command.apply()

			command.completedFeedback.size should be (0)
			markerFeedbackItems.size should be (2)
		}
	}
}


