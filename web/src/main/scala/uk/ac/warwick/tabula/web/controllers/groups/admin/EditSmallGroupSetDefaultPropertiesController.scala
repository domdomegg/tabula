package uk.ac.warwick.tabula.web.controllers.groups.admin

import javax.validation.Valid

import org.springframework.stereotype.Controller
import org.springframework.validation.Errors
import org.springframework.web.bind.annotation.{ModelAttribute, PathVariable, RequestMapping}
import uk.ac.warwick.tabula.commands.Appliable
import uk.ac.warwick.tabula.commands.groups.admin.EditSmallGroupSetDefaultPropertiesCommand
import uk.ac.warwick.tabula.data.model.Module
import uk.ac.warwick.tabula.data.model.groups.SmallGroupSet
import uk.ac.warwick.tabula.groups.web.Routes
import uk.ac.warwick.tabula.web.Mav

abstract class AbstractEditSmallGroupSetDefaultPropertiesController extends SmallGroupEventsController {

  type EditSmallGroupSetDefaultPropertiesCommand = Appliable[SmallGroupSet]

  @ModelAttribute("command") def command(@PathVariable module: Module, @PathVariable("smallGroupSet") set: SmallGroupSet): EditSmallGroupSetDefaultPropertiesCommand =
    EditSmallGroupSetDefaultPropertiesCommand(module, set)

  protected def render(set: SmallGroupSet): Mav = {
    Mav("groups/admin/groups/events/defaults", "cancelUrl" -> postSaveRoute(set))
      .crumbs(Breadcrumbs.Department(set.module.adminDepartment, set.academicYear), Breadcrumbs.ModuleForYear(set.module, set.academicYear))
  }

  protected def postSaveRoute(set: SmallGroupSet): String

  @RequestMapping
  def form(
    @PathVariable("smallGroupSet") set: SmallGroupSet,
    @ModelAttribute("command") cmd: EditSmallGroupSetDefaultPropertiesCommand
  ): Mav = render(set)

  protected def submit(cmd: EditSmallGroupSetDefaultPropertiesCommand, errors: Errors, set: SmallGroupSet, route: String): Mav = {
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
  ): Mav = submit(cmd, errors, set, postSaveRoute(set))

}

@RequestMapping(Array("/groups/admin/module/{module}/groups/new/{smallGroupSet}/events/defaults"))
@Controller
class CreateSmallGroupSetEditDefaultPropertiesController extends AbstractEditSmallGroupSetDefaultPropertiesController {
  override def postSaveRoute(set: SmallGroupSet): String = Routes.admin.createAddEvents(set)
}

@RequestMapping(Array("/groups/admin/module/{module}/groups/edit/{smallGroupSet}/events/defaults"))
@Controller
class EditSmallGroupSetEditDefaultPropertiesController extends AbstractEditSmallGroupSetDefaultPropertiesController {
  override def postSaveRoute(set: SmallGroupSet): String = Routes.admin.editAddEvents(set)
}
