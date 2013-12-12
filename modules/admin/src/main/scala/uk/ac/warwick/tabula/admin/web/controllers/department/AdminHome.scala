package uk.ac.warwick.tabula.admin.web.controllers.department

import scala.collection.JavaConverters._
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import uk.ac.warwick.spring.Wire
import uk.ac.warwick.tabula.CurrentUser
import uk.ac.warwick.tabula.PermissionDeniedException
import uk.ac.warwick.tabula.admin.web.Routes
import uk.ac.warwick.tabula.admin.web.controllers.AdminController
import uk.ac.warwick.tabula.commands.Command
import uk.ac.warwick.tabula.commands.ReadOnly
import uk.ac.warwick.tabula.commands.Unaudited
import uk.ac.warwick.tabula.data.model.Department
import uk.ac.warwick.tabula.data.model.Module
import uk.ac.warwick.tabula.permissions.Permissions
import uk.ac.warwick.tabula.services.ModuleAndDepartmentService
import uk.ac.warwick.tabula.services.SecurityService
import uk.ac.warwick.tabula.data.model.Route


/**
 * Screens for department and module admins.
 */
@Controller
@RequestMapping(Array("/department"))
class AdminHomeController extends AdminController {
	@RequestMapping(method=Array(GET, HEAD))
	def homeScreen(user: CurrentUser) = Redirect(Routes.home)
}

@Controller
@RequestMapping(value=Array("/department/{department}"))
class AdminDepartmentHomeController extends AdminController {

	hideDeletedItems
	
	@ModelAttribute def command(@PathVariable("department") dept: Department, user: CurrentUser) =
		new AdminDepartmentHomeCommand(dept, user)
	
	@RequestMapping(method=Array(GET, HEAD))
	def adminDepartment(cmd: AdminDepartmentHomeCommand) = {
		val (modules, routes) = cmd.apply()
		
		Mav("admin/department",
			"department" -> cmd.department,
			"modules" -> modules,
			"departmentRoutes" -> routes) // Stupid workaround for "routes" being used already in Freemarker
	}
}

class AdminDepartmentHomeCommand(val department: Department, val user: CurrentUser) extends Command[(Seq[Module], Seq[Route])] with ReadOnly with Unaudited {
	
	var securityService = Wire[SecurityService]
	var moduleService = Wire[ModuleAndDepartmentService]
	
	val modules: Seq[Module] = 
		if (securityService.can(user, Permissions.Module.Administer, mandatory(department))) {
			// This may seem silly because it's rehashing the above; but it avoids an assertion error where we don't have any explicit permission definitions
			PermissionCheck(Permissions.Module.Administer, department)
			
			department.modules.asScala
		} else {
			val managedModules = moduleService.modulesWithPermission(user, Permissions.Module.Administer, department).toList
			
			// This is implied by the above, but it's nice to check anyway
			PermissionCheckAll(Permissions.Module.Administer, managedModules)
			
			if (managedModules.isEmpty)
				throw new PermissionDeniedException(user, Permissions.Module.Administer, department)
			
			managedModules
		}
	
	val routes: Seq[Route] = 
		if (securityService.can(user, Permissions.Route.Administer, mandatory(department))) {
			// This may seem silly because it's rehashing the above; but it avoids an assertion error where we don't have any explicit permission definitions
			PermissionCheck(Permissions.Route.Administer, department)
			
			department.routes.asScala
		} else {
			val managedRoutes = moduleService.routesWithPermission(user, Permissions.Route.Administer, department).toList
			
			// This is implied by the above, but it's nice to check anyway
			PermissionCheckAll(Permissions.Route.Administer, managedRoutes)
			
			if (managedRoutes.isEmpty)
				throw new PermissionDeniedException(user, Permissions.Route.Administer, department)
			
			managedRoutes
		}
	
	def applyInternal() = (modules.sorted, routes.sorted)
		
}