package uk.ac.warwick.tabula.commands.coursework.markerfeedback

import uk.ac.warwick.tabula.commands.UserAware
import uk.ac.warwick.tabula.commands.coursework.ReleasedState
import uk.ac.warwick.tabula.commands.coursework.assignments.{FirstMarkerReleaseNotifier, ReleaseForMarkingState}
import uk.ac.warwick.tabula.data.model._
import uk.ac.warwick.tabula.data.model.notifications.coursework.ReleaseToMarkerNotification
import uk.ac.warwick.tabula.helpers.Logging
import uk.ac.warwick.tabula.services.UserLookupComponent
import uk.ac.warwick.tabula.{Mockito, TestBase}

import scala.collection.JavaConversions._


class ReleaseForMarkingNotifiesTest extends TestBase with Mockito {

	@Test
	def notifiesEachAffectedUser() { new MarkingNotificationFixture {

		testAssignment.markingWorkflow = new ModeratedMarkingWorkflow {
			userLookup = mockUserLookup
			firstMarkers = userGroup("marker1", "marker2")
			secondMarkers = userGroup("marker3")
		}

		val m1UserGroup = userGroup("student1", "student2")
		val m2UserGroup = userGroup("student3", "student4")

		testAssignment.firstMarkers = Seq(
			FirstMarkersMap(testAssignment, "marker1", m1UserGroup),
			FirstMarkersMap(testAssignment, "marker2", m2UserGroup)
		)

		val (f1, mf1) = makeMarkerFeedback(student1)(MarkingNotificationFixture.FirstMarkerLink)
		val (f2, mf2) = makeMarkerFeedback(student2)(MarkingNotificationFixture.FirstMarkerLink)
		val (f3, mf3) = makeMarkerFeedback(student3)(MarkingNotificationFixture.FirstMarkerLink)
		val (f4, mf4) = makeMarkerFeedback(student4)(MarkingNotificationFixture.FirstMarkerLink)

		val notifier = new FirstMarkerReleaseNotifier with ReleaseForMarkingState with ReleasedState with UserAware
			with UserLookupComponent with Logging {
			val user = marker1
			val assignment = testAssignment
			val module = new Module
			newReleasedFeedback = List(mf1, mf2, mf3, mf4)
			var userLookup = mockUserLookup
		}

		val notifications  = notifier.emit(List())

		notifications.foreach {
			case n: ReleaseToMarkerNotification => n.userLookup = mockUserLookup
		}

		notifications.size should be(2)
		notifications(0).recipients should contain(marker2)
		notifications(0).entities should contain(mf3)
		notifications(0).entities should contain(mf4)
		notifications(1).recipients should contain(marker1)
		notifications(1).entities should contain(mf1)
		notifications(1).entities should contain(mf2)

	}}

}