package uk.ac.warwick.tabula.data.model.notifications.coursework

import uk.ac.warwick.tabula.data.model.{Feedback, Notification}
import uk.ac.warwick.tabula.web.views.FreemarkerRendering
import uk.ac.warwick.tabula.{Fixtures, TestBase, Mockito}
import uk.ac.warwick.tabula.helpers.Tap._

class FinaliseFeedbackNotificationTest extends TestBase with Mockito with FreemarkerRendering {

	private trait Fixture {
		val freeMarkerConfig = newFreemarkerConfiguration

		val assignment = Fixtures.assignment("5,000 word essay")
		assignment.module = Fixtures.module("cs118", "Programming for Computer Scientists")
	}

	private trait SingleItemFixture extends Fixture {
		val feedback = Fixtures.assignmentFeedback("0000001").tap { _.assignment = assignment }

		val notification = Notification.init(new FinaliseFeedbackNotification, currentUser.apparentUser, feedback.asInstanceOf[Feedback], assignment)
	}

	private trait MultipleItemsFixture extends Fixture {
		val feedbacks: Seq[Feedback] =
			Seq("0000001", "0000002", "0000003").map { uniId => Fixtures.assignmentFeedback(uniId).tap { _.assignment = assignment } }

		val notification = Notification.init(new FinaliseFeedbackNotification, currentUser.apparentUser, feedbacks, assignment)
	}

	@Test def titleSingle = withUser("cuscav", "0672089") {
		new SingleItemFixture {
			notification.title should be ("CS118: 1 submission for \"5,000 word essay\" has been marked")
		}
	}

	@Test def titleMultiple = withUser("cuscav", "0672089") {
		new MultipleItemsFixture {
			notification.title should be ("CS118: 3 submissions for \"5,000 word essay\" have been marked")
		}
	}

	@Test def outputSingle = withUser("cuscav", "0672089") {
		new SingleItemFixture {
			val notificationContent = renderToString(freeMarkerConfig.getTemplate(notification.content.template), notification.content.model)
			notificationContent should be (
				"""1 submission for CS118 5,000 word essay has been marked and is ready to be released to students:
					|
					|* 0000001""".stripMargin
			)
		}
	}

	@Test def outputMultiple = withUser("cuscav", "0672089") {
		new MultipleItemsFixture {
			val notificationContent = renderToString(freeMarkerConfig.getTemplate(notification.content.template), notification.content.model)
			notificationContent should be (
				"""3 submissions for CS118 5,000 word essay have been marked and are ready to be released to students:
					|
					|* 0000001
					|* 0000002
					|* 0000003""".stripMargin
			)
		}
	}

}
