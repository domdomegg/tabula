package uk.ac.warwick.tabula.services


import org.springframework.stereotype.Service

import uk.ac.warwick.tabula.data.Daoisms
import uk.ac.warwick.tabula.data.model._
import uk.ac.warwick.tabula.helpers.Logging
import uk.ac.warwick.spring.Wire

trait SubmissionService {
	def saveSubmission(submission: Submission)
	def getSubmissionByUniId(assignment: Assignment, uniId: String): Option[Submission]
	def getSubmission(id: String): Option[Submission]

	def delete(submission: Submission): Unit
}

trait OriginalityReportService {
	def deleteOriginalityReport(attachment: FileAttachment): Unit
	def saveOriginalityReport(attachment: FileAttachment): Unit
}

@Service(value = "submissionService")
class SubmissionServiceImpl extends SubmissionService with Daoisms with Logging {

	def saveSubmission(submission: Submission) = {
		session.saveOrUpdate(submission)
		session.flush()
	}

	def getSubmissionByUniId(assignment: Assignment, uniId: String) = {
		session.newCriteria[Submission]
			.add(is("assignment", assignment))
			.add(is("universityId", uniId))
			.uniqueResult
	}

	def getSubmission(id: String) = getById[Submission](id)

	def delete(submission: Submission) {
		submission.assignment.submissions.remove(submission)
		session.delete(submission)
		// force delete now, just for the cases where we re-insert in the same session
		// (i.e. when a student is resubmitting work). [HFC-385#comments]
		session.flush()
	}
}

trait SubmissionServiceComponent {
	def submissionService: SubmissionService
}

trait AutowiringSubmissionServiceComponent extends SubmissionServiceComponent {
	var submissionService = Wire[SubmissionService]
}



@Service(value = "originalityReportService")
class OriginalityReportServiceImpl extends OriginalityReportService with Daoisms with Logging {

	/**
	 * Deletes the OriginalityReport attached to this Submission if one
	 * exists. It flushes the session straight away because otherwise deletes
	 * don't always happen until after some insert operation that assumes
	 * we've deleted it.
	 */
	def deleteOriginalityReport(attachment: FileAttachment) {
		if (attachment.originalityReport != null) {
			val report = attachment.originalityReport
			attachment.originalityReport = null
			session.delete(report)
			session.flush()
		}
	}

	def saveOriginalityReport(attachment: FileAttachment) {
		attachment.originalityReport.attachment = attachment
		session.save(attachment.originalityReport)
	}
}