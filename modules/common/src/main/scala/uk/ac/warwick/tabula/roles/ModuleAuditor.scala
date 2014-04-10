package uk.ac.warwick.tabula.roles

import uk.ac.warwick.tabula.data._
import uk.ac.warwick.tabula.permissions.Permissions._
import uk.ac.warwick.tabula.permissions.PermissionsTarget
import uk.ac.warwick.tabula.JavaImports

case class ModuleAuditor(module: model.Module) extends BuiltInRole(ModuleAuditorRoleDefinition, module)

case object ModuleAuditorRoleDefinition extends BuiltInRoleDefinition {
	
	override def description = "Module Auditor"

	GrantsScopedPermission( 
		Module.Administer,
		Module.ManageAssignments,
		Module.ManageSmallGroups,
		
		Assignment.Read,
		
		Submission.ViewPlagiarismStatus,
		Submission.Read,
		
		Marks.DownloadTemplate,
		Marks.Read,
		
		Extension.Read,

		Feedback.Read,
		
		SmallGroups.Read,
		SmallGroups.ReadMembership,
		SmallGroupEvents.ViewRegister,

		ModuleRegistration.Core,
		ModuleRegistration.Results
	)

	def canDelegateThisRolesPermissions: JavaImports.JBoolean = false
}