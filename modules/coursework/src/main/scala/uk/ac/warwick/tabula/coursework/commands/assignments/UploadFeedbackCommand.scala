package uk.ac.warwick.tabula.coursework.commands.assignments

import java.util.ArrayList
import scala.collection.JavaConversions._
import scala.beans.BeanProperty
import scala.util.matching.Regex
import org.springframework.validation.{BindingResult, Errors}
import org.springframework.web.multipart.MultipartFile
import uk.ac.warwick.tabula.data.Transactions._
import uk.ac.warwick.tabula.CurrentUser
import uk.ac.warwick.tabula.UniversityId
import uk.ac.warwick.tabula.commands.Command
import uk.ac.warwick.tabula.commands.Description
import uk.ac.warwick.tabula.commands.UploadedFile
import uk.ac.warwick.tabula.data.Daoisms
import uk.ac.warwick.tabula.data.FileDao
import uk.ac.warwick.tabula.data.model.{Assignment, Feedback, FileAttachment}
import uk.ac.warwick.tabula.helpers.FoundUser
import uk.ac.warwick.tabula.helpers.LazyLists
import uk.ac.warwick.tabula.helpers.Logging
import uk.ac.warwick.tabula.helpers.NoUser
import uk.ac.warwick.tabula.services._
import uk.ac.warwick.tabula.helpers.StringUtils._
import uk.ac.warwick.util.core.spring.FileUtils
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream
import uk.ac.warwick.spring.Wire
import uk.ac.warwick.tabula.system.BindListener
import uk.ac.warwick.tabula.data.model.Module

class FeedbackItem {
	var uniNumber: String = _
	var file: UploadedFile = new UploadedFile

	var submissionExists = false
	var isPublished = false
	// true when at least one non-ignored file is uploaded
	var isModified = true
	var duplicateFileNames: Set[String] = Set()
	var ignoredFileNames: Set[String] = Set()

	def listAttachments() = file.attached.map(f => {
		val duplicate = duplicateFileNames.contains(f.name)
		val ignore = ignoredFileNames.contains(f.name)
		new AttachmentItem(f.name, duplicate, ignore)
	})

	def this(uniNumber: String) = {
		this()
		this.uniNumber = uniNumber
	}

	class AttachmentItem(val name: String, val duplicate: Boolean, val ignore: Boolean){}
}

// Purely for storing in command to display on the model.
case class ProblemFile(
	                      path: String,
	                      file: FileAttachment) {
	def this() = this(null, null)
}

// Purely to generate an audit log event
class ExtractFeedbackZip(cmd: UploadFeedbackCommand[_]) extends Command[Unit] {
	def applyInternal() {}
	def describe(d: Description) = d.assignment(cmd.assignment).properties(
		"archive" -> cmd.archive.getOriginalFilename)
}

/**
 * Command which adds feedback for an assignment.
 * It either takes a single uniNumber and file, or it takes a
 * zip of files with uni numbers embedded in their path.
 *
 * TODO The controller that does single-file upload isn't exposed in the UI
 * so we could check that this is no longer being accessed by anyone, and then
 * remove all the code in here that handles it, to simplify it a little.
 */
abstract class UploadFeedbackCommand[A](val module: Module, val assignment: Assignment, val submitter: CurrentUser)
	extends Command[A] with Daoisms with Logging with BindListener {
	
	// Permissions checks delegated to implementing classes FOR THE MOMENT

	val uniNumberPattern = new Regex("""(\d{7,})""")
	// TAB-114 - vast majority of module codes match this pattern
	val moduleCodePattern = new Regex("""([a-z][a-z][0-9][0-z][0-z])""")

	var zipService = Wire.auto[ZipService]
	var userLookup = Wire.auto[UserLookupService]
	var fileDao = Wire.auto[FileDao]
	var assignmentService = Wire.auto[AssignmentService]
	var stateService = Wire.auto[StateService]

	/* for single upload */
	var uniNumber: String = _
	var file: UploadedFile = new UploadedFile
	/* ----- */

	/* for multiple upload */
	// use lazy list with factory as spring doesn't know how to dynamically create items
	var items: JList[FeedbackItem] = LazyLists.simpleFactory()
	var unrecognisedFiles: JList[ProblemFile] = LazyLists.simpleFactory()
	var moduleMismatchFiles: JList[ProblemFile] = LazyLists.simpleFactory()
	var invalidFiles: JList[ProblemFile] = LazyLists.simpleFactory()
	var archive: MultipartFile = _
	var batch: Boolean = false
	var fromArchive: Boolean = false
	var confirmed: Boolean = false
	/* ---- */

	private def filenameOf(path: String) = new java.io.File(path).getName

	def preExtractValidation(errors: Errors) {
		if (batch) {
			if (archive != null && !archive.isEmpty()) {
				logger.debug("file name is " + archive.getOriginalFilename())
				if (!"zip".equals(FileUtils.getLowerCaseExtension(archive.getOriginalFilename))) {
					errors.rejectValue("archive", "archive.notazip")
				}
			} else if (items != null && items.isEmpty() && file.isMissing) {
				errors.rejectValue("file.upload", "file.missing")
			}
		}
	}

	def postExtractValidation(errors: Errors) {
		if (!invalidFiles.isEmpty) errors.rejectValue("invalidFiles", "invalidFiles")
		if (items != null && !items.isEmpty) {
			for (i <- 0 until items.length) {
				val item = items.get(i)
				errors.pushNestedPath("items[" + i + "]")
				validateUploadedFile(item, errors)
				errors.popNestedPath()
			}
		} else {
			val tempItem = new FeedbackItem(uniNumber)
			tempItem.file = file
			validateUploadedFile(tempItem, errors)
		}
	}

	private def validateUploadedFile(item: FeedbackItem, errors: Errors) {
		val file = item.file
		val uniNumber = item.uniNumber
		
		if (file.isMissing) errors.rejectValue("file", "file.missing")
		for((f, i) <- file.attached.zipWithIndex){
			if (f.actualDataLength == 0) {
				errors.rejectValue("file.attached[" + i + "]", "file.empty")
			}
			
			if ("url".equals(FileUtils.getLowerCaseExtension(f.getName))) {
				errors.rejectValue("file.attached[" + i + "]", "file.url")
			}
		}
		if (uniNumber.hasText) {
			if (!UniversityId.isValid(uniNumber)) {
				errors.rejectValue("uniNumber", "uniNumber.invalid")
			} else {
				userLookup.getUserByWarwickUniId(uniNumber) match {
					case FoundUser(u) =>
					case NoUser(u) => errors.rejectValue("uniNumber", "uniNumber.userNotFound", Array(uniNumber), "")
				}
				
				validateExisting(item, errors)

			}
		} else {
			errors.rejectValue("uniNumber", "NotEmpty")
		}
	}
	
	def validateExisting(item: FeedbackItem, errors: Errors)

	override def onBind(result:BindingResult) = transactional() {
		file.onBind(result)

		def store(itemMap: collection.mutable.Map[String, FeedbackItem], number: String, name: String, file: FileAttachment) =
			itemMap.getOrElseUpdate(number, new FeedbackItem(uniNumber = number))
				.file.attached.add(file)

		def processFiles(bits: Seq[Pair[String, FileAttachment]]) {
			// go through individual files, extracting the uni number and grouping
			// them into feedback items.
			var itemMap = new collection.mutable.HashMap[String, FeedbackItem]()
			unrecognisedFiles.clear()

			for ((filename, file) <- bits) {
				// match uni numbers found in file path
				val allNumbers = uniNumberPattern.findAllIn(filename).matchData.map { _.subgroups(0) }.toList

				// ignore any numbers longer than 7 characters.
				val numbers = allNumbers.filter { _.length == 7 }

				if (numbers.isEmpty) {
					// no numbers at all.
					unrecognisedFiles.add(new ProblemFile(filename, file))
				} else if (numbers.distinct.size > 1) {
					// multiple different numbers, ambiguous, reject this.
					invalidFiles.add(new ProblemFile(filename, file))
				} else {
					// one 7 digit number, this one might be okay.
					store(itemMap, numbers.head, filenameOf(filename), file)
				}
				
				// match potential module codes found in file path
				val allModuleCodes = moduleCodePattern.findAllIn(filename).matchData.map { _.subgroups(0)}.toList
				
				if(!allModuleCodes.isEmpty){
					// the potential module code doesn't match this assignment's module code
					if (!allModuleCodes.distinct.head.equals(assignment.module.code)){
						moduleMismatchFiles.add(new ProblemFile(filename, file))
					} 
				}
				
			}
			items = itemMap.values.toList
		}

		// ZIP has been uploaded. unpack it
		if (archive != null && !archive.isEmpty()) {
			val zip = new ZipArchiveInputStream(archive.getInputStream)

			val bits = Zips.iterator(zip) { (iterator) =>
				for (entry <- iterator if !entry.isDirectory) yield {
					val f = new FileAttachment
					// Funny char from Windows? We can't work out what it is so
					// just turn it into an underscore.
					val name = entry.getName.replace("\uFFFD", "_")
					f.name = filenameOf(name)
					f.uploadedData = new ZipEntryInputStream(zip, entry)
					f.uploadedDataLength = entry.getSize
					fileDao.saveTemporary(f)
					(name, f)
				}
			}

			processFiles(bits)

			// remember we got these items from a Zip, so we can tailor the text in the HTML.
			fromArchive = true

			// this do-nothing command is to generate an audit event to record the unzipping
			new ExtractFeedbackZip(this).apply()

		} else {
			if (batch && !file.attached.isEmpty()) {
				val bits = file.attached.map { (attachment) => attachment.name -> attachment }
				processFiles(bits)
			}

			if (items != null) {
				for (item <- items if item.file != null) {
					item.file.onBind(result)
				}
			}
		}

	}

}

