package uk.ac.warwick.tabula.commands.profiles

import org.springframework.validation.Errors
import uk.ac.warwick.tabula.CurrentUser
import uk.ac.warwick.tabula.commands.Description
import uk.ac.warwick.tabula.data.model.MemberNote
import uk.ac.warwick.tabula.permissions.Permissions

class EditMemberNoteCommand(note: MemberNote, currentUser: CurrentUser) extends ModifyMemberNoteCommand(note.member, currentUser) {

	PermissionCheck(Permissions.MemberNotes.Update, note)

	val memberNote = note

	def describe(d: Description) = d.memberNote(memberNote)

	override def contextSpecificValidation(errors:Errors){
		if (memberNote.deleted) errors.rejectValue("note", "profiles.memberNote.edit.deleted")
	}

}
