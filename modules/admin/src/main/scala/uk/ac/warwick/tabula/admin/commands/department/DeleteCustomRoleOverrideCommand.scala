package uk.ac.warwick.tabula.admin.commands.department

import uk.ac.warwick.tabula.commands.{Description, Describable, SelfValidating, CommandInternal, ComposableCommand}
import uk.ac.warwick.tabula.data.Transactions._
import org.springframework.validation.Errors
import uk.ac.warwick.tabula.system.permissions.{PermissionsChecking, RequiresPermissionsChecking}
import uk.ac.warwick.tabula.permissions.Permissions
import uk.ac.warwick.tabula.data.model.permissions.{RoleOverride, CustomRoleDefinition}
import uk.ac.warwick.tabula.data.model.Department
import uk.ac.warwick.tabula.services.permissions.{AutowiringPermissionsServiceComponent, PermissionsServiceComponent}

object DeleteCustomRoleOverrideCommand {
	def apply(department: Department, customRoleDefinition: CustomRoleDefinition, roleOverride: RoleOverride) =
		new DeleteCustomRoleOverrideCommandInternal(department, customRoleDefinition, roleOverride)
			with ComposableCommand[RoleOverride]
			with DeleteCustomRoleOverrideCommandDescription
			with DeleteCustomRoleOverrideCommandValidation
			with DeleteCustomRoleOverrideCommandPermissions
			with AutowiringPermissionsServiceComponent
}

trait DeleteCustomRoleOverrideCommandState {
	def department: Department
	def customRoleDefinition: CustomRoleDefinition
	def roleOverride: RoleOverride
}

class DeleteCustomRoleOverrideCommandInternal(val department: Department, val customRoleDefinition: CustomRoleDefinition, val roleOverride: RoleOverride) extends CommandInternal[RoleOverride] with DeleteCustomRoleOverrideCommandState {
	self: PermissionsServiceComponent =>

	override def applyInternal() = transactional() {
		customRoleDefinition.overrides.remove(roleOverride)

		permissionsService.saveOrUpdate(customRoleDefinition)
		roleOverride
	}
}

trait DeleteCustomRoleOverrideCommandPermissions extends RequiresPermissionsChecking {
	self: DeleteCustomRoleOverrideCommandState =>

	def permissionsCheck(p: PermissionsChecking) {
		p.mustBeLinked(p.mandatory(customRoleDefinition), p.mandatory(department))
		p.mustBeLinked(p.mandatory(roleOverride), p.mandatory(customRoleDefinition))
		p.PermissionCheck(Permissions.RolesAndPermissions.Delete, roleOverride)
	}
}

trait DeleteCustomRoleOverrideCommandValidation extends SelfValidating {
	self: DeleteCustomRoleOverrideCommandState =>

	override def validate(errors: Errors) {}
}

trait DeleteCustomRoleOverrideCommandDescription extends Describable[RoleOverride] {
	self: DeleteCustomRoleOverrideCommandState =>
	// describe the thing that's happening.
	override def describe(d: Description) =
		d.customRoleDefinition(customRoleDefinition)
}

