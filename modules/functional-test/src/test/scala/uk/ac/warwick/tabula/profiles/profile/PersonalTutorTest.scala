package uk.ac.warwick.tabula.profiles.profile

import org.scalatest.GivenWhenThen
import uk.ac.warwick.tabula.{BrowserTest, FunctionalTestAcademicYear}
import uk.ac.warwick.tabula.web.FeaturesDriver

class PersonalTutorTest extends BrowserTest with GivenWhenThen with FeaturesDriver with StudentProfileFixture {

	"An admin" should "be able to view personal tutor details" in {

		Given("Admin1 is an admin of the student")
		createStaffMember(P.Admin1.usercode, deptCode = TEST_DEPARTMENT_CODE)

		When("Admin1 views the profile of Student1")
		signIn as P.Admin1 to Path(s"/profiles/view/${P.Student1.warwickId}")
		currentUrl should endWith (s"/profiles/view/${P.Student1.warwickId}")

		Then("They view the Personal tutor page")
		click on linkText("Personal tutor")
		currentUrl should endWith ("/tutor")

		And("There is no personal tutor")
		pageSource should include ("No personal tutor details found for this course and academic year")

		Given("Student1 has a personal tutor")
		createStaffMember(P.Marker1.usercode, deptCode = TEST_DEPARTMENT_CODE)
		createStudentRelationship(P.Student1, P.Marker1, "tutor")

		When("Admin1 views the personal tutors of Student1")
		go to Path(s"/profiles/view/${P.Student1.warwickId}/tutor")

		Then("The personal tutor should be shown")
		pageSource should include ("tabula-functest-marker1")
		pageSource should include ("No meeting records exist for this academic year")

	}

	"A personal tutor" should "be able to create a meeting record" in {

		Given("Marker1 is a personal tutor of the student")
		createStaffMember(P.Marker1.usercode, deptCode = TEST_DEPARTMENT_CODE)
		createStudentRelationship(P.Student1, P.Marker1, "tutor")

		When("Marker1 views the profile of Student1")
		signIn as P.Marker1 to Path(s"/profiles/view/${P.Student1.warwickId}")
		currentUrl should endWith (s"/profiles/view/${P.Student1.warwickId}")

		Then("They view the Personal tutor page")
		click on linkText("Personal tutor")
		currentUrl should endWith ("/tutor")

		And("There is a New record button")
		cssSelector("a.new-meeting-record").findAllElements.size should be (2)

		Then("They create a new record")
		// Modals don't work in HtmlUnit, so screw them
		ifHtmlUnitDriver(h=>h.setJavascriptEnabled(false))

		click on linkText("New record")

		ifHtmlUnitDriver(h=>h.setJavascriptEnabled(true))
		textField("title").value = "Created meeting"
		textField("meetingDateTime").value = s"01-Nov-${FunctionalTestAcademicYear.current.startYear.toString} 11:00:00"
		singleSel("format").value = "f2f"
		cssSelector("button.btn-primary[type=submit]").findElement.get.underlying.click()

		eventually {
			currentUrl should endWith ("/tutor")
		}

		Then("They new record is displayed")
		cssSelector("section.meetings table tbody tr").findAllElements.size should be (1)
		pageSource should include ("Created meeting")

		When("Student1 views their profile")
		signIn as P.Student1 to Path(s"/profiles")
		currentUrl should endWith (s"/profiles/view/${P.Student1.warwickId}")

		And("They view the Personal tutor page")
		click on linkText("Personal tutor")
		currentUrl should endWith ("/tutor")

		Then("They see a meeting requiring approval")
		pageSource should include ("This record needs your approval. Please review, then approve or return it with comments")

		When("They approve the meeting")
		click on radioButton("approved")
		cssSelector("form.approval button.btn-primary").findElement.get.underlying.click()

		Then("The meeting is approved")
		cssSelector("input[name=approved]").findElement.isEmpty
	}

}