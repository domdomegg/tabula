package uk.ac.warwick.tabula.groups.commands.admin.reusable

import org.springframework.validation.Errors
import uk.ac.warwick.tabula.JavaImports._
import uk.ac.warwick.tabula.commands._
import uk.ac.warwick.tabula.data.model.groups.{DepartmentSmallGroupSet, DepartmentSmallGroup}
import uk.ac.warwick.tabula.helpers.LazyLists
import uk.ac.warwick.tabula.permissions.Permissions
import uk.ac.warwick.tabula.services.{AutowiringSmallGroupServiceComponent, SmallGroupServiceComponent}
import uk.ac.warwick.tabula.system.permissions.{PermissionsChecking, PermissionsCheckingMethods, RequiresPermissionsChecking}
import scala.collection.JavaConverters._
import uk.ac.warwick.tabula.helpers.StringUtils._

object EditDepartmentSmallGroupsCommand {
	def apply(set: DepartmentSmallGroupSet) =
		new EditDepartmentSmallGroupsCommandInternal(set)
			with ComposableCommand[Seq[DepartmentSmallGroup]]
			with EditDepartmentSmallGroupsPermissions
			with EditDepartmentSmallGroupsValidation
			with EditDepartmentSmallGroupsDescription
			with PopulateEditDepartmentSmallGroupsCommand
			with AutowiringSmallGroupServiceComponent
}

trait EditDepartmentSmallGroupsCommandState {
	def set: DepartmentSmallGroupSet

	var groupNames: JList[String] = LazyLists.create()
}

class EditDepartmentSmallGroupsCommandInternal(val set: DepartmentSmallGroupSet) extends CommandInternal[Seq[DepartmentSmallGroup]] with EditDepartmentSmallGroupsCommandState {
	self: SmallGroupServiceComponent =>

	override def applyInternal() = {
		groupNames.asScala.zipWithIndex.foreach { case (name, i) =>
			if (set.groups.size() > i) {
				// Edit an existing group
				set.groups.get(i).name = name
			} else {
				// Add a new group
				val group = new DepartmentSmallGroup(set)
				group.name = name

				set.groups.add(group)
			}
		}

		if (groupNames.size() < set.groups.size()) {
			for (i <- set.groups.size() to groupNames.size()) {
				set.groups.remove(i - 1)
			}
		}

		smallGroupService.saveOrUpdate(set)
		set.groups.asScala
	}
}

trait PopulateEditDepartmentSmallGroupsCommand extends PopulateOnForm {
	self: EditDepartmentSmallGroupsCommandState =>

	override def populate() {
		groupNames.clear()
		groupNames.addAll(set.groups.asScala.map { _.name }.asJava)
	}
}

trait EditDepartmentSmallGroupsPermissions extends RequiresPermissionsChecking with PermissionsCheckingMethods {
	self: EditDepartmentSmallGroupsCommandState =>

	override def permissionsCheck(p: PermissionsChecking) {
		p.PermissionCheck(Permissions.SmallGroups.Update, mandatory(set))
	}
}

trait EditDepartmentSmallGroupsDescription extends Describable[Seq[DepartmentSmallGroup]] {
	self: EditDepartmentSmallGroupsCommandState =>

	override def describe(d: Description) {
		d.department(set.department).properties("smallGroupSet" -> set.id)
	}

}

trait EditDepartmentSmallGroupsValidation extends SelfValidating {
	self: EditDepartmentSmallGroupsCommandState =>

	override def validate(errors: Errors) {
		groupNames.asScala.zipWithIndex.foreach { case (name, index) =>
			if (!name.hasText) errors.rejectValue(s"groupNames[${index}]", "smallGroup.name.NotEmpty")
			else if (name.orEmpty.length > 200) errors.rejectValue(s"groupNames[${index}]", "smallGroup.name.Length", Array[Object](200: JInteger), "")
		}

		if (groupNames.size() < set.groups.size()) {
			for (i <- set.groups.size() to groupNames.size()) {
				val group = set.groups.get(i - 1)

				if (!group.students.isEmpty) {
					errors.rejectValue(s"groupNames[${i - 1}]", "smallGroup.delete.notEmpty")
				}
			}
		}
	}
}