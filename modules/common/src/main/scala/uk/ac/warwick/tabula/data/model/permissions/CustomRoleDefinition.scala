package uk.ac.warwick.tabula.data.model.permissions

import scala.collection.JavaConversions._
import org.hibernate.annotations.Type
import javax.persistence._
import uk.ac.warwick.tabula.JavaImports._
import uk.ac.warwick.tabula.data.model.Department
import uk.ac.warwick.tabula.data.model.GeneratedId
import uk.ac.warwick.tabula.permissions.PermissionsTarget
import uk.ac.warwick.tabula.roles.BuiltInRoleDefinition
import uk.ac.warwick.tabula.roles.RoleDefinition
import scala.collection.immutable.ListMap
import uk.ac.warwick.tabula.data.model.HibernateVersioned
import uk.ac.warwick.tabula.permissions.Permission

@Entity
class CustomRoleDefinition extends RoleDefinition with HibernateVersioned with GeneratedId with PermissionsTarget {

	// The department which owns this definition - probably want to expand this to include sub-departments later
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "department_id")
	var department: Department = _

	var name: String = _

	// Role uses getName. Could change it to name.
	def getName = name

	// The role definition that this role infers from; can be a built in role definition
	// or a custom role definition
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "custom_base_role_id")
	var customBaseRoleDefinition: CustomRoleDefinition = _

	@OneToMany(mappedBy="customBaseRoleDefinition", fetch = FetchType.LAZY, cascade = Array(CascadeType.ALL), orphanRemoval = true)
	var subDefinitions:JList[CustomRoleDefinition] = List()

	@Type(`type` = "uk.ac.warwick.tabula.data.model.permissions.BuiltInRoleDefinitionUserType")
	var builtInBaseRoleDefinition: BuiltInRoleDefinition = _

	def baseRoleDefinition: RoleDefinition = Option(customBaseRoleDefinition) getOrElse builtInBaseRoleDefinition
	def baseRoleDefinition_=(definition: RoleDefinition) = definition match {
		case customDefinition: CustomRoleDefinition => {
			customBaseRoleDefinition = customDefinition
			builtInBaseRoleDefinition = null
		}
		case builtInDefinition: BuiltInRoleDefinition => {
			customBaseRoleDefinition = null
			builtInBaseRoleDefinition = builtInDefinition
		}
		case _ => {
			customBaseRoleDefinition = null
			builtInBaseRoleDefinition = null
		}
	}

	// A set of role overrides
	@OneToMany(mappedBy="customRoleDefinition", fetch = FetchType.LAZY, cascade = Array(CascadeType.ALL), orphanRemoval = true)
	var overrides:JList[RoleOverride] = JArrayList()

	def permissionsParents =
		Seq(Option(department)).flatten

	/**
	 * This method eagerly resolves sub-roles, which is why we return
	 * an empty set of actual sub-roles. It has to resolve now so that
	 * we can do the removal accurately - otherwise we won't be able to
	 * remove permissions added in sub-roles.
	 */
	def permissions(scope: Option[PermissionsTarget]) = {
		val basePermissions = baseRoleDefinition.allPermissions(scope)

		val (additionOverrides, removalOverrides) = overrides.partition(_.overrideType)
		val additions = additionOverrides.map { _.permission -> scope }
		val removals = removalOverrides.map { _.permission }

		(basePermissions ++ additions) -- removals
	}

	def subRoles(scope: Option[PermissionsTarget]) = Set()

	/**
	 * Return all permissions, resolving sub-roles. This is the behaviour of permissions() anyway
	 */
	def allPermissions(scope: Option[PermissionsTarget]): Map[Permission, Option[PermissionsTarget]] =
		permissions(scope)
		
	def mayGrant(target: Permission) =
		baseRoleDefinition.mayGrant(target) ||
		(overrides exists { o => o.overrideType == RoleOverride.Allow && o.permission == target })

}