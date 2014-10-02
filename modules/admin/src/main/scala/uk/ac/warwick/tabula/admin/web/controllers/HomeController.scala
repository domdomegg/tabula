package uk.ac.warwick.tabula.admin.web.controllers
import org.springframework.stereotype.Controller

import uk.ac.warwick.spring.Wire
import uk.ac.warwick.tabula.CurrentUser
import uk.ac.warwick.tabula.permissions.Permissions
import uk.ac.warwick.tabula.services.{CourseAndRouteService, ModuleAndDepartmentService}

@Controller class HomeController extends AdminController {
	
	var moduleService = Wire[ModuleAndDepartmentService]
	var routeService = Wire[CourseAndRouteService]
	
	@RequestMapping(Array("/")) def home(user: CurrentUser) = {
		val ownedDepartments = 
			moduleService.departmentsWithPermission(user, Permissions.Module.Administer) ++
			moduleService.departmentsWithPermission(user, Permissions.Route.Administer)
		val ownedModules = moduleService.modulesWithPermission(user, Permissions.Module.Administer)
		val ownedRoutes = routeService.routesWithPermission(user, Permissions.Route.Administer)
		
		Mav("home/view",
			"ownedDepartments" -> ownedDepartments,
			"ownedModuleDepartments" -> ownedModules.map { _.adminDepartment },
			"ownedRouteDepartments" -> ownedRoutes.map { _.adminDepartment })
	}
	
}