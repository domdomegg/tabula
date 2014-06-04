package uk.ac.warwick.tabula.services

import java.io.File
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

import org.springframework.beans.factory.annotation.{Autowired, Value}
import org.springframework.beans.factory.InitializingBean
import org.springframework.stereotype.Service
import uk.ac.warwick.tabula.data.model._
import uk.ac.warwick.tabula.helpers.Logging
import org.apache.commons.io.input.BoundedInputStream
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import uk.ac.warwick.tabula.helpers.Closeables._
import uk.ac.warwick.tabula.Features
import uk.ac.warwick.userlookup.User
import uk.ac.warwick.userlookup.AnonymousUser
import scala.collection.JavaConverters._
import uk.ac.warwick.spring.Wire

/**
 * FIXME this could generate a corrupt file if two requests tried to generate the same zip simultaneously
 * 		 or if a feedback upload invalidates a zip while one is being generated/downloaded
 */
@Service
class ZipService extends InitializingBean with ZipCreator with Logging {

	@Value("${filesystem.zip.dir}") var zipDir: File = _
	@Value("${filesystem.create.missing}") var createMissingDirectories: Boolean = _
	@Autowired var features: Features = _
	@Autowired var userLookup: UserLookupService = _
	
	val idSplitSize = 4

	logger.info("Creating ZipService")

	override def afterPropertiesSet() {
		if (!zipDir.exists) {
			if (createMissingDirectories) {
				zipDir.mkdirs
			} else {
				throw new IllegalStateException("zip dir '%s' does not exist" format zipDir)
			}
		}
	}

	def partition(id: String): String = id.replace("-", "").grouped(idSplitSize).mkString("/")

	def resolvePath(feedback: Feedback): String = "feedback/" + partition(feedback.id)
	def resolvePath(submission: Submission): String = "submission/" + partition(submission.id)
	def resolvePathForFeedback(assignment: Assignment) = "all-feedback/" + partition(assignment.id)
	def resolvePathForSubmission(assignment: Assignment) = "all-submissions/" + partition(assignment.id)

	def showStudentName(assignment: Assignment): Boolean = assignment.module.department.showStudentName

	def invalidateFeedbackZip(assignment: Assignment) = invalidate(resolvePathForFeedback(assignment))
	def invalidateSubmissionZip(assignment: Assignment) = invalidate(resolvePathForSubmission(assignment))
	def invalidateIndividualFeedbackZip(feedback: Feedback) = invalidate(resolvePath(feedback))

	def getFeedbackZip(feedback: Feedback): File =
		getZip(resolvePath(feedback), getFeedbackZipItems(feedback))

	def getSubmissionZip(submission: Submission): File =
		getZip(resolvePath(submission), getSubmissionZipItems(submission))

	private def getFeedbackZipItems(feedback: Feedback): Seq[ZipItem] =
		feedback.attachments.asScala.map { (attachment) =>
			new ZipFileItem(feedback.universityId + " - " + attachment.name, attachment.dataStream)
		}
	
	private def getMarkerFeedbackZipItems(markerFeedback: MarkerFeedback): Seq[ZipItem] =
		markerFeedback.attachments.asScala.map { (attachment) =>
			new ZipFileItem(markerFeedback.feedback.universityId + " - " + attachment.name, attachment.dataStream)
		}

	/**
	 * A zip of feedback with a folder for each student.
	 */
	def getAllFeedbackZips(assignment: Assignment): File =
		getZip(resolvePathForFeedback(assignment),
			assignment.feedbacks.asScala flatMap getFeedbackZipItems //flatmap - take the lists of items, and flattens them to one single list
		)

	/**
	 * Find all file attachment fields and any attachments in them, as a single list.
	 * TODO This doesn't check for duplicate file names
	 */
	def getSubmissionZipItems(submission: Submission): Seq[ZipItem] = {
		val allAttachments = submission.allAttachments
		val user = userLookup.getUserByUserId(submission.userId)
		val assignment = submission.assignment
		val code = assignment.module.code

		val submissionZipItems: Seq[ZipItem] = for (attachment <- allAttachments) yield {
			val userIdentifier = if(!showStudentName(assignment) || (user==null || user.isInstanceOf[AnonymousUser])) {
				submission.universityId
			} else {
				s"${user.getFullName} - ${submission.universityId}"
			}

			new ZipFileItem(code + " - " + userIdentifier + " - " + attachment.name, attachment.dataStream)
		}

		if (features.feedbackTemplates){
			val feedbackSheets = generateFeedbackSheet(submission)
			feedbackSheets ++ submissionZipItems
		}
		else
			submissionZipItems
	}

	/**
	 * Get a zip containing these submissions. If there is more than one submission
	 * for a user, the zip _might_ work but look weird.
	 */
	def getSomeSubmissionsZip(submissions: Seq[Submission]): File =
		createUnnamedZip(submissions flatMap getSubmissionZipItems)

	/**
		* Get a zip containing these feedbacks.
	*/
	def getSomeFeedbacksZip(feedbacks: Seq[Feedback]): File =
		createUnnamedZip(feedbacks flatMap getFeedbackZipItems)

	/**
	 * Get a zip containing these marker feedbacks.
	 */
	def getSomeMarkerFeedbacksZip(markerFeedbacks: Seq[MarkerFeedback]): File =
		createUnnamedZip(markerFeedbacks flatMap getMarkerFeedbackZipItems)

	/**
	 * A zip of submissions with a folder for each student.
	 */
	def getAllSubmissionsZip(assignment: Assignment): File =
		getZip(resolvePathForSubmission(assignment),
			assignment.submissions.asScala flatMap getSubmissionZipItems)

	/**
	 * A zip of feedback templates for each student registered on the assignment
	 * assumes a feedback template exists
	 */
	def getMemberFeedbackTemplates(users: Seq[User], assignment: Assignment): File = {
		val templateFile = assignment.feedbackTemplate.attachment
		val zipItems:Seq[ZipItem] = for (user <- users) yield {
			val filename = assignment.module.code + " - " + user.getWarwickId + " - " + templateFile.name
			new ZipFileItem(filename, templateFile.dataStream)
		}
		createUnnamedZip(zipItems)
	}

	/**
	 * Returns a sequence with a single ZipItem (the feedback template) or an empty
	 * sequence if no feedback template exists
	 */
	def generateFeedbackSheet(submission: Submission): Seq[ZipItem] = {
		// wrap template in an option to deal with nulls
		Option(submission.assignment.feedbackTemplate) match {
			case Some(t) => Seq(new ZipFileItem(submission.zipFileName(t.attachment), t.attachment.dataStream))
			case None => Seq()
		}
	}

	def getSomeMeetingRecordAttachmentsZip(meetingRecord: AbstractMeetingRecord): File =
		createUnnamedZip(getMeetingRecordZipItems(meetingRecord))
	
	private def getMeetingRecordZipItems(meetingRecord: AbstractMeetingRecord): Seq[ZipItem] =
		meetingRecord.attachments.asScala.map { (attachment) =>
			new ZipFileItem(attachment.name, attachment.dataStream)
		}

	def getSomeMemberNoteAttachmentsZip(memberNote: MemberNote): File =
		createUnnamedZip(getMemberNoteZipItems(memberNote))

	private def getMemberNoteZipItems(memberNote: MemberNote): Seq[ZipItem] =
		memberNote.attachments.asScala.map { (attachment) =>
			new ZipFileItem(attachment.name, attachment.dataStream)
		}
}

trait ZipServiceComponent {
	def zipService: ZipService
}

trait AutowiringZipServiceComponent extends ZipServiceComponent {
	var zipService = Wire[ZipService]
}

/**
 * InputStream to read a single zip entry from a parent input stream.
 */
class ZipEntryInputStream(val zip: InputStream, val entry: ZipEntry)
	extends BoundedInputStream(zip, entry.getSize) {
	// don't close input stream - we'll close it when we're finished reading the whole file
	setPropagateClose(false)
}

object Zips {

	def each(zip: ZipInputStream)(fn: (ZipEntry) => Unit) { map(zip)(fn) }

	/**
	 * Provides an iterator for ZipEntry items which will be closed when you're done with them.
	 * The object returned from the function is converted to a list to guarantee that it's evaluated before closing.
	 */
	//   def iterator[A](zip:ZipInputStream)(fn: (Iterator[ZipEntry])=>Iterator[A]): List[A] = ensureClose(zip) {
	//	   fn( Iterator.continually{zip.getNextEntry}.takeWhile{_ != null} ).toList
	//   }

	def iterator[A](zip: ZipArchiveInputStream)(fn: (Iterator[ZipArchiveEntry]) => Iterator[A]): List[A] = ensureClose(zip) {
		fn(Iterator.continually { zip.getNextZipEntry }.takeWhile { _ != null }).toList
	}

	def map[A](zip: ZipInputStream)(fn: (ZipEntry) => A): Seq[A] = ensureClose(zip) {
		Iterator.continually { zip.getNextEntry }.takeWhile { _ != null }.map { (item) =>
			val t = fn(item)
			zip.closeEntry()
			t
		}.toList // use toList to evaluate items now, before we actually close the stream
	}

}