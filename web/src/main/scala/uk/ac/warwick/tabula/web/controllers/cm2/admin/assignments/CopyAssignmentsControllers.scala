package uk.ac.warwick.tabula.web.controllers.cm2.admin.assignments

import org.joda.time.DateTime
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.{ModelAttribute, PathVariable, RequestMapping}
import uk.ac.warwick.tabula.AcademicYear
import uk.ac.warwick.tabula.JavaImports.JList
import uk.ac.warwick.tabula.cm2.web.Routes
import uk.ac.warwick.tabula.commands.cm2.assignments.CopyAssignmentsCommand
import uk.ac.warwick.tabula.data.model.{Assignment, Department, Module}
import uk.ac.warwick.tabula.permissions.Permission
import uk.ac.warwick.tabula.services.{AutowiringMaintenanceModeServiceComponent, AutowiringModuleAndDepartmentServiceComponent, AutowiringUserSettingsServiceComponent}
import uk.ac.warwick.tabula.web.Mav
import uk.ac.warwick.tabula.web.controllers.DepartmentScopedController
import uk.ac.warwick.tabula.web.controllers.cm2.CourseworkController

import scala.collection.JavaConverters._

abstract class AbstractCopyAssignmentsController extends CourseworkController {

	@ModelAttribute("academicYearChoices")
	def academicYearChoices: JList[AcademicYear] =
		AcademicYear.guessSITSAcademicYearByDate(DateTime.now).yearsSurrounding(0, 1).asJava

}


@Profile(Array("cm2Enabled"))
@Controller
@RequestMapping(value = Array("/${cm2.prefix}/admin/module/{module}/copy-assignments"))
class CopyModuleAssignmentsController extends AbstractCopyAssignmentsController with AliveAssignmentsMap {

	@ModelAttribute("copyAssignmentsCommand")
	def copyAssignmentsCommand(@PathVariable module: Module): CopyAssignmentsCommand.Command =
		CopyAssignmentsCommand(mandatory(module).adminDepartment, Seq(module))

	@RequestMapping
	def showForm(@PathVariable module: Module, @ModelAttribute("copyAssignmentsCommand") cmd: CopyAssignmentsCommand.Command): Mav = {
		Mav("cm2/admin/modules/copy_assignments",
			"title" -> module.name,
			"cancel" -> Routes.admin.module(module),
			"department" -> module.adminDepartment,
			"map" -> moduleAssignmentMap(cmd.modules))
			.crumbs(Breadcrumbs.Department(module.adminDepartment, cmd.academicYear))
	}

	@RequestMapping(method = Array(POST))
	def submit(@ModelAttribute("copyAssignmentsCommand") cmd: CopyAssignmentsCommand.Command, @PathVariable module: Module): Mav = {
		cmd.apply()
		Redirect(Routes.admin.module(module))
	}

}

@Profile(Array("cm2Enabled"))
@Controller
@RequestMapping(value = Array("/${cm2.prefix}/admin/department/{department}/copy-assignments"))
class CopyDepartmentAssignmentsController extends AbstractCopyAssignmentsController with AliveAssignmentsMap
	with AutowiringUserSettingsServiceComponent
	with AutowiringModuleAndDepartmentServiceComponent
	with AutowiringMaintenanceModeServiceComponent
	with DepartmentScopedController {

	override val departmentPermission: Permission = CopyAssignmentsCommand.AdminPermission

	@ModelAttribute("activeDepartment")
	override def activeDepartment(@PathVariable department: Department): Option[Department] = retrieveActiveDepartment(Option(department))

	@ModelAttribute("copyAssignmentsCommand")
	def copyAssignmentsCommand(@PathVariable department: Department): CopyAssignmentsCommand.Command = {
		val modules = department.modules.asScala.filter(_.assignments.asScala.exists(_.isAlive)).sortBy(_.code)
		CopyAssignmentsCommand(mandatory(department), modules)
	}

	@RequestMapping
	def showForm(@PathVariable department: Department, @ModelAttribute("copyAssignmentsCommand") cmd: CopyAssignmentsCommand.Command): Mav = {
		Mav("cm2/admin/modules/copy_assignments",
			"title" -> department.name,
			"cancel" -> Routes.admin.department(department),
			"map" -> moduleAssignmentMap(cmd.modules),
			"showSubHeadings" -> true)
			.crumbs(Breadcrumbs.Department(department, cmd.academicYear))
	}

	@RequestMapping(method = Array(POST))
	def submit(@ModelAttribute("copyAssignmentsCommand") cmd: CopyAssignmentsCommand.Command, @PathVariable department: Department): Mav = {
		cmd.apply()
		Redirect(Routes.admin.department(department))
	}

}

trait AliveAssignmentsMap {
	def moduleAssignmentMap(modules: Seq[Module]): Map[String, Seq[Assignment]] =
		modules.map { module => module.code -> module.assignments.asScala.filter(_.isAlive) }
			.toMap
			.filter { case (_, assignments) => assignments.nonEmpty }
}
