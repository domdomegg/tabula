package uk.ac.warwick.tabula.web.controllers.admin.permissions

import org.joda.time.DateTime
import org.springframework.web.bind.annotation.{RequestParam, PathVariable, ModelAttribute, RequestMapping}
import org.springframework.stereotype.Controller
import uk.ac.warwick.tabula.permissions.{SelectorPermission, Permission, Permissions, PermissionsSelector, PermissionsTarget}
import uk.ac.warwick.tabula.commands.{SelfValidating, Appliable}
import uk.ac.warwick.tabula.data.model.permissions.{GrantedPermission, GrantedRole}
import uk.ac.warwick.tabula.commands.permissions._
import uk.ac.warwick.tabula.data.model.{StudentRelationshipType, Assignment, Route, Module, Department, Member}
import uk.ac.warwick.tabula.web.Mav
import uk.ac.warwick.tabula.web.controllers.admin.AdminController
import uk.ac.warwick.tabula.roles.{SelectorBuiltInRoleDefinition, RoleBuilder, RoleDefinition}
import uk.ac.warwick.spring.Wire
import uk.ac.warwick.tabula.services.UserLookupService
import scala.collection.JavaConverters._
import javax.validation.Valid
import org.springframework.validation.Errors
import scala.reflect.ClassTag
import uk.ac.warwick.tabula.services.permissions.PermissionsService
import uk.ac.warwick.tabula.data.model.groups.{SmallGroupSet, SmallGroupEvent, SmallGroup}
import uk.ac.warwick.tabula.{AcademicYear, CurrentUser}
import uk.ac.warwick.tabula.helpers.ReflectionHelper
import uk.ac.warwick.tabula.data.Transactions._
import scala.collection.immutable.SortedMap
import uk.ac.warwick.tabula.web.Routes

abstract class PermissionsControllerMethods[A <: PermissionsTarget : ClassTag] extends AdminController {

	validatesSelf[SelfValidating]

	type GrantRoleCommand = Appliable[GrantedRole[A]] with GrantRoleCommandState[A]
	type RevokeRoleCommand = Appliable[GrantedRole[A]] with RevokeRoleCommandState[A]

	type GrantPermissionsCommand = Appliable[GrantedPermission[A]] with GrantPermissionsCommandState[A]
	type RevokePermissionsCommand = Appliable[GrantedPermission[A]] with RevokePermissionsCommandState[A]

	@ModelAttribute("addCommand") def addCommandModel(@PathVariable("target") target: A): GrantRoleCommand = GrantRoleCommand(target)
	@ModelAttribute("removeCommand") def removeCommandModel(@PathVariable("target") target: A): RevokeRoleCommand = RevokeRoleCommand(target)

	@ModelAttribute("addSingleCommand") def addSingleCommandModel(@PathVariable("target") target: A): GrantPermissionsCommand = GrantPermissionsCommand(target)
	@ModelAttribute("removeSingleCommand") def removeSingleCommandModel(@PathVariable("target") target: A): RevokePermissionsCommand = RevokePermissionsCommand(target)

	var userLookup = Wire[UserLookupService]
	var permissionsService = Wire[PermissionsService]

	def form(target: A): Mav = {
		Mav("admin/permissions/permissions",
			"target" -> target,
			"existingRoleDefinitions" -> existingRoleDefinitions(target),
			"grantableRoleDefinitions" -> grantableRoleDefinitions(target, user),
			"existingPermissions" -> existingPermissions(target)
		).crumbs(Breadcrumbs.Permissions(target))
	}

	def form(target: A, usercodes: Seq[String], role: Option[RoleDefinition], action: String): Mav = {
		val users = userLookup.getUsersByUserIds(usercodes.asJava).asScala
		Mav("admin/permissions/permissions",
			"target" -> target,
			"users" -> users,
			"role" -> role,
			"action" -> action,
		  "existingRoleDefinitions" -> existingRoleDefinitions(target),
			"grantableRoleDefinitions" -> grantableRoleDefinitions(target, user),
			"existingPermissions" -> existingPermissions(target)
		).crumbs(Breadcrumbs.Permissions(target))
	}

	@RequestMapping
	def permissionsForm(@PathVariable("target") target: A, @RequestParam(defaultValue="") usercodes: Array[String],
											@RequestParam(value="role", required=false) role: RoleDefinition, @RequestParam(value="action", required=false) action: String): Mav =
		form(target, usercodes, Some(role), action)

	@RequestMapping(method = Array(POST), params = Array("_command=add"))
	def addRole(@Valid @ModelAttribute("addCommand") command: GrantRoleCommand, errors: Errors) : Mav =  {
		val target = command.scope
		if (errors.hasErrors()) {
			form(target)
		} else {
			val role = Some(command.apply().roleDefinition)
			val userCodes = command.usercodes.asScala
			form(target, userCodes, role, "add")
		}
	}

	@RequestMapping(method = Array(POST), params = Array("_command=remove"))
	def removeRole(@Valid @ModelAttribute("removeCommand") command: RevokeRoleCommand,
											 errors: Errors): Mav = {
		val target = command.scope
		if (errors.hasErrors()) {
			form(target)
		} else {
			val role = Some(command.apply().roleDefinition)
			val userCodes = command.usercodes.asScala
			form(target, userCodes, role, "remove")
		}
	}

	@RequestMapping(method = Array(POST), params = Array("_command=addSingle"))
	def addPermission(@Valid @ModelAttribute("addSingleCommand") command: GrantPermissionsCommand, errors: Errors) : Mav =  {
		val target = command.scope
		if (errors.hasErrors()) {
			form(target)
		} else {
			val grantedPermission = Some(command.apply())
			val userCodes = command.usercodes.asScala
			form(target, userCodes, None, "add")
		}
	}

	@RequestMapping(method = Array(POST), params = Array("_command=removeSingle"))
	def removePermission(@Valid @ModelAttribute("removeSingleCommand") command: RevokePermissionsCommand,
											 errors: Errors): Mav = {
		val target = command.scope
		if (errors.hasErrors()) {
			form(target)
		} else {
			val grantedPermission = Some(command.apply())
			val userCodes = command.usercodes.asScala
			form(target, userCodes, None, "remove")
		}
	}

	implicit val defaultOrderingForRoleDefinition = Ordering.by[RoleDefinition, String] ( _.getName )

//	@ModelAttribute("existingRoleDefinitions") // Not a ModelAttribute because this changes after a change
	def existingRoleDefinitions(@PathVariable("target") target: A) = {
		SortedMap(
			permissionsService.getAllGrantedRolesFor(target)
				.groupBy { _.roleDefinition }
				.filterKeys { _.isAssignable }
				.map { case (defn, roles) => defn -> roles.head.build() }
				.toSeq:_*
		)
	}

	private def parentDepartments[B <: PermissionsTarget](permissionsTarget: B): Seq[Department] = permissionsTarget match {
		case department: Department => Seq(department)
		case _ => permissionsTarget.permissionsParents.flatMap(parentDepartments)
	}

//	@ModelAttribute("grantableRoleDefinitions") // Not a ModelAttribute because this changes after a change
	def grantableRoleDefinitions(@PathVariable("target") target: A, user: CurrentUser) = transactional(readOnly = true) {
		val builtInRoleDefinitions = ReflectionHelper.allBuiltInRoleDefinitions

		val allDepartments = parentDepartments(target)

		val relationshipTypes =
			allDepartments
				.flatMap { _.displayedStudentRelationshipTypes }
				.distinct

		val selectorBuiltInRoleDefinitions =
			ReflectionHelper.allSelectorBuiltInRoleDefinitionNames.flatMap { name =>
				SelectorBuiltInRoleDefinition.of(name, PermissionsSelector.Any[StudentRelationshipType]) +:
					relationshipTypes.map { relationshipType =>
						SelectorBuiltInRoleDefinition.of(name, relationshipType)
					}
			}

		val customRoleDefinitions =
			allDepartments
				.flatMap { department => permissionsService.getCustomRoleDefinitionsFor(department) }
				.filterNot { _.replacesBaseDefinition }

		val allDefinitions = (builtInRoleDefinitions ++ selectorBuiltInRoleDefinitions ++ customRoleDefinitions).filter { roleDefinition =>
			roleDefinition.isAssignable &&
			roleDefinition.allPermissions(Some(target)).keys.filterNot(securityService.canDelegate(user,_,target)).isEmpty
		}

		SortedMap(
			allDefinitions
				.filterNot { defn => existingRoleDefinitions(target).contains(defn) }
				.map { defn => defn -> RoleBuilder.build(defn, Some(target), defn.getName) }
				.toSeq:_*
		)
	}

//	@ModelAttribute("existingPermissions") // Not a ModelAttribute because this changes after a change
	def existingPermissions(@PathVariable("target") target: A) = {
		permissionsService.getAllGrantedPermissionsFor(target)
	}

	@ModelAttribute("allPermissions") def allPermissions(@PathVariable("target") target: A) = {
		def groupFn(p: Permission) = {
			val simpleName = Permissions.shortName(p.getClass)

			val parentName =
				if (simpleName.indexOf('.') == -1) ""
				else simpleName.substring(0, simpleName.lastIndexOf('.'))

			parentName
		}

		val allDepartments = parentDepartments(target)

		val relationshipTypes =
			allDepartments
				.flatMap { _.displayedStudentRelationshipTypes }
				.distinct

		ReflectionHelper.allPermissions
			.filter { p => groupFn(p).hasText }
			.sortBy { p => groupFn(p) }
			.flatMap { p =>
				if (p.isInstanceOf[SelectorPermission[_]]) {
					p +: relationshipTypes.map { relationshipType =>
						SelectorPermission.of(p.getName, relationshipType)
					}
				} else {
					Seq(p)
				}
			}
			.groupBy(groupFn)
			.map { case (key, value) => (key, value.map { p =>
				val name = p match {
					case p: SelectorPermission[_] => p.toString()
					case _ => p.getName
				}

				(name, name)
			})}
	}

}

case class AdminLink(title: String, href: String)

@Controller @RequestMapping(value = Array("/admin/permissions/member/{target}"))
class MemberPermissionsController extends PermissionsControllerMethods[Member] {
	@ModelAttribute("adminLinks") def adminLinks(@PathVariable("target") member: Member) = Seq(
		AdminLink("View profile", Routes.profiles.profile.view(member))
	)
}

@Controller @RequestMapping(value = Array("/admin/permissions/department/{target}"))
class DepartmentPermissionsController extends PermissionsControllerMethods[Department] {
	@ModelAttribute("adminLinks") def adminLinks(@PathVariable("target") department: Department) = Seq(
		AdminLink("Coursework Management", Routes.coursework.admin.department(department)),
		AdminLink("Small Group Teaching", Routes.groups.admin(department, AcademicYear.guessSITSAcademicYearByDate(DateTime.now))),
		AdminLink("Monitoring Points - View and record", Routes.attendance.View.department(department)),
		AdminLink("Monitoring Points - Create and edit", Routes.attendance.Manage.department(department)),
		AdminLink("Administration & Permissions", Routes.admin.department(department))
	)
}

@Controller @RequestMapping(value = Array("/admin/permissions/module/{target}"))
class ModulePermissionsController extends PermissionsControllerMethods[Module] {
	@ModelAttribute("adminLinks") def adminLinks(@PathVariable("target") module: Module) = Seq(
		AdminLink("Coursework Management", Routes.coursework.admin.module(module)),
		AdminLink("Small Group Teaching", Routes.groups.admin(module.adminDepartment, AcademicYear.guessSITSAcademicYearByDate(DateTime.now))),
		AdminLink("Administration & Permissions", Routes.admin.module(module))
	)
}

@Controller @RequestMapping(value = Array("/admin/permissions/route/{target}"))
class RoutePermissionsController extends PermissionsControllerMethods[Route] {
	@ModelAttribute("adminLinks") def adminLinks(@PathVariable("target") route: Route) = Seq(
		AdminLink("Administration & Permissions", Routes.admin.route(route))
	)
}

@Controller @RequestMapping(value = Array("/admin/permissions/assignment/{target}"))
class AssignmentPermissionsController extends PermissionsControllerMethods[Assignment] {
	@ModelAttribute("adminLinks") def adminLinks(@PathVariable("target") assignment: Assignment) = Seq(
		AdminLink("Manage", Routes.coursework.admin.module(assignment.module))
	)
}

@Controller @RequestMapping(value = Array("/admin/permissions/smallgroupset/{target}"))
class SmallGroupSetPermissionsController extends PermissionsControllerMethods[SmallGroupSet] {
	@ModelAttribute("adminLinks") def adminLinks(@PathVariable("target") set: SmallGroupSet) = Seq(
		AdminLink("Manage", Routes.groups.admin.module(set.module, set.academicYear))
	)
}

@Controller @RequestMapping(value = Array("/admin/permissions/smallgroup/{target}"))
class SmallGroupPermissionsController extends PermissionsControllerMethods[SmallGroup]

@Controller @RequestMapping(value = Array("/admin/permissions/smallgroupevent/{target}"))
class SmallGroupEventPermissionsController extends PermissionsControllerMethods[SmallGroupEvent]