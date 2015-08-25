package uk.ac.warwick.tabula.web.controllers.groups.admin

import javax.validation.Valid

import org.springframework.stereotype.Controller
import org.springframework.validation.Errors
import org.springframework.web.bind.annotation.{RequestMapping, PathVariable, ModelAttribute}
import uk.ac.warwick.tabula.commands.Appliable
import uk.ac.warwick.tabula.commands.groups.admin.ImportSmallGroupEventsFromExternalSystemCommand
import uk.ac.warwick.tabula.data.model.Module
import uk.ac.warwick.tabula.data.model.groups.SmallGroupSet
import uk.ac.warwick.tabula.groups.web.Routes
import uk.ac.warwick.tabula.services.AutowiringTermServiceComponent
import scala.collection.JavaConverters._

abstract class AbstractImportSmallGroupEventsFromExternalSystemController extends SmallGroupEventsController with AutowiringTermServiceComponent {

	type ImportSmallGroupEventsFromExternalSystemCommand = Appliable[SmallGroupSet]

	@ModelAttribute("command") def command(@PathVariable("module") module: Module, @PathVariable("smallGroupSet") set: SmallGroupSet): ImportSmallGroupEventsFromExternalSystemCommand =
		ImportSmallGroupEventsFromExternalSystemCommand(module, set)

	protected def render(set: SmallGroupSet) = {
		Mav("groups/admin/groups/events/import", "cancelUrl" -> postSaveRoute(set))
			.crumbs(Breadcrumbs.DepartmentForYear(set.module.adminDepartment, set.academicYear), Breadcrumbs.ModuleForYear(set.module, set.academicYear))
	}

	@ModelAttribute("groups") def groups(@PathVariable("smallGroupSet") set: SmallGroupSet) =
		set.groups.asScala.sorted

	protected def postSaveRoute(set: SmallGroupSet): String

	@RequestMapping
	def form(
		@PathVariable("smallGroupSet") set: SmallGroupSet,
		@ModelAttribute("command") cmd: ImportSmallGroupEventsFromExternalSystemCommand
	) = render(set)

	protected def submit(cmd: ImportSmallGroupEventsFromExternalSystemCommand, errors: Errors, set: SmallGroupSet, route: String) = {
		if (errors.hasErrors) {
			render(set)
		} else {
			cmd.apply()
			RedirectForce(route)
		}
	}

	@RequestMapping(method = Array(POST))
	def save(
		@Valid @ModelAttribute("command") cmd: ImportSmallGroupEventsFromExternalSystemCommand,
		errors: Errors,
		@PathVariable("smallGroupSet") set: SmallGroupSet
	) = submit(cmd, errors, set, postSaveRoute(set))

}

@RequestMapping(Array("/groups/admin/module/{module}/groups/new/{smallGroupSet}/events/import"))
@Controller
class CreateImportSmallGroupEventsFromExternalSystemController extends AbstractImportSmallGroupEventsFromExternalSystemController {
	override def postSaveRoute(set: SmallGroupSet) = Routes.admin.createAddEvents(set)
}

@RequestMapping(Array("/groups/admin/module/{module}/groups/edit/{smallGroupSet}/events/import"))
@Controller
class EditImportSmallGroupEventsFromExternalSystemController extends AbstractImportSmallGroupEventsFromExternalSystemController {
	override def postSaveRoute(set: SmallGroupSet) = Routes.admin.editAddEvents(set)
}
