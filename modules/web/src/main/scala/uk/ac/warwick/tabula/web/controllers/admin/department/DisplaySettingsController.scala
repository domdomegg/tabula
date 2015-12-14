package uk.ac.warwick.tabula.web.controllers.admin.department

import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.{ModelAttribute, RequestMapping, PathVariable}
import uk.ac.warwick.tabula.data.model.{CourseType, Department}
import org.springframework.validation.Errors
import javax.validation.Valid
import uk.ac.warwick.tabula.commands.admin.department.DisplaySettingsCommand
import uk.ac.warwick.tabula.permissions.{Permissions, Permission}
import uk.ac.warwick.tabula.web.Routes
import uk.ac.warwick.tabula.web.controllers.DepartmentScopedController
import uk.ac.warwick.tabula.web.controllers.admin.AdminController
import uk.ac.warwick.tabula.commands.{PopulateOnForm, SelfValidating, Appliable}
import uk.ac.warwick.tabula.services.{AutowiringMaintenanceModeServiceComponent, AutowiringModuleAndDepartmentServiceComponent, AutowiringUserSettingsServiceComponent, RelationshipService}
import uk.ac.warwick.spring.Wire

@Controller
@RequestMapping(Array("/admin/department/{department}/settings/display"))
class DisplaySettingsController extends AdminController
	with DepartmentScopedController with AutowiringUserSettingsServiceComponent with AutowiringModuleAndDepartmentServiceComponent
	with AutowiringMaintenanceModeServiceComponent {
	
	var relationshipService = Wire[RelationshipService]

	type DisplaySettingsCommand = Appliable[Department] with PopulateOnForm

	validatesSelf[SelfValidating]

	@ModelAttribute("displaySettingsCommand")
	def displaySettingsCommand(@PathVariable department: Department): DisplaySettingsCommand =
		DisplaySettingsCommand(mandatory(department))

	@ModelAttribute("allRelationshipTypes") def allRelationshipTypes = relationshipService.allStudentRelationshipTypes

	override val departmentPermission: Permission = Permissions.Department.ManageDisplaySettings

	@ModelAttribute("activeDepartment")
	override def activeDepartment(@PathVariable department: Department): Option[Department] = retrieveActiveDepartment(Option(department))

	@RequestMapping(method=Array(GET, HEAD))
	def initialView(@PathVariable department: Department, @ModelAttribute("displaySettingsCommand") cmd: DisplaySettingsCommand) = {
		cmd.populate()
		viewSettings(department)
	}

	private def viewSettings(department: Department) =
		Mav("admin/display-settings",
			"department" -> department,
			"expectedCourseTypes" -> Seq(CourseType.UG, CourseType.PGT, CourseType.PGR),
			"returnTo" -> getReturnTo("")
		).crumbs(
			Breadcrumbs.Department(department)
		)

	@RequestMapping(method=Array(POST))
	def saveSettings(
		@Valid @ModelAttribute("displaySettingsCommand") cmd: DisplaySettingsCommand,
		errors: Errors,
		@PathVariable department: Department
	) = {
		if (errors.hasErrors){
			viewSettings(department)
		} else {
			cmd.apply()
			Redirect(Routes.admin.department(department))
		}
	}
}