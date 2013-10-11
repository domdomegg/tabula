package uk.ac.warwick.tabula.profiles.commands

import uk.ac.warwick.tabula.commands._
import uk.ac.warwick.tabula.data.Transactions._
import uk.ac.warwick.tabula.data.model.{Member, StudentRelationship}
import uk.ac.warwick.tabula.data.model.StudentRelationshipType
import uk.ac.warwick.tabula.services._
import uk.ac.warwick.tabula.system.permissions.{PermissionsChecking, RequiresPermissionsChecking, PerformsPermissionsChecking}
import uk.ac.warwick.tabula.permissions.Permissions

// Don't need this, unless there is specific state on the command which the controller needs access to.
//
//trait ViewRelatedStudentsCommand extends ComposableCommand[Seq[StudentRelationship]]  {
//	this:ViewRelatedStudentsCommandInternal=>
//}
object ViewRelatedStudentsCommand{
	def apply(currentMember: Member, relationshipType: StudentRelationshipType): Command[Seq[StudentRelationship]] = {
		new ViewRelatedStudentsCommandInternal(currentMember, relationshipType)
			with ComposableCommand[Seq[StudentRelationship]]
			with AutowiringRelationshipServiceComponent
			with ViewRelatedStudentsCommandPermissions
			with Unaudited with ReadOnly
	}
}

trait ViewRelatedStudentsCommandState {
	val currentMember: Member
	val relationshipType: StudentRelationshipType
}

class ViewRelatedStudentsCommandInternal(val currentMember: Member, val relationshipType: StudentRelationshipType)
	extends CommandInternal[Seq[StudentRelationship]] with ViewRelatedStudentsCommandState {

	this: RelationshipServiceComponent =>

	def applyInternal(): Seq[StudentRelationship] = transactional(readOnly = true) {
		relationshipService.listStudentRelationshipsWithMember(relationshipType, currentMember)
	}

}

trait ViewRelatedStudentsCommandPermissions extends RequiresPermissionsChecking {
	this: ViewRelatedStudentsCommandState =>

	def permissionsCheck(p: PermissionsChecking) {
		p.PermissionCheck(Permissions.Profiles.StudentRelationship.Read(p.mandatory(relationshipType)), currentMember)
	}
}
