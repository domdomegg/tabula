package uk.ac.warwick.tabula.coursework.commands.markerfeedback

import collection.JavaConversions._
import uk.ac.warwick.tabula.coursework.commands.assignments.MarkingCompletedCommand
import uk.ac.warwick.tabula._
import uk.ac.warwick.tabula.data.model._
import uk.ac.warwick.tabula.services._
import uk.ac.warwick.spring.Wire



class MarkingCompletedTest extends AppContextTestBase with MarkingWorkflowWorld {

	lazy val stateService = Wire[StateService]

	@Test
	def firstMarkerFinished = 
		withUser("cuslaj"){
			val isFirstMarker = assignment.isFirstMarker(currentUser.apparentUser)
			val command = new MarkingCompletedCommand(assignment.module, assignment, currentUser, isFirstMarker)
			command.stateService = stateService
			command.students = List("9876004", "0270954", "9170726")
			command.onBind()

			command.preSubmitValidation()
			command.noFeedback.size should be (3)
			command.noMarks.size should be (3)

			transactional { tx =>
				command.apply()
			}
			
			val releasedFeedback = assignment.feedbacks.map(_.firstMarkerFeedback).filter(_.state == MarkingState.MarkingCompleted)
			releasedFeedback.size should be (3)
		}
	

	@Test
	def secondMarkerFinished = transactional { tx =>
		withUser("cuday"){
			val isFirstMarker = assignment.isFirstMarker(currentUser.apparentUser)
			val command = new MarkingCompletedCommand(assignment.module, assignment, currentUser, isFirstMarker)
			command.stateService = stateService
			command.students = List("0672088", "0672089")
			command.onBind()

			command.preSubmitValidation()
			command.noFeedback.size should be (2)
			command.noMarks.size should be (2)

			command.apply()
			val secondFeedback = assignment.feedbacks.flatMap(f => Option(f.secondMarkerFeedback))
			val releasedFeedback = secondFeedback.filter(_.state == MarkingState.MarkingCompleted)
			releasedFeedback.size should be (2)
		}
	}

}
