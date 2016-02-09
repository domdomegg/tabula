package uk.ac.warwick.tabula.web.controllers.coursework.admin.modules

import scala.collection.JavaConverters._
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.{PathVariable, ModelAttribute, RequestMapping}
import uk.ac.warwick.tabula.data.model.{Assignment, Department, Module}
import uk.ac.warwick.tabula.CurrentUser
import org.springframework.validation.Errors
import uk.ac.warwick.tabula.coursework.web.Routes
import uk.ac.warwick.tabula.commands.coursework.assignments.CopyAssignmentsCommand
import uk.ac.warwick.tabula.web.controllers.coursework.CourseworkController

@Controller
@RequestMapping(value = Array("/coursework/admin/module/{module}/copy-assignments"))
class CopyModuleAssignmentsController extends CourseworkController with UnarchivedAssignmentsMap {

	@ModelAttribute
	def copyAssignmentsCommand(@PathVariable module: Module) = CopyAssignmentsCommand(module.adminDepartment, Seq(module))

	@RequestMapping(method = Array(HEAD, GET))
	def showForm(@PathVariable module: Module, cmd: CopyAssignmentsCommand) = {

		Mav("coursework/admin/modules/copy_assignments",
			"title" -> module.name,
			"cancel" -> Routes.admin.module(module),
			"map" -> moduleAssignmentMap(cmd.modules)
		)
	}

	@RequestMapping(method = Array(POST))
	def submit(cmd: CopyAssignmentsCommand, @PathVariable module: Module, errors: Errors, user: CurrentUser) = {
		cmd.apply()
		Redirect(Routes.admin.module(module))
	}

}

@Controller
@RequestMapping(value = Array("/coursework/admin/department/{department}/copy-assignments"))
class CopyDepartmentAssignmentsController extends CourseworkController with UnarchivedAssignmentsMap {

	@ModelAttribute
	def copyAssignmentsCommand(@PathVariable department: Department) = {
		val modules = department.modules.asScala.filter(_.assignments.asScala.exists(_.isAlive)).sortBy { _.code }
		CopyAssignmentsCommand(department, modules)
	}

	@RequestMapping(method = Array(HEAD, GET))
	def showForm(@PathVariable department: Department, cmd: CopyAssignmentsCommand) = {

		Mav("coursework/admin/modules/copy_assignments",
			"title" -> department.name,
			"cancel" -> Routes.admin.department(department),
			"map" -> moduleAssignmentMap(cmd.modules),
			"showSubHeadings" -> true
		)
	}

	@RequestMapping(method = Array(POST))
	def submit(cmd: CopyAssignmentsCommand, @PathVariable department: Department, errors: Errors, user: CurrentUser) = {
		cmd.apply()
		Redirect(Routes.admin.department(department))
	}

}

trait UnarchivedAssignmentsMap {

	def moduleAssignmentMap(modules: Seq[Module]): Map[String, Seq[Assignment]] = (
		for(module <- modules) yield module.code ->  module.assignments.asScala.filter { _.isAlive }
	).toMap.filterNot(_._2.isEmpty)

}
