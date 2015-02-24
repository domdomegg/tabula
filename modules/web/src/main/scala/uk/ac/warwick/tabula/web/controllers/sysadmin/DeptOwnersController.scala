package uk.ac.warwick.tabula.web.controllers.sysadmin

import scala.collection.JavaConverters._
import org.springframework.stereotype.Controller
import org.springframework.validation.Errors
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import javax.validation.Valid
import uk.ac.warwick.tabula.commands.permissions.{RevokeRoleCommandState, GrantRoleCommandState, GrantRoleCommand, RevokeRoleCommand}
import uk.ac.warwick.tabula.data.model.Department
import uk.ac.warwick.tabula.roles.{RoleDefinition, DepartmentalAdministratorRoleDefinition}
import uk.ac.warwick.tabula.web.Mav
import uk.ac.warwick.tabula.commands.{SelfValidating, Appliable}
import uk.ac.warwick.tabula.data.model.permissions.GrantedRole

@Controller
@RequestMapping(Array("/sysadmin/departments/"))
class SysadminDeptDetailsController extends BaseSysadminController {

	@RequestMapping
	def departments = Mav("sysadmin/departments/list",
		"departments" -> moduleService.allDepartments.sortBy{ _.name })
		.crumbs(Breadcrumbs.Current("Sysadmin department list"))

	@RequestMapping(Array("/{dept}/"))
	def department(@PathVariable("dept") dept: Department) = {
		mandatory(dept)
		Mav("sysadmin/departments/single",
			"department" -> dept)
			.crumbs(Breadcrumbs.Current(s"Sysadmin ${dept.name} overview"))
	}
}

trait DepartmentPermissionControllerMethods extends BaseSysadminController {

	type GrantRoleCommand = Appliable[GrantedRole[Department]] with GrantRoleCommandState[Department]
	type RevokeRoleCommand = Appliable[GrantedRole[Department]] with RevokeRoleCommandState[Department]

	@ModelAttribute("addCommand") def addCommandModel(@PathVariable("department") department: Department): GrantRoleCommand =
		GrantRoleCommand(mandatory(department), DepartmentalAdministratorRoleDefinition)

	@ModelAttribute("removeCommand") def removeCommandModel(@PathVariable("department") department: Department) =
		RevokeRoleCommand(mandatory(department), DepartmentalAdministratorRoleDefinition)

	def form(@PathVariable("department") department: Department): Mav = {
		Mav("sysadmin/departments/permissions", "department" -> department)
			.crumbs(Breadcrumbs.Current(s"Sysadmin ${department.name} permissions"))
	}

	def form(department: Department, usercodes: Seq[String], role: Option[RoleDefinition], action: String): Mav = {
		val users = userLookup.getUsersByUserIds(usercodes.asJava)
		Mav("sysadmin/departments/permissions",
			"department" -> department,
			"users" -> users,
			"role" -> role,
			"action" -> action)
			.crumbs(Breadcrumbs.Current(s"Sysadmin ${department.name} permissions"))
	}
}

@Controller @RequestMapping(Array("/sysadmin/departments/{department}/permissions"))
class SysadminDepartmentPermissionController extends BaseSysadminController with DepartmentPermissionControllerMethods {
	@RequestMapping
	def permissionsForm(@PathVariable("department") department: Department): Mav =
		form(mandatory(department))
}

@Controller @RequestMapping(Array("/sysadmin/departments/{department}/permissions"))
class SysadminDepartmentAddPermissionController extends BaseSysadminController with DepartmentPermissionControllerMethods {

	validatesSelf[SelfValidating]

	@RequestMapping(method = Array(POST), params = Array("_command=add"))
	def addPermission(@Valid @ModelAttribute("addCommand") command: GrantRoleCommand, errors: Errors): Mav = {
		val department = command.scope
		if (errors.hasErrors()) {
			form(department)
		} else {
			val role = Some(command.apply().roleDefinition)
			val userCodes = command.usercodes.asScala
			form(department, userCodes, role, "add")
		}
	}
}

@Controller @RequestMapping(Array("/sysadmin/departments/{department}/permissions"))
class SysadminDepartmentRemovePermissionController extends BaseSysadminController with DepartmentPermissionControllerMethods {

	validatesSelf[SelfValidating]

	@RequestMapping(method = Array(POST), params = Array("_command=remove"))
	def addPermission(@Valid @ModelAttribute("removeCommand") command: RevokeRoleCommand, errors: Errors): Mav = {
		val department = command.scope
		if (errors.hasErrors()) {
			form(department)
		} else {
			val role = Some(command.apply().roleDefinition)
			val userCodes = command.usercodes.asScala
			form(department, userCodes, role, "remove")
		}
	}
}