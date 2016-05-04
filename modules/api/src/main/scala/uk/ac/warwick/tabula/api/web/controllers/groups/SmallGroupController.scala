package uk.ac.warwick.tabula.api.web.controllers.groups

import javax.servlet.http.HttpServletResponse
import javax.validation.Valid

import org.springframework.http.MediaType
import org.springframework.stereotype.Controller
import org.springframework.validation.Errors
import org.springframework.web.bind.annotation._
import uk.ac.warwick.tabula.JavaImports._
import uk.ac.warwick.tabula.api.commands.JsonApiRequest
import uk.ac.warwick.tabula.api.web.controllers.groups.SmallGroupController.{CreateSmallGroupsCommand, DeleteSmallGroupCommand, ModifySmallGroupCommand}
import uk.ac.warwick.tabula.commands.Appliable
import uk.ac.warwick.tabula.commands.groups.admin.EditSmallGroupsCommand.NewGroupProperties
import uk.ac.warwick.tabula.commands.groups.admin._
import uk.ac.warwick.tabula.data.model._
import uk.ac.warwick.tabula.data.model.groups.{SmallGroup, SmallGroupSet}
import uk.ac.warwick.tabula.web.views.{JSONErrorView, JSONView}

import scala.beans.BeanProperty


object SmallGroupController {
	type CreateSmallGroupsCommand = Appliable[Seq[SmallGroup]] with EditSmallGroupsCommandState with EditSmallGroupsValidation
	type DeleteSmallGroupCommand = Appliable[SmallGroup] with DeleteSmallGroupCommandState with DeleteSmallGroupValidation
	type ModifySmallGroupCommand = ModifySmallGroupCommand.Command
}

@Controller
@RequestMapping(Array("/v1/module/{module}/groups/edit/{smallGroupSet}/groups"))
class CreateSmallGroupsControllerForApi extends SmallGroupSetController with CreateSmallGroupsApi


trait CreateSmallGroupsApi {
	self: SmallGroupSetController =>


	@ModelAttribute("command")
	def createCommand(@PathVariable module: Module, @PathVariable smallGroupSet: SmallGroupSet): CreateSmallGroupsCommand =
		EditSmallGroupsCommand(module, smallGroupSet)

	@RequestMapping(method = Array(POST), consumes = Array(MediaType.APPLICATION_JSON_VALUE), produces = Array("application/json"))
	def createGroups(@RequestBody request: CreateSmallGroupsRequest, @ModelAttribute("command") command: CreateSmallGroupsCommand, errors: Errors, @PathVariable smallGroupSet: SmallGroupSet)(implicit response: HttpServletResponse) = {
		request.copyTo(command, errors)
		globalValidator.validate(command, errors)
		command.validate(errors)
		if (errors.hasErrors) {
			Mav(new JSONErrorView(errors))
		} else {
			command.apply()
			getSmallGroupSetMav(smallGroupSet)
		}
	}
}


@Controller
@RequestMapping(Array("/v1/module/{module}/groups/edit/{smallGroupSet}/group/{smallGroup}"))
class EditSmallGroupControllerForApi extends SmallGroupSetController with EditSmallGroupApi

trait EditSmallGroupApi {
	self: SmallGroupSetController =>

	@ModelAttribute("editCommand")
	def editCommand(@PathVariable module: Module, @PathVariable smallGroupSet: SmallGroupSet, @PathVariable smallGroup: SmallGroup): ModifySmallGroupCommand =
		ModifySmallGroupCommand.edit(module, smallGroupSet, smallGroup)

	@RequestMapping(method = Array(PUT), consumes = Array(MediaType.APPLICATION_JSON_VALUE), produces = Array("application/json"))
	def editGroup(@RequestBody request: ModifySmallGroupRequest, @ModelAttribute("editCommand") command: ModifySmallGroupCommand, errors: Errors, @PathVariable smallGroupSet: SmallGroupSet, @PathVariable smallGroup: SmallGroup)(implicit response: HttpServletResponse) = {
		request.copyTo(command, errors)
		globalValidator.validate(command, errors)
		command.validate(errors)
		if (errors.hasErrors) {
			Mav(new JSONErrorView(errors))
		} else {
			command.apply()
			getSmallGroupSetMav(smallGroupSet)
		}
	}
}

@Controller
@RequestMapping(Array("/v1/module/{module}/groups/edit/{smallGroupSet}/group/{smallGroup}"))
class DeleteSmallGroupControllerForApi extends SmallGroupSetController with DeleteSmallGroupApi

trait DeleteSmallGroupApi {
	self: SmallGroupSetController =>

	@ModelAttribute("deleteCommand")
	def deleteCommand(@PathVariable module: Module, @PathVariable smallGroupSet: SmallGroupSet, @PathVariable smallGroup: SmallGroup): DeleteSmallGroupCommand =
		DeleteSmallGroupCommand(smallGroupSet, smallGroup)


	@RequestMapping(method = Array(DELETE), consumes = Array(MediaType.APPLICATION_JSON_VALUE), produces = Array("application/json"))
	def deleteGroup(@Valid @ModelAttribute("deleteCommand") command: DeleteSmallGroupCommand, errors: Errors) = {
		if (errors.hasErrors) {
			Mav(new JSONErrorView(errors))
		} else {
			command.apply()
			Mav(new JSONView(Map(
				"success" -> true,
				"status" -> "ok"
			)))
		}
	}
}


class CreateSmallGroupsRequest extends JsonApiRequest[CreateSmallGroupsCommand] {
	@BeanProperty var newGroups: JList[NewGroupProperties] = null

	override def copyTo(state: CreateSmallGroupsCommand, errors: Errors) {
		Option(newGroups).foreach {
			state.newGroups = _
		}
	}
}

class ModifySmallGroupRequest extends JsonApiRequest[ModifySmallGroupCommand] {
	@BeanProperty var name: String = null
	@BeanProperty var maxGroupSize: JInteger = null

	override def copyTo(state: ModifySmallGroupCommand, errors: Errors) {
		Option(name).foreach {
			state.name = _
		}
		Option(maxGroupSize).foreach {
			state.maxGroupSize = _
		}
	}
}
