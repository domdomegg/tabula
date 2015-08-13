package uk.ac.warwick.tabula.coursework.jobs


import org.springframework.stereotype.Component
import uk.ac.warwick.tabula.data.model._
import uk.ac.warwick.tabula.helpers.Logging
import uk.ac.warwick.tabula.helpers.StringUtils._
import uk.ac.warwick.tabula.services._
import uk.ac.warwick.tabula.services.jobs.JobInstance
import uk.ac.warwick.tabula.web.views.FreemarkerRendering
import uk.ac.warwick.tabula.jobs._
import uk.ac.warwick.tabula.services.turnitinlti.{TurnitinLtiResponse, TurnitinLtiService, AutowiringTurnitinLtiServiceComponent}
import scala.collection.JavaConverters._
import uk.ac.warwick.spring.Wire
import uk.ac.warwick.tabula.data.Transactions._
import uk.ac.warwick.tabula.coursework.web.Routes
import uk.ac.warwick.tabula.jobs.JobPrototype
import uk.ac.warwick.tabula.data.model.notifications.coursework.{TurnitinJobSuccessNotification, TurnitinJobErrorNotification}
import scala.annotation.tailrec
import uk.ac.warwick.tabula.CurrentUser

object SubmitToTurnitinLtiJob {
	val identifier = "turnitin-submit-lti"
	def apply(assignment: Assignment) = JobPrototype(identifier, Map(
		"assignment" -> assignment.id))
}

@Component
class SubmitToTurnitinLtiJob extends Job
	with NotifyingJob[Seq[OriginalityReport]]
  with Logging with FreemarkerRendering
	with AutowiringAssessmentServiceComponent with AutowiringTurnitinLtiServiceComponent
	with AutowiringFileAttachmentServiceComponent with AutowiringOriginalityReportServiceComponent {

	val identifier = SubmitToTurnitinLtiJob.identifier

	var topLevelUrl: String = Wire.property("${toplevel.url}")

	// Turnitin have requested that submissions should be sent at a rate of no more than 1 per second
	val WaitingRequestsToTurnitinSleep = 2000
	val WaitingRequestsToTurnitinRetries = 5


	val WaitingRequestsFromTurnitinCallbackSleep = 2000
	val WaitingRequestsFromTurnitinCallbacksRetries = 10

	var sendNotifications = true

	def run(implicit job: JobInstance) {
			new Runner(job).run()
	}

	class Runner(job: JobInstance) {
		implicit private val _job: JobInstance = job

		val assignment = {
			transactional(readOnly = true) {
			val id = job.getString("assignment")
			assessmentService.getAssignmentById(id) getOrElse (throw obsoleteJob)
		}}

		def run() {
			updateStatus("Submitting to Turnitin...")
			updateProgress(10) // update the progress bar

			submitAssignment(WaitingRequestsToTurnitinRetries)

			awaitTurnitinId(assignment, WaitingRequestsFromTurnitinCallbacksRetries)

			updateStatus("Submitting papers to Turnitin")
			val allAttachments = assignment.submissions.asScala flatMap { _.allAttachments.filter(TurnitinLtiService.validFileType) }
			val uploadsTotal = allAttachments.size

			val failedUploads = submitPapers(assignment, uploadsTotal)

			updateStatus("Getting similarity reports from Turnitin")
			val (originalityReports, noResults) = retrieveResults(uploadsTotal)

			if (sendNotifications) {
				debug("Sending an email to " + job.user.email)
				val notification = Notification.init(new TurnitinJobSuccessNotification, job.user.apparentUser, originalityReports, assignment)
				// there shouldn't be dupes, but if there are, the first, failed upload, error message is more useful
				notification.failedUploads.value = noResults ++ failedUploads
				pushNotification(job, notification)
			}
			transactional() {
				updateStatus("Generated a report.")
				job.succeeded = true
				updateProgress(100)
			}
		}

		@tailrec
		private def submitAssignment(retries: Int): TurnitinLtiResponse = {
			def submit()  = {
				Thread.sleep(WaitingRequestsToTurnitinSleep)
				turnitinLtiService.submitAssignment(assignment, job.user)
			}
			submit() match {
				case response if response.success => response
				case response if retries == 0 => throw new FailedJobException("Failed to submit assignment '" + assignment.name + "' - " + Some(response.statusMessage))
				case _ => submitAssignment(retries -1)
			}
		}

		private def submitPapers(assignment: Assignment, uploadsTotal: Int): Map[String, String] = {

			var failedUploads = Map[String, String]()
			var uploadsDone: Int = 0

			assignment.submissions.asScala.foreach(submission => {
				for (attachment <- submission.allAttachments if TurnitinLtiService.validFileType(attachment)) {
					// Don't need to resubmit the same papers again.
					if (attachment.originalityReport == null || !attachment.originalityReport.reportReceived) {
						val token: FileAttachmentToken = getToken(attachment)
						val attachmentAccessUrl = Routes.admin.assignment.turnitinlti.fileByToken(submission, attachment, token)
						val submitPaper = submitSinglePaper(attachmentAccessUrl, submission, attachment, WaitingRequestsToTurnitinRetries)
							if (!submitPaper.success) {
								failedUploads += (attachment.name -> submitPaper.statusMessage.getOrElse("failed upload"))
							}
					}
					uploadsDone += 1
				}
				updateProgress(10 + ((uploadsDone * 40) / uploadsTotal)) // 10% to 50%
			})

			if (failedUploads.nonEmpty) logger.error("Not all papers were submitted to Turnitin successfully.")
			failedUploads
		}

		@tailrec
		private def submitSinglePaper(
	 		attachmentAccessUrl: String, submission: Submission, attachment: FileAttachment, retries: Int
		): TurnitinLtiResponse = {

			def submit() = {
				Thread.sleep(WaitingRequestsToTurnitinSleep)
				turnitinLtiService.submitPaper(assignment, attachmentAccessUrl,
					s"${submission.userId}@TurnitinLti.warwick.ac.uk", attachment, submission.universityId, "Student")
			}

			submit() match {
				case response if response.success => response
				case response if retries == 0 => response
				case _ => submitSinglePaper(attachmentAccessUrl, submission, attachment, retries-1)
			}
		}

		def retrieveResults(uploadsTotal: Int): (Seq[OriginalityReport], Map[String, String]) = {
			var resultsReceived = 0
			val originalityReports = Seq()
			var failedResults = Map[String, String]()
			assignment.submissions.asScala.foreach(submission => {
				submission.allAttachments.foreach(attachment => {
					val originalityReport = transactional(readOnly = true) {
						originalityReportService.getOriginalityReportByFileId(attachment.id)
					}

					if (originalityReport.isDefined && !originalityReport.get.reportReceived) {
						val report = originalityReport.get

						val response = retrieveSinglePaperResults(report.turnitinId, job.user, WaitingRequestsToTurnitinRetries)

						if (response.success) {
							val result = response.submissionInfo()
							report.similarity = result.similarity.map(_.toInt)
							report.publicationOverlap = result.publication_overlap.map(_.toInt)
							report.webOverlap = result.web_overlap.map(_.toInt)
							report.studentOverlap = result.student_overlap.map(_.toInt)
							attachment.originalityReport = report
							attachment.originalityReport.reportReceived = true
							transactional() {
								originalityReportService.saveOriginalityReport(attachment)
							}
							originalityReports :+ report
						} else {
							logger.warn(s"Failed to get results for ${attachment.id}: ${response.statusMessage.getOrElse("")}")
							failedResults += (attachment.name -> response.statusMessage.getOrElse("failed to retrieve results"))
						}
					} else {
						logger.warn(s"Failed to find originality report for attachment $attachment.id")
						failedResults += (attachment.name -> "failed to find Originality Report")
					}
					resultsReceived += 1
				})

				updateProgress(10 + (((resultsReceived * 40) / uploadsTotal) * 2)) // 50% to 90%
			})
			if (failedResults.nonEmpty) logger.error("Did not receive all reports from Turnitin.")
			(originalityReports, failedResults)
		}

		@tailrec
		private def retrieveSinglePaperResults(
			turnitinPaperId: String, currentUser: CurrentUser, retries: Int
		): TurnitinLtiResponse = {

			def getResults = {
				Thread.sleep(WaitingRequestsToTurnitinSleep)
				turnitinLtiService.getSubmissionDetails(turnitinPaperId, currentUser)
			}

			getResults match {
				case response if response.success => response
				case response if retries == 0 => response
				case _ => retrieveSinglePaperResults(turnitinPaperId, currentUser, retries-1)
			}
		}

		private def getToken(attachment: FileAttachment): FileAttachmentToken = {
			transactional() {
				val token = attachment.generateToken()
				fileAttachmentService.saveOrUpdate(token)
				token
			}
		}

		private def sendFailureNotification(job: JobInstance, assignment: Assignment) {
			debug("Sending an email to " + job.user.email)
			val notification = Notification.init(new TurnitinJobErrorNotification, job.user.apparentUser, Seq[OriginalityReport](), assignment)
			pushNotification(job, notification)
		}

		@tailrec
		private def awaitTurnitinId(assignment: Assignment, retries: Int): String = {
			// wait for Callback from Turnitin with Turnitin assignment id - if it already has a turnitin assignment id, that's fine
			def hasTurnitinId = {
				Thread.sleep(WaitingRequestsFromTurnitinCallbackSleep)
				assignment.turnitinId.hasText
			}

			hasTurnitinId match {
				case true => assignment.turnitinId
				case false if retries == 0 =>
					if (sendNotifications) {
						sendFailureNotification(job, assignment)
					}
					throw new FailedJobException("Failed to submit the assignment to Turnitin")
				case _ => awaitTurnitinId(assignment, retries - 1)
			}
		}
	}
}