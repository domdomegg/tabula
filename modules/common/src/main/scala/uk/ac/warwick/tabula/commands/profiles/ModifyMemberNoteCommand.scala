package uk.ac.warwick.tabula.commands.profiles

import org.joda.time.DateTime
import org.springframework.validation.{BindingResult, Errors}
import uk.ac.warwick.spring.Wire
import uk.ac.warwick.tabula.CurrentUser
import uk.ac.warwick.tabula.commands.{Command, SelfValidating, UploadedFile}
import uk.ac.warwick.tabula.data.Transactions._
import uk.ac.warwick.tabula.data.model.{FileAttachment, Member, MemberNote}
import uk.ac.warwick.tabula.helpers.StringUtils._
import uk.ac.warwick.tabula.services.{FileAttachmentService, MemberNoteService, ProfileService, UserLookupService}
import uk.ac.warwick.tabula.system.BindListener
import uk.ac.warwick.userlookup.User

import scala.collection.JavaConversions._
import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.language.implicitConversions

abstract class ModifyMemberNoteCommand(val member: Member, val submitter: CurrentUser) extends Command[MemberNote] with BindListener with SelfValidating  {

	var profileService = Wire[ProfileService]
	var memberNoteService = Wire[MemberNoteService]
	var fileAttachmentService = Wire[FileAttachmentService]
	var userLookup = Wire[UserLookupService]

	var note: String = _
	var title: String = _
	var creationDate = DateTime.now
	var lastUpdatedDate = DateTime.now

	var file: UploadedFile = new UploadedFile
	var attachedFiles:JList[FileAttachment] = JArrayList()

	var creator: User = _
	var attachmentTypes = Seq[String]()

	val memberNote: MemberNote

	def showForm() {
		this.copyFrom(memberNote)
	}

	def applyInternal(): MemberNote = transactional() {

		creator = submitter.apparentUser

		this.copyTo(memberNote)

		if (memberNote.attachments != null) {
			val filesToKeep = Option(attachedFiles).map(_.asScala.toList).getOrElse(List())
			val filesToRemove: mutable.Buffer[FileAttachment] = memberNote.attachments.asScala -- filesToKeep
			memberNote.attachments = JArrayList[FileAttachment](filesToKeep)
			fileAttachmentService.deleteAttachments(filesToRemove)
		}

		if (!file.attached.isEmpty) {
			for (attachment <- file.attached) {
				memberNote.addAttachment(attachment)
			}
		}

		memberNoteService.saveOrUpdate(memberNote)

		memberNote
	}

	// can be overridden in concrete implementations to provide additional validation
	def contextSpecificValidation(errors: Errors)

	def validate(errors:Errors){
		contextSpecificValidation(errors)

		if (!note.hasText && !file.hasAttachments){
			errors.rejectValue("note", "profiles.memberNote.empty")
		}
	}

	def onBind(result: BindingResult) {
		file.onBind(result)
	}

	def copyFrom(memberNote: MemberNote) {

		this.note = memberNote.note
		this.title = memberNote.title
		this.creationDate = memberNote.creationDate
		this.creator = memberNote.creator
		this.attachedFiles = memberNote.attachments

	}

	def copyTo(memberNote: MemberNote) {
		memberNote.note = this.note
		memberNote.title = this.title
		memberNote.creationDate = this.creationDate
		memberNote.creatorId = this.creator.getWarwickId
		memberNote.member = this.member
		memberNote.lastUpdatedDate = new DateTime()

	}

}
