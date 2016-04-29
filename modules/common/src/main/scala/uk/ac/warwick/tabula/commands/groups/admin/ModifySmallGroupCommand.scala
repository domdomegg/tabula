package uk.ac.warwick.tabula.commands.groups.admin

import org.springframework.validation.Errors
import uk.ac.warwick.tabula.JavaImports._
import uk.ac.warwick.tabula.commands._
import uk.ac.warwick.tabula.data.Transactions._
import uk.ac.warwick.tabula.data.model._
import uk.ac.warwick.tabula.data.model.groups._
import uk.ac.warwick.tabula.permissions.Permissions
import uk.ac.warwick.tabula.services._
import uk.ac.warwick.tabula.system.permissions.{PermissionsChecking, PermissionsCheckingMethods, RequiresPermissionsChecking}

object ModifySmallGroupCommand {
	type Command = Appliable[SmallGroup] with SelfValidating with ModifySmallGroupCommandState

	def edit(module: Module, set: SmallGroupSet, group: SmallGroup): Command =
		new EditSmallGroupCommandInternal(module, set, group)
			with ComposableCommand[SmallGroup]
			with EditSmallGroupPermissions
			with EditSmallGroupDescription
			with ModifySmallGroupValidation
			with AutowiringSmallGroupServiceComponent
}

trait ModifySmallGroupCommandState extends CurrentSITSAcademicYear {
	def module: Module
	def set: SmallGroupSet
	def existingSmallGroup: Option[SmallGroup]

	var name: String = _
	var maxGroupSize: Int = SmallGroup.DefaultGroupSize
}

trait EditSmallGroupCommandState extends ModifySmallGroupCommandState {
	def smallGroup: SmallGroup
	def existingSmallGroup = Some(smallGroup)
}

class EditSmallGroupCommandInternal(val module: Module, val set: SmallGroupSet, val smallGroup: SmallGroup) extends ModifySmallGroupCommandInternal with EditSmallGroupCommandState {
	self: SmallGroupServiceComponent =>

	copyFrom(smallGroup)

	override def applyInternal() = transactional() {
		copyTo(smallGroup)
		smallGroupService.saveOrUpdate(smallGroup)
		smallGroup
	}
}

abstract class ModifySmallGroupCommandInternal
	extends CommandInternal[SmallGroup] with ModifySmallGroupCommandState {

	def copyFrom(smallGroup: SmallGroup) {
		name = smallGroup.name
		maxGroupSize = smallGroup.maxGroupSize
	}

	def copyTo(smallGroup: SmallGroup) {
		smallGroup.name = name
		smallGroup.maxGroupSize = maxGroupSize
	}
}

trait ModifySmallGroupValidation extends SelfValidating {
	self: ModifySmallGroupCommandState =>

	override def validate(errors: Errors): Unit = {
		if (set.allocationMethod == SmallGroupAllocationMethod.Linked) {
			errors.reject("smallGroupSet.linked")
		}

		if (name.isEmpty) errors.rejectValue("name", "smallGroup.name.NotEmpty")
			else if (name.length > 200) errors.rejectValue("name", "smallGroup.name.Length", Array[Object](200: JInteger), "")
		}
	}

trait EditSmallGroupPermissions extends RequiresPermissionsChecking with PermissionsCheckingMethods {
	self: EditSmallGroupCommandState =>

	override def permissionsCheck(p: PermissionsChecking) {
		mustBeLinked(set, module)
		mustBeLinked(smallGroup, set)
		p.PermissionCheck(Permissions.SmallGroups.Update, mandatory(smallGroup))
	}
}

trait EditSmallGroupDescription extends Describable[SmallGroup] {
	self: EditSmallGroupCommandState =>

	override def describe(d: Description) {
		d.smallGroupSet(set)
	}
}
