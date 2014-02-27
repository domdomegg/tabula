package uk.ac.warwick.tabula.coursework.commands.departments

import java.util.Date
import scala.collection.JavaConverters._
import org.apache.poi.ss.usermodel.Cell
import org.apache.poi.xssf.usermodel.XSSFRow
import org.joda.time.base.AbstractInstant
import uk.ac.warwick.tabula.TestBase
import uk.ac.warwick.tabula.coursework.services.feedbackreport.FeedbackReport
import uk.ac.warwick.userlookup.User

// scalastyle:off
class FeedbackReportTest extends TestBase with ReportWorld {
	import FeedbackReport._

	@Test
	def simpleGetSubmissionTest() {
		val userOne = new User(idFormat(1))
		userOne.setWarwickId(idFormat(1))
		val submissions = auditEventQueryMethods.submissionForStudent(assignmentOne, userOne)
		submissions.size should be (1)
	}

	@Test
	def simpleGetFeedbackTest() {
		val userOne = new User(idFormat(1))
		userOne.setWarwickId(idFormat(1))
		val publishes = auditEventQueryMethods.publishFeedbackForStudent(assignmentOne, userOne)
		publishes.size should be (1)
	}

	@Test
	def feedbackCountsTest() {
		val report = getTestFeedbackReport

		report.getFeedbackCount(assignmentOne) should be (FeedbackCount(10,0, dateTime(2013, 3, 25), dateTime(2013, 3, 25))) // 10 on time
		report.getFeedbackCount(assignmentTwo) should be (FeedbackCount(0,29, dateTime(2013, 5, 15), dateTime(2013, 5, 15))) // 29 late
		report.getFeedbackCount(assignmentThree) should be (FeedbackCount(4,9, dateTime(2013, 5, 20), dateTime(2013, 6, 14))) // 4 on time - 9 late
		report.getFeedbackCount(assignmentFour) should be (FeedbackCount(7,28, dateTime(2013, 7, 1), dateTime(2013, 7, 1))) // 7 on time - 28 late
		report.getFeedbackCount(assignmentFive) should be (FeedbackCount(2,98, dateTime(2013, 9, 24), dateTime(2013, 9, 24))) // 2 on time - 98 late
		report.getFeedbackCount(assignmentSix) should be (FeedbackCount(65,8, dateTime(2013, 7, 16), dateTime(2013, 8, 1))) // 65 on time - 8 late
	}


	/**
	 * Checks that the dissertation feedback is counted as on time
	 *
	 **/

	@Test
	def feedbackCountsDissertationTest() {
		val report = getTestFeedbackReport

		// assignmentSeven is a dissertation, assignmentEight isn't
		report.getFeedbackCount(assignmentSeven) should be (FeedbackCount(50,0, dateTime(2013, 8, 1), dateTime(2013, 8, 1))) // 50 on time
		report.getFeedbackCount(assignmentEight) should be (FeedbackCount(0,50, dateTime(2013, 8, 1), dateTime(2013, 8, 1))) // is not a dissertation, 50 late
	}

	/**
	 * Checks that feedback published on deadline day is marked as on-time
	 *
	 **/

	@Test
	def deadlineDayTest() {

		// For close date of 28/3/2013
		// if 20 working days allowed for feedback
		// 32 days later = deadline day, which should be marked as on-time

		val report = getTestFeedbackReport
		val assignmentEight = addAssignment("1007", "test deadline day - 1", dateTime(2013, 3, 28), 10, 0, moduleOne)
		createPublishEvent(assignmentEight, 31, studentData(0, 10))	// on-time
		var feedbackCount = report.getFeedbackCount(assignmentEight)
		feedbackCount should be (FeedbackCount(10, 0, dateTime(2013, 4, 28), dateTime(2013, 4, 28))) // 10 on time

		val assignmentNine = addAssignment("1008", "test deadline day", dateTime(2013, 3, 28), 10, 0, moduleOne)
		createPublishEvent(assignmentNine, 32, studentData(0, 10))	// on time
		feedbackCount = report.getFeedbackCount(assignmentNine)
		feedbackCount should be (FeedbackCount(10, 0, dateTime(2013, 4, 29), dateTime(2013, 4, 29))) // 10 on time

		val assignmentTen = addAssignment("1009", "test deadline day + 1", dateTime(2013, 3, 28), 10, 0, moduleOne)
		createPublishEvent(assignmentTen, 33, studentData(0, 10))	// late
		feedbackCount = report.getFeedbackCount(assignmentTen)
		feedbackCount should be (FeedbackCount(0, 10, dateTime(2013, 4, 30), dateTime(2013, 4, 30))) // 10 late

	}

	@Test
	def deadlineDayTest2() {
		val report = getTestFeedbackReport
		val assignmentEleven = addAssignment("1010", "test deadline day - 1", dateTime(2013, 5, 29), 10, 0, moduleOne)
		createPublishEvent(assignmentEleven, 27, studentData(0, 10))	// on time
		var feedbackCount = report.getFeedbackCount(assignmentEleven)
		feedbackCount should be (FeedbackCount(10, 0, dateTime(2013, 6, 25), dateTime(2013, 6, 25))) // 10 on time

		val assignmentTwelve = addAssignment("1010", "test deadline day", dateTime(2013, 5, 29), 10, 0, moduleOne)
		createPublishEvent(assignmentTwelve, 28, studentData(0, 10))	// on time
		feedbackCount = report.getFeedbackCount(assignmentTwelve)
		feedbackCount should be (FeedbackCount(10, 0, dateTime(2013, 6, 26), dateTime(2013, 6, 26))) // 10 on time

		val assignmentThirteen = addAssignment("1011", "test deadline day + 1", dateTime(2013, 5, 29), 10, 0, moduleOne)
		createPublishEvent(assignmentThirteen, 29, studentData(0, 10))	// late
		feedbackCount = report.getFeedbackCount(assignmentThirteen)
		feedbackCount should be (FeedbackCount(0, 10, dateTime(2013, 6, 27), dateTime(2013, 6, 27))) // 10 late
	}


	// Move this up and out if it'd be useful in other tests.
	object SpreadsheetTester {
		/** Assert that this cell is the expected value. */
		def compare(cell: Cell, expected: Any) = expected match {
			case string: String => cell.getStringCellValue should be (string)
			case dt: AbstractInstant => cell.getDateCellValue should be (dt.toDate)
			case date: Date => cell.getDateCellValue should be (date)
			case number: Number => cell.getNumericCellValue should be (number)
			case x => fail("This value type is not handled by compare(): " + x)
		}

		/** Check each cell in the row against the corresponding item in expected. */
		def check(description:String, row:XSSFRow, expected: Seq[Any]) {
			for ((cell, (expectedValue, i)) <- row.cellIterator().asScala.toSeq zip expected.zipWithIndex) {
				withClue(s"$description column index $i:") {
					compare(cell, expectedValue)
				}
			}
		}
	}

	@Test
	def sheetTest() {
		import SpreadsheetTester._

		val report = getTestFeedbackReport
		report.buildAssignmentData()

		val assignmentSheet = report.generateAssignmentSheet(department)
		report.populateAssignmentSheet(assignmentSheet)

		check("Row 1",
			assignmentSheet.getRow(1),
			Seq("test one", "IN101", dateTime(2013, 3, 10), dateTime(2013, 4, 9), "Summative", "", 10, 10, 0, 2, 0, 10, 10, 1, 0, 0))

		check("Row 2",
			assignmentSheet.getRow(2),
			Seq("test two", "IN101", dateTime(2013, 4, 10), dateTime(2013, 5, 9), "Summative", "", 29, 29, 0, 5, 0, 29, 0, 0, 29, 1))

		check("Row 3",
			assignmentSheet.getRow(3),
			Seq("test three", "IN101", dateTime(2013, 5, 10), dateTime(2013, 6, 10), "Formative", "",  13, 13, 0, 2, 0, 13, 4, 0.307692307692307692, 9, 0.6923076923076923))

		check("Row 4",
			assignmentSheet.getRow(4),
			Seq("test four","IN102",dateTime(2013, 5, 31),dateTime(2013, 6, 28),"Summative", "", 35,35,0,7,0,35,7,0.2,28,0.8))

		check("Row 5",
			assignmentSheet.getRow(8),
			Seq("test five","IN102",dateTime(2013, 8, 23),dateTime(2013, 9, 23),"Summative", "", 100,100,0,2,0,100,2,0.02,98,0.98))

		check("Row 6",
			assignmentSheet.getRow(5),
			Seq("test six","IN102",dateTime(2013, 7, 1),dateTime(2013, 7, 29),"Summative", "", 73,73,24,0,0,73,65,0.890410958904109589,8,0.109589041095890410))

		check("Row 7",
			assignmentSheet.getRow(6),
			Seq("test seven","IN102", dateTime(2013, 7, 1), dateTime(2013, 7, 29), "Summative", "Dissertation", 100, 100, 0, 2, 50, 50, 50, 1, 0, 0))

		check("Row 8",
			assignmentSheet.getRow(7),
			Seq("test eight","IN102", dateTime(2013, 7, 1), dateTime(2013, 7, 29), "Summative", "", 100, 100, 0, 2, 50, 50, 0, 0, 50, 1)
		)


		val moduleSheet = report.generateModuleSheet(department)
		report.populateModuleSheet(moduleSheet)

		check("Module row 1",
			moduleSheet.getRow(1),
			Seq("Module One","IN101",3,52,52,0,9,0,52,14,0.269230769230769230,38,0.730769230769230769))

		check("Module row 2",
			moduleSheet.getRow(2),
			Seq("Module Two","IN102",5,408,408,24,13,100,308,124,0.4025974025974026,184,0.5974025974025974))
	}

	def getTestFeedbackReport = {
		val report = new FeedbackReport(department, dateTime(2013, 3, 1), dateTime(2013, 9, 30))
		report.assignmentMembershipService = assignmentMembershipService
		report.auditEventQueryMethods = auditEventQueryMethods
		report.submissionService = submissionService
		report.feedbackService = feedbackService
		report
	}

}
