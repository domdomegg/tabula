package uk.ac.warwick.tabula.scheduling.commands

import org.joda.time.DateTime
import uk.ac.warwick.tabula._
import uk.ac.warwick.tabula.commands.ComposableCommand
import uk.ac.warwick.tabula.data.model.FeedbackForSitsStatus.Successful
import uk.ac.warwick.tabula.data.model.{DegreeType, FeedbackForSits, MarkType}
import uk.ac.warwick.tabula.data.{FeedbackForSitsDao, FeedbackForSitsDaoComponent}
import uk.ac.warwick.tabula.scheduling.services.{ExportFeedbackToSitsService, ExportFeedbackToSitsServiceComponent}
import uk.ac.warwick.tabula.services.FeedbackService
import uk.ac.warwick.userlookup.User

trait ComponentMixins
extends FeedbackForSitsDaoComponent
with ExportFeedbackToSitsServiceComponent with Mockito {
	var exportFeedbackToSitsService: ExportFeedbackToSitsService = smartMock[ExportFeedbackToSitsService]
	var feedbackForSitsDao: FeedbackForSitsDao = smartMock[FeedbackForSitsDao]
}

class ExportFeedbackToSitsCommandTest extends TestBase  with ComponentMixins with Mockito {

	trait Environment {
		val module = Fixtures.module("HY903", "Further Explorations")
		module.degreeType = DegreeType.Undergraduate

		// test department
		val department = Fixtures.department("XX", "Xander Department")
		department.id = "1"
		department.code = "XX"
		department.setUploadMarksToSitsForYear(new AcademicYear(2014), DegreeType.Undergraduate, canUpload = true)

		module.adminDepartment = department

		val cmd = new ExportFeedbackToSitsCommand
			with ComposableCommand[Seq[FeedbackForSits]]
			with ExportFeedbackToSitsCommandPermissions
			with ExportFeedbackToSitsCommandDescription
			with ComponentMixins

		val assignment = Fixtures.assignment("test assignment")
		assignment.module = module
		assignment.academicYear = new AcademicYear(2014)

		// set up feedback
		val feedback = Fixtures.assignmentFeedback("0070790")
		feedback.assignment = assignment
		feedback.actualGrade = Some("B")
		feedback.actualMark = Some(73)
		feedback.id = "397"

		assignment.feedbacks.add(feedback)

	}

	trait EnvironmentMarkAdjusted extends Environment {
		feedback.addMark(null, MarkType.Adjustment, 78, null, null)
	}

	trait EnvironmentMarkAndGradeAdjusted extends EnvironmentMarkAdjusted {
		feedback.latestPrivateAdjustment.map(_.grade = Some("A-"))
	}

	@Test def testUploadFeedbackToSitsMarkAndGradeAdjusted() = withUser("0070790", "cusdx") {
		new EnvironmentMarkAndGradeAdjusted {

			cmd.feedbackForSitsDao = feedbackForSitsDao

			val feedbackForSits = Fixtures.feedbackForSits(feedback, currentUser.apparentUser)

			exportFeedbackToSitsService.exportToSits(feedbackForSits) returns 1
			cmd.exportFeedbackToSitsService = exportFeedbackToSitsService

			// upload the feedback to SITS
			cmd.uploadFeedbackToSits(feedbackForSits)

			// check that the feedbackForSits record has been updated to reflect the fact that data has been written to SITS
			val dateOfUpload = feedbackForSits.dateOfUpload
			dateOfUpload should not be null
			dateOfUpload.isAfter(DateTime.now) should be{false}
			dateOfUpload.plusMinutes(1).isAfter(DateTime.now) should be{true}

			feedbackForSits.actualGradeLastUploaded should be("A-")
			feedbackForSits.actualMarkLastUploaded should be(78)
		}
	}

	@Test def testUploadFeedbackToSitsMarkAdjusted() = withUser("0070790", "cusdx") {
		new EnvironmentMarkAdjusted {

			cmd.feedbackForSitsDao = feedbackForSitsDao

			val feedbackForSits = Fixtures.feedbackForSits(feedback, currentUser.apparentUser)

			exportFeedbackToSitsService.exportToSits(feedbackForSits) returns 1
			cmd.exportFeedbackToSitsService = exportFeedbackToSitsService

			// upload the feedback to SITS
			cmd.uploadFeedbackToSits(feedbackForSits)

			// check that the feedbackForSits record has been updated to reflect the fact that data has been written to SITS
			val dateOfUpload = feedbackForSits.dateOfUpload
			dateOfUpload should not be null
			dateOfUpload.isAfter(DateTime.now) should be{false}
			dateOfUpload.plusMinutes(1).isAfter(DateTime.now) should be{true}

			feedbackForSits.actualGradeLastUploaded should be("B")
			feedbackForSits.actualMarkLastUploaded should be(78)
		}
	}

	@Test def testUploadFeedbackToSitsNotAdjusted() = withUser("0070790", "cusdx") {
		new Environment {
			cmd.feedbackForSitsDao = feedbackForSitsDao

			val feedbackForSits = Fixtures.feedbackForSits(feedback, currentUser.apparentUser)

			exportFeedbackToSitsService.exportToSits(feedbackForSits) returns 1
			cmd.exportFeedbackToSitsService = exportFeedbackToSitsService

			// upload the feedback to SITS
			cmd.uploadFeedbackToSits(feedbackForSits)

			// check that the feedbackForSits record has been updated to reflect the fact that data has been written to SITS
			val dateOfUpload = feedbackForSits.dateOfUpload
			dateOfUpload should not be null
			dateOfUpload.isAfter(DateTime.now) should be{false}
			dateOfUpload.plusMinutes(1).isAfter(DateTime.now) should be{true}

			feedbackForSits.actualGradeLastUploaded should be("B")
			feedbackForSits.actualMarkLastUploaded should be(73)
		}
	}

	@Test def testApply() = withUser("0070790", "cusdx") {
		new EnvironmentMarkAndGradeAdjusted {
			val user = currentUser.apparentUser
			val feedbackService = smartMock[FeedbackService]
			feedbackService.getUsersForFeedback(assignment) returns Seq[(String, User)]((user.getWarwickId, user))

			val feedbackForSits = Fixtures.feedbackForSits(feedback, currentUser.apparentUser)
			feedbackForSitsDao.feedbackToLoad returns Seq(feedbackForSits)
			cmd.feedbackForSitsDao = feedbackForSitsDao

			exportFeedbackToSitsService.countMatchingSasRecords(feedbackForSits) returns 1
			exportFeedbackToSitsService.exportToSits(feedbackForSits) returns 1

			cmd.exportFeedbackToSitsService = exportFeedbackToSitsService

			// call apply to queue the feedback
			val uploadedFeedbacks = cmd.applyInternal()

			// check to see if the return is as expected
			uploadedFeedbacks.size should be(1)
			uploadedFeedbacks.head.status should be(Successful)
			uploadedFeedbacks.head.feedback.actualMark should be(Some(73))
			uploadedFeedbacks.head.feedback.actualGrade should be(Some("B"))
			uploadedFeedbacks.head.feedback.adjustedMark should be(Some(78))
			uploadedFeedbacks.head.feedback.adjustedGrade should be(Some("A-"))
		}
	}

}
