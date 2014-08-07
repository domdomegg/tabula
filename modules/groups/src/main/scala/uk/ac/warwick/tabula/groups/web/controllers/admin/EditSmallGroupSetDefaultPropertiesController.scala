package uk.ac.warwick.tabula.groups.web.controllers.admin

import javax.validation.Valid
import org.springframework.stereotype.Controller
import org.springframework.validation.Errors
import org.springframework.web.bind.annotation.{ModelAttribute, PathVariable, RequestMapping}
import uk.ac.warwick.tabula.commands.Appliable
import uk.ac.warwick.tabula.data.model.Module
import uk.ac.warwick.tabula.data.model.groups.SmallGroupSet
import uk.ac.warwick.tabula.groups.commands.admin.EditSmallGroupSetDefaultPropertiesCommand
import uk.ac.warwick.tabula.groups.web.Routes
import uk.ac.warwick.tabula.services.AutowiringTermServiceComponent

abstract class AbstractEditSmallGroupSetDefaultPropertiesController extends SmallGroupEventsController with AutowiringTermServiceComponent {

	type EditSmallGroupSetDefaultPropertiesCommand = Appliable[SmallGroupSet]

	@ModelAttribute("command") def command(@PathVariable("module") module: Module, @PathVariable("smallGroupSet") set: SmallGroupSet): EditSmallGroupSetDefaultPropertiesCommand =
		EditSmallGroupSetDefaultPropertiesCommand(module, set)

	protected def render(set: SmallGroupSet) = {
		Mav("admin/groups/events/defaults", "cancelUrl" -> postSaveRoute(set))
			.crumbs(Breadcrumbs.Department(set.module.department), Breadcrumbs.Module(set.module))
	}

	protected def postSaveRoute(set: SmallGroupSet): String

	@RequestMapping
	def form(
		@PathVariable("smallGroupSet") set: SmallGroupSet,
		@ModelAttribute("command") cmd: EditSmallGroupSetDefaultPropertiesCommand
	) = render(set)

	protected def submit(cmd: EditSmallGroupSetDefaultPropertiesCommand, errors: Errors, set: SmallGroupSet, route: String) = {
		if (errors.hasErrors) {
			render(set)
		} else {
			cmd.apply()
			RedirectForce(route)
		}
	}

	@RequestMapping(method = Array(POST))
	def save(
		@Valid @ModelAttribute("command") cmd: EditSmallGroupSetDefaultPropertiesCommand,
		errors: Errors,
		@PathVariable("smallGroupSet") set: SmallGroupSet
	) = submit(cmd, errors, set, postSaveRoute(set))

}

@RequestMapping(Array("/admin/module/{module}/groups/new/{smallGroupSet}/events/defaults"))
@Controller
class CreateSmallGroupSetEditDefaultPropertiesController extends AbstractEditSmallGroupSetDefaultPropertiesController {
	override def postSaveRoute(set: SmallGroupSet) = Routes.admin.createAddEvents(set)
}

@RequestMapping(Array("/admin/module/{module}/groups/edit/{smallGroupSet}/events/defaults"))
@Controller
class EditSmallGroupSetEditDefaultPropertiesController extends AbstractEditSmallGroupSetDefaultPropertiesController {
	override def postSaveRoute(set: SmallGroupSet) = Routes.admin.editAddEvents(set)
}
