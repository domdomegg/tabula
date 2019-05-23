package uk.ac.warwick.tabula.data.model.permissions

import javax.persistence._
import org.hibernate.annotations.{Any, AnyMetaDef, MetaValue, Type}
import uk.ac.warwick.tabula.data.PostLoadBehaviour
import uk.ac.warwick.tabula.data.model._
import uk.ac.warwick.tabula.data.model.groups.{SmallGroup, SmallGroupEvent, SmallGroupSet}
import uk.ac.warwick.tabula.data.model.mitcircs.MitigatingCircumstancesPanel
import uk.ac.warwick.tabula.permissions.{Permission, PermissionsTarget}
import uk.ac.warwick.tabula.roles.RoleBuilder.GeneratedRole
import uk.ac.warwick.tabula.roles.{BuiltInRoleDefinition, Role, RoleBuilder, RoleDefinition}

import scala.reflect._

@Entity
@Access(AccessType.FIELD)
class GrantedRole[A <: PermissionsTarget] extends GeneratedId with HibernateVersioned with PostLoadBehaviour {

  @OneToOne(cascade = Array(CascadeType.ALL), fetch = FetchType.EAGER)
  @JoinColumn(name = "usergroup_id")
  private var _users: UserGroup = UserGroup.ofUsercodes

  def users: UnspecifiedTypeUserGroup = _users

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "custom_role_id")
  var customRoleDefinition: CustomRoleDefinition = _

  @Type(`type` = "uk.ac.warwick.tabula.data.model.permissions.BuiltInRoleDefinitionUserType")
  var builtInRoleDefinition: BuiltInRoleDefinition = _

  def roleDefinition: RoleDefinition = Option(customRoleDefinition) getOrElse builtInRoleDefinition

  def roleDefinition_=(definition: RoleDefinition): Unit = definition match {
    case customDefinition: CustomRoleDefinition =>
      customRoleDefinition = customDefinition
      builtInRoleDefinition = null
    case builtInDefinition: BuiltInRoleDefinition =>
      customRoleDefinition = null
      builtInRoleDefinition = builtInDefinition
    case _ =>
      customRoleDefinition = null
      builtInRoleDefinition = null
  }

  /**
    * The scope of the GrantedRole is what the permissions contained within are granted against,
    * which is a PermissionsTarget.
    */
  @Any(metaColumn = new Column(name = "scope_type"), fetch = FetchType.LAZY)
  @AnyMetaDef(idType = "string", metaType = "string",
    metaValues = Array(
      new MetaValue(targetEntity = classOf[Department], value = "Department"),
      new MetaValue(targetEntity = classOf[Module], value = "Module"),
      new MetaValue(targetEntity = classOf[Route], value = "Route"),
      new MetaValue(targetEntity = classOf[Member], value = "Member"),
      new MetaValue(targetEntity = classOf[Assignment], value = "Assignment"),
      new MetaValue(targetEntity = classOf[SmallGroup], value = "SmallGroup"),
      new MetaValue(targetEntity = classOf[SmallGroupSet], value = "SmallGroupSet"),
      new MetaValue(targetEntity = classOf[SmallGroupEvent], value = "SmallGroupEvent"),
      new MetaValue(targetEntity = classOf[MitigatingCircumstancesPanel], value = "MitigatingCircumstancesPanel"),
    )
  )
  @JoinColumn(name = "scope_id")
  @ForeignKey(name = "none")
  var scope: A = _

  @Column(name = "scope_type", insertable = false, updatable = false)
  var scopeType: String = _

  /**
    * Build a Role from this definition
    */
  def build(): GeneratedRole =
    if (scopeType == "___GLOBAL___")
      RoleBuilder.build(GlobalRoleDefinition(replaceableRoleDefinition), None, replaceableRoleDefinition.getName)
    else
      RoleBuilder.build(replaceableRoleDefinition, Option(scope), replaceableRoleDefinition.getName)

  def mayGrant(target: Permission): Boolean = Option(replaceableRoleDefinition).fold(false) {
    _.mayGrant(target)
  }

  /**
    * Provides a route to Department from the scope, so that we can look for custom definitions.
    *
    * In almost all cases, Department will be one of the permissionsParents of the scope (maybe multiple
    * levels up), but providing a direct link here means we don't have to iterate up the tree.
    */
  def scopeDepartment: Option[Department] = scope match {
    case null => None
    case department: Department => Some(department)
    case module: Module => Some(module.adminDepartment)
    case route: Route => Some(route.adminDepartment)
    case student: StudentMember =>
      student.mostSignificantCourseDetails.flatMap(_.latestStudentCourseYearDetails.enrolmentDepartment.subDepartmentsContaining(student).lastOption).orElse(Option(student.homeDepartment).flatMap(_.subDepartmentsContaining(student).lastOption))
    case member: Member => Option(member.homeDepartment)
    case assignment: Assignment => Some(assignment.module.adminDepartment)
    case smallGroup: SmallGroup => Some(smallGroup.groupSet.module.adminDepartment)
    case smallGroupSet: SmallGroupSet => Some(smallGroupSet.module.adminDepartment)
    case smallGroupEvent: SmallGroupEvent => Some(smallGroupEvent.group.groupSet.module.adminDepartment)
    case _ => None
  }

  def replaceableRoleDefinition: RoleDefinition = scopeDepartment.flatMap(_.replacedRoleDefinitionFor(roleDefinition)).getOrElse(roleDefinition)

  // If hibernate sets users to null, make a new empty usergroup
  override def postLoad() {
    ensureUsers
  }

  def ensureUsers: UnspecifiedTypeUserGroup = {
    if (users == null) _users = UserGroup.ofUsercodes
    users
  }

}

object GrantedRole {
  def apply[A <: PermissionsTarget : ClassTag](scope: A, definition: RoleDefinition): GrantedRole[A] =
    if (canDefineFor[A]) {
      val r = new GrantedRole[A]
      r.scope = scope
      r.roleDefinition = definition
      r
    } else {
      throw new IllegalArgumentException(s"Cannot define new roles for $scope")
    }

  def canDefineFor[A <: PermissionsTarget : ClassTag]: Boolean = classTag[A] == classTag[PermissionsTarget] || scopeType[A].nonEmpty

  def scopeType[A <: PermissionsTarget : ClassTag]: Option[String] = classTag[A] match {
    case t if isSubtype(t, classTag[Department]) => Some("Department")
    case t if isSubtype(t, classTag[Module]) => Some("Module")
    case t if isSubtype(t, classTag[Route]) => Some("Route")
    case t if isSubtype(t, classTag[Member]) => Some("Member")
    case t if isSubtype(t, classTag[Assignment]) => Some("Assignment")
    case t if isSubtype(t, classTag[SmallGroup]) => Some("SmallGroup")
    case t if isSubtype(t, classTag[SmallGroupSet]) => Some("SmallGroupSet")
    case t if isSubtype(t, classTag[SmallGroupEvent]) => Some("SmallGroupEvent")
    case t if isSubtype(t, classTag[MitigatingCircumstancesPanel]) => Some("MitigatingCircumstancesPanel")
    case _ => None
  }

  private def isSubtype[A, B](self: ClassTag[A], other: ClassTag[B]) = other.runtimeClass.isAssignableFrom(self.runtimeClass)
}

/**
  * Wrap a normal RoleDefinition to allow us to make permissions that aren't allowed to be global, global.
  */
case class GlobalRoleDefinition(delegate: RoleDefinition) extends RoleDefinition {
  def permissions(scope: Option[PermissionsTarget]): Map[Permission, Option[PermissionsTarget]] =
    delegate.permissions(Some(null)).map {
      case (perm, Some(null)) => (perm, None)
      case (perm, s) => (perm, s)
    }

  def subRoles(scope: Option[PermissionsTarget]): Set[Role] = delegate.subRoles(scope)

  def mayGrant(permission: Permission): Boolean = delegate.mayGrant(permission)

  def allPermissions(scope: Option[PermissionsTarget]): Map[Permission, Option[PermissionsTarget]] = delegate.allPermissions(scope)

  def canDelegateThisRolesPermissions: _root_.uk.ac.warwick.tabula.JavaImports.JBoolean = delegate.canDelegateThisRolesPermissions

  def getName: String = delegate.getName

  def description: String = delegate.description

  def isAssignable: Boolean = delegate.isAssignable
}
