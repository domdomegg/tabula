package uk.ac.warwick.tabula.coursework.commands.markerfeedback

import collection.JavaConversions._
import uk.ac.warwick.tabula.coursework.commands.assignments.{MarkingCompletedState, SecondMarkerReleaseNotifier, MarkingCompletedCommand}
import uk.ac.warwick.tabula._
import uk.ac.warwick.tabula.data.model._
import uk.ac.warwick.tabula.services._
import org.hibernate.Session
import uk.ac.warwick.tabula.data.SessionComponent
import uk.ac.warwick.spring.Wire
import org.junit.{Before, After}
import uk.ac.warwick.tabula.events.EventListener
import uk.ac.warwick.tabula.commands.UserAware
import uk.ac.warwick.tabula.coursework.commands.markingworkflows.notifications.ReleasedState
import uk.ac.warwick.tabula.helpers.Logging
import uk.ac.warwick.tabula.Mockito
import uk.ac.warwick.tabula.data.model.notifications.ReleaseToMarkerNotification

/*
 * Fixed this test by replacing the full appcontext with a minimal functional one as below.
 * Not completely convinced this is the best solution, but as the command creates subcommands
 * and calls their apply() it seems necessary to make the services it looks for available. So
 * this may be the best we can do without refactoring Command some more.
 *
 * MinimalCommandDependencies could be pulled out and reused in other tests, though as I say,
 *
 */

object MarkingCompletedTest {
	class Ctx extends FunctionalContext with Mockito with MinimalCommandDependencies

	trait MinimalCommandDependencies extends FunctionalContext with Mockito {
		delayedInit {
			singleton() { mock[MaintenanceModeService] }
			singleton() { mock[EventListener] }
		}
	}
}

class MarkingCompletedTest extends TestBase with MarkingWorkflowWorld with Mockito with FunctionalContextTesting {

	val mockSession = mock[Session]
	var stateService: StateService = new ComposableStateServiceImpl with SessionComponent {
		def session = mockSession
	}

	@Before
	def before() {
		Wire.ignoreMissingBeans = true
	}

	@After
	def after() {
		Wire.ignoreMissingBeans = false
	}

	@Test
	def firstMarkerFinished() {
		withUser("cuslaj") {

			val isFirstMarker = assignment.isFirstMarker(currentUser.apparentUser)
			val command = MarkingCompletedCommand(assignment.module, assignment, currentUser.apparentUser, isFirstMarker)
			command.stateService = stateService
			command.students = List("9876004", "0270954", "9170726")
			command.onBind()

			command.preSubmitValidation()
			command.noFeedback.size should be (3)
			command.noMarks.size should be (3)

			command.applyInternal()
			val releasedFeedback = assignment.feedbacks.map(_.firstMarkerFeedback).filter(_.state == MarkingState.MarkingCompleted)
			releasedFeedback.size should be (3)
		}
	}



	@Test
	def secondMarkerFinished(){
		inContext[MarkingCompletedTest.Ctx] {
		withUser("cuday"){

			val isFirstMarker = assignment.isFirstMarker(currentUser.apparentUser)
			val command = MarkingCompletedCommand(assignment.module, assignment, currentUser.apparentUser, isFirstMarker)
			command.stateService = stateService
			command.students = List("0672088", "0672089")
			command.onBind()

			command.preSubmitValidation()
			command.noFeedback.size should be (2)
			command.noMarks.size should be (2)
			setFirstMarkerFeedbackState(MarkingState.AwaitingSecondMarking)

			command.applyInternal()

			val secondFeedback = assignment.feedbacks.flatMap(f => Option(f.secondMarkerFeedback))
			val completedSecondMarking = secondFeedback.filter(_.state == MarkingState.MarkingCompleted)
			completedSecondMarking.size should be (2)

			val firstFeedback = assignment.feedbacks.flatMap(f => Option(f.firstMarkerFeedback))
			val completedFirstMarking = firstFeedback.filter(_.state == MarkingState.SecondMarkingComplete)
			completedFirstMarking.size should be (2)

			val finalFeedback = assignment.feedbacks.flatMap(f => Option(f.finalMarkerFeedback))
			finalFeedback.size should be (2)
		}
		}
	}


	@Test
	def finalMarkingComplete() {
		withUser("cuslaj") {

			val isFirstMarker = assignment.isFirstMarker(currentUser.apparentUser)
			val command = MarkingCompletedCommand(assignment.module, assignment, currentUser.apparentUser, isFirstMarker)

			assignment.feedbacks.map(addMarkerFeedback(_,FinalFeedback))
			setFinalMarkerFeedbackState(MarkingState.InProgress)
			setFirstMarkerFeedbackState(MarkingState.SecondMarkingComplete)

			command.stateService = stateService
			command.students = List("9876004", "0270954", "9170726")
			command.onBind()

			command.preSubmitValidation()
			command.applyInternal()
			command.noFeedback.size should be (3)
			command.noMarks.size should be (3)

			command.applyInternal()
			val releasedFeedback = assignment.feedbacks.map(_.finalMarkerFeedback).filter(_.state == MarkingState.MarkingCompleted)
			releasedFeedback.size should be (3)
		}


	}


	@Test
	def notifiesEachAffectedUser() { new MarkingNotificationFixture {

		testAssignment.markingWorkflow = new ModeratedMarkingWorkflow {
			userLookup = mockUserLookup
			firstMarkers = userGroup("marker1")
			secondMarkers = userGroup("marker2", "marker3")
		}

		val m2UserGroup = userGroup("student1", "student2")
		val m3UserGroup = userGroup("student3", "student4")

		testAssignment.markerMap = Map(
			"marker2" -> m2UserGroup,
			"marker3" -> m3UserGroup
		)

		val (f1, mf1) = makeMarkerFeedback(student1)(MarkingNotificationFixture.SecondMarkerLink)
		val (f2, mf2) = makeMarkerFeedback(student2)(MarkingNotificationFixture.SecondMarkerLink)
		val (f3, mf3) = makeMarkerFeedback(student3)(MarkingNotificationFixture.SecondMarkerLink)
		val (f4, mf4) = makeMarkerFeedback(student4)(MarkingNotificationFixture.SecondMarkerLink)

		val notifier = new SecondMarkerReleaseNotifier with MarkingCompletedState with ReleasedState with UserAware with UserLookupComponent with Logging {
			val user = marker1
			val assignment = testAssignment
			val module = new Module
			newReleasedFeedback = List(mf1, mf2, mf3, mf4)
			var userLookup = mockUserLookup
		}

		val notifications = notifier.emit()
		notifications.foreach {
			case n:ReleaseToMarkerNotification => n.userLookup = mockUserLookup
		}

		notifications.size should be(2)
		notifications(0).recipients should contain(marker3)
		notifications(0).entities should contain(mf3)
		notifications(0).entities should contain(mf4)
		notifications(1).recipients should contain(marker2)
		notifications(1).entities should contain(mf1)
		notifications(1).entities should contain(mf2)

	}}



}
