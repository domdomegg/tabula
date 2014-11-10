package uk.ac.warwick.tabula.groups.notifications

import uk.ac.warwick.tabula.data.model.notifications.groups.OpenSmallGroupSetsNotification
import uk.ac.warwick.tabula.{FreemarkerTestHelpers, TestBase}
import uk.ac.warwick.tabula.web.views.FreemarkerRendering
import uk.ac.warwick.tabula.groups.SmallGroupFixture
import uk.ac.warwick.tabula.JavaImports.JHashMap
import org.mockito.Mockito._
import org.mockito.Matchers._

class OpenGroupSetNotificationTemplateTest extends TestBase with FreemarkerTestHelpers with FreemarkerRendering {

	private trait NotificationFixture extends SmallGroupFixture {

		val urlModel = new StubFreemarkerDirectiveModel

		implicit val config = newFreemarkerConfiguration
	}

	@Test
	def includesTheNameOfEachGroupSet {
		new NotificationFixture {
			val output =
				renderToString(OpenSmallGroupSetsNotification.templateLocation,
					Map("formatsString" -> "great", "groupsets" -> List(groupSet1, groupSet2), "profileUrl" -> "profileUrl"))
			output should include(groupSet1.name)
			output should include(groupSet2.name)
		}}


}
