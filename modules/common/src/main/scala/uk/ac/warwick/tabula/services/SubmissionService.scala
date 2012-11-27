package uk.ac.warwick.tabula.services

import uk.ac.warwick.tabula.data.model.Submission
import uk.ac.warwick.tabula.data.model.SubmissionState._
import org.springframework.stereotype.Service
import uk.ac.warwick.tabula.data.Daoisms
import uk.ac.warwick.tabula.helpers.Logging

trait SubmissionService {
	def updateState(submission: Submission, state: SubmissionState)
}

@Service(value = "submissionService")
class SubmissionServiceServiceImpl extends SubmissionService with Daoisms with Logging {

	def updateState(submission: Submission, state: SubmissionState){
		submission.state = state
		session.saveOrUpdate(submission)
	}

}
