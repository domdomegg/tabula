package uk.ac.warwick.tabula.groups.notifications

import uk.ac.warwick.tabula.{FreemarkerTestHelpers, TestBase, Mockito}
import uk.ac.warwick.tabula.groups.SmallGroupFixture
import uk.ac.warwick.tabula.JavaImports.{JHashMap}
import org.mockito.Mockito._
import org.mockito.Matchers._
import uk.ac.warwick.tabula.web.views.FreemarkerRendering
import uk.ac.warwick.tabula.data.model.notifications.ReleaseSmallGroupSetsNotification


class ReleaseSmallGroupNotificationTemplateTest extends TestBase with FreemarkerTestHelpers with FreemarkerRendering{

  private trait NotificationFixture extends SmallGroupFixture {

    val weekRangeFormatter = new StubFreemarkerMethodModel
    val urlModel = new StubFreemarkerDirectiveModel
    val timeBuilder= new StubFreemarkerMethodModel

    implicit val config = newFreemarkerConfiguration(JHashMap(
      "url" -> urlModel,
      "weekRangesFormatter" -> weekRangeFormatter,
      "timeBuilder"->timeBuilder))
  }

  @Test
  def includesTheNameOfEachGroup{
    new NotificationFixture {
      val output =
        renderToString(ReleaseSmallGroupSetsNotification.templateLocation,
          Map("user" -> recipient, "groups" -> List(group1, group2), "profileUrl" -> "profileUrl"))
    output should include(group1.name)
    output should include(group2.name)
  }}

  @Test
  def includesTheCountOfStudents{new NotificationFixture {
    val output =
      renderToString(ReleaseSmallGroupSetsNotification.templateLocation,
        Map("user" -> recipient, "groups" -> List(group1), "profileUrl" -> "profileUrl"))
      output should include("2 students")
  }}

  @Test
  def callsWeekRangeFormatterOncePerEvent() {
    new NotificationFixture {
      val output =
        renderToString(ReleaseSmallGroupSetsNotification.templateLocation,
          Map("user" -> recipient, "groups" -> List(group1, group2), "profileUrl" -> "profileUrl"))
      verify(weekRangeFormatter.mock, times(2)).exec(anyList())
  }}

  @Test
  def formatsTimeNicely(){
    new NotificationFixture {
      val output =
        renderToString(ReleaseSmallGroupSetsNotification.templateLocation,
          Map("user" -> recipient, "groups" -> List(group1, group2), "profileUrl" -> "profileUrl"))
     verify(timeBuilder.mock, times(2)).exec(anyList())
  }}
}
