package uk.ac.warwick.tabula.web.controllers.groups.admin

import javax.validation.Valid
import org.joda.time.DateTime
import org.springframework.stereotype.Controller
import org.springframework.validation.Errors
import org.springframework.web.bind.WebDataBinder
import org.springframework.web.bind.annotation.{RequestParam, ModelAttribute, PathVariable, RequestMapping}
import uk.ac.warwick.spring.Wire
import uk.ac.warwick.tabula.services.SmallGroupService
import uk.ac.warwick.tabula.AcademicYear
import uk.ac.warwick.tabula.data.model.Module
import uk.ac.warwick.tabula.data.model.groups.SmallGroupFormat
import uk.ac.warwick.tabula.commands.groups.admin._
import uk.ac.warwick.tabula.groups.web.Routes
import uk.ac.warwick.tabula.web.controllers.groups.GroupsController
import uk.ac.warwick.util.web.bind.AbstractPropertyEditor
import uk.ac.warwick.tabula.data.model.groups.SmallGroupSet
import uk.ac.warwick.tabula.data.model.groups.SmallGroupAllocationMethod
import uk.ac.warwick.tabula.commands._

trait SmallGroupSetsController extends GroupsController {

	validatesSelf[SelfValidating]

	var smallGroupService = Wire[SmallGroupService]

	@ModelAttribute("ManageSmallGroupsMappingParameters") def params = ManageSmallGroupsMappingParameters

	@ModelAttribute("academicYearChoices") def academicYearChoices =
		AcademicYear.guessSITSAcademicYearByDate(DateTime.now).yearsSurrounding(2, 2)

	@ModelAttribute("allFormats") def allFormats = SmallGroupFormat.members

	override final def binding[A](binder: WebDataBinder, cmd: A) {
		binder.registerCustomEditor(classOf[SmallGroupFormat], new AbstractPropertyEditor[SmallGroupFormat] {
			override def fromString(code: String) = SmallGroupFormat.fromCode(code)
			override def toString(format: SmallGroupFormat) = format.code
		})
		binder.registerCustomEditor(classOf[SmallGroupAllocationMethod], new AbstractPropertyEditor[SmallGroupAllocationMethod] {
			override def fromString(code: String) = SmallGroupAllocationMethod.fromDatabase(code)
			override def toString(method: SmallGroupAllocationMethod) = method.dbValue
		})
	}

}

@RequestMapping(Array("/groups/admin/module/{module}/groups/new"))
@Controller
class CreateSmallGroupSetController extends SmallGroupSetsController {

	type CreateSmallGroupSetCommand = Appliable[SmallGroupSet] with CreateSmallGroupSetCommandState

	@ModelAttribute("departmentSmallGroupSets") def departmentSmallGroupSets(@PathVariable("module") module: Module, @RequestParam(value="academicYear", required=false) academicYear: AcademicYear) =
		smallGroupService.getDepartmentSmallGroupSets(module.adminDepartment, Option(academicYear).getOrElse(AcademicYear.guessSITSAcademicYearByDate(DateTime.now)))

	@ModelAttribute("createSmallGroupSetCommand") def cmd(@PathVariable("module") module: Module): CreateSmallGroupSetCommand =
		ModifySmallGroupSetCommand.create(module)

	@RequestMapping
	def form(@ModelAttribute("createSmallGroupSetCommand") cmd: CreateSmallGroupSetCommand) = {
		Mav("groups/admin/groups/new").crumbs(Breadcrumbs.DepartmentForYear(cmd.module.adminDepartment, cmd.academicYear), Breadcrumbs.ModuleForYear(cmd.module, cmd.academicYear))
	}

	private def submit(cmd: CreateSmallGroupSetCommand, errors: Errors, route: SmallGroupSet => String) = {
		if (errors.hasErrors) form(cmd)
		else {
			val set = cmd.apply()
			RedirectForce(route(set))
		}
	}

	@RequestMapping(method = Array(POST), params = Array("action=update"))
	def save(@Valid @ModelAttribute("createSmallGroupSetCommand") cmd: CreateSmallGroupSetCommand, errors: Errors) =
		submit(cmd, errors, Routes.admin.create(_))

	@RequestMapping(method = Array(POST), params=Array("action!=refresh", "action!=update"))
	def saveAndExit(@Valid @ModelAttribute("createSmallGroupSetCommand") cmd: CreateSmallGroupSetCommand, errors: Errors) =
		submit(cmd, errors, { _ => Routes.admin(cmd.module.adminDepartment, cmd.academicYear) })

	@RequestMapping(method = Array(POST), params = Array(ManageSmallGroupsMappingParameters.createAndAddStudents, "action!=refresh", "action!=update"))
	def submitAndAddStudents(@Valid @ModelAttribute("createSmallGroupSetCommand") cmd: CreateSmallGroupSetCommand, errors: Errors) =
		submit(cmd, errors, Routes.admin.createAddStudents(_))

	@RequestMapping(method = Array(POST), params = Array(ManageSmallGroupsMappingParameters.createAndAddGroups, "action!=refresh", "action!=update"))
	def submitAndAddGroups(@Valid @ModelAttribute("createSmallGroupSetCommand") cmd: CreateSmallGroupSetCommand, errors: Errors) =
		submit(cmd, errors, Routes.admin.createAddGroups(_))

	@RequestMapping(method = Array(POST), params = Array(ManageSmallGroupsMappingParameters.createAndAddEvents, "action!=refresh", "action!=update"))
	def submitAndAddEvents(@Valid @ModelAttribute("createSmallGroupSetCommand") cmd: CreateSmallGroupSetCommand, errors: Errors) =
		submit(cmd, errors, Routes.admin.createAddEvents(_))

	@RequestMapping(method = Array(POST), params = Array(ManageSmallGroupsMappingParameters.createAndAllocate, "action!=refresh", "action!=update"))
	def submitAndAllocate(@Valid @ModelAttribute("createSmallGroupSetCommand") cmd: CreateSmallGroupSetCommand, errors: Errors) =
		submit(cmd, errors, Routes.admin.createAllocate(_))
}

@RequestMapping(Array("/groups/admin/module/{module}/groups/new/{smallGroupSet}"))
@Controller
class CreateSmallGroupSetEditPropertiesController extends SmallGroupSetsController {

	type EditSmallGroupSetCommand = Appliable[SmallGroupSet] with EditSmallGroupSetCommandState

	@ModelAttribute("departmentSmallGroupSets") def departmentSmallGroupSets(@PathVariable("module") module: Module, @PathVariable("smallGroupSet") set: SmallGroupSet, @RequestParam(value="academicYear", required=false) academicYear: AcademicYear) =
		smallGroupService.getDepartmentSmallGroupSets(module.adminDepartment, Option(academicYear).getOrElse(set.academicYear))

	@ModelAttribute("createSmallGroupSetCommand") def cmd(@PathVariable("module") module: Module, @PathVariable("smallGroupSet") set: SmallGroupSet) =
		ModifySmallGroupSetCommand.edit(module, set)

	private def renderForm(cmd: EditSmallGroupSetCommand, model: (String, _)*) =
		Mav("groups/admin/groups/new", model:_*).crumbs(Breadcrumbs.DepartmentForYear(cmd.module.adminDepartment, cmd.academicYear), Breadcrumbs.ModuleForYear(cmd.module, cmd.academicYear))

	@RequestMapping
	def form(@ModelAttribute("createSmallGroupSetCommand") cmd: EditSmallGroupSetCommand) = renderForm(cmd)

	private def submit(cmd: EditSmallGroupSetCommand, errors: Errors, route: SmallGroupSet => String) = {
		if (errors.hasErrors) form(cmd)
		else {
			val set = cmd.apply()
			RedirectForce(route(set))
		}
	}

	@RequestMapping(method = Array(POST), params=Array("action=update"))
	def save(@Valid @ModelAttribute("createSmallGroupSetCommand") cmd: EditSmallGroupSetCommand, errors: Errors) = {
		if (!errors.hasErrors) cmd.apply()

		renderForm(cmd, "saved" -> true)
	}

	@RequestMapping(method = Array(POST), params=Array("action!=refresh", "action!=update"))
	def saveAndExit(@Valid @ModelAttribute("createSmallGroupSetCommand") cmd: EditSmallGroupSetCommand, errors: Errors) =
		submit(cmd, errors, { _ => Routes.admin(cmd.module.adminDepartment, cmd.academicYear) })

	@RequestMapping(method = Array(POST), params = Array(ManageSmallGroupsMappingParameters.editAndAddStudents, "action!=refresh", "action!=update"))
	def submitAndAddStudents(@Valid @ModelAttribute("createSmallGroupSetCommand") cmd: EditSmallGroupSetCommand, errors: Errors) =
		submit(cmd, errors, Routes.admin.createAddStudents(_))

	@RequestMapping(method = Array(POST), params = Array(ManageSmallGroupsMappingParameters.editAndAddGroups, "action!=refresh", "action!=update"))
	def submitAndAddGroups(@Valid @ModelAttribute("createSmallGroupSetCommand") cmd: EditSmallGroupSetCommand, errors: Errors) =
		submit(cmd, errors, Routes.admin.createAddGroups(_))

	@RequestMapping(method = Array(POST), params = Array(ManageSmallGroupsMappingParameters.editAndAddEvents, "action!=refresh", "action!=update"))
	def submitAndAddEvents(@Valid @ModelAttribute("createSmallGroupSetCommand") cmd: EditSmallGroupSetCommand, errors: Errors) =
		submit(cmd, errors, Routes.admin.createAddEvents(_))

	@RequestMapping(method = Array(POST), params = Array(ManageSmallGroupsMappingParameters.editAndAllocate, "action!=refresh", "action!=update"))
	def submitAndAllocate(@Valid @ModelAttribute("createSmallGroupSetCommand") cmd: EditSmallGroupSetCommand, errors: Errors) =
		submit(cmd, errors, Routes.admin.createAllocate(_))
}

@RequestMapping(Array("/groups/admin/module/{module}/groups/{smallGroupSet}/edit", "/groups/admin/module/{module}/groups/edit/{smallGroupSet}"))
@Controller
class EditSmallGroupSetController extends SmallGroupSetsController {

	type EditSmallGroupSetCommand = Appliable[SmallGroupSet] with EditSmallGroupSetCommandState

	@ModelAttribute("departmentSmallGroupSets") def departmentSmallGroupSets(@PathVariable("module") module: Module, @PathVariable("smallGroupSet") set: SmallGroupSet, @RequestParam(value="academicYear", required=false) academicYear: AcademicYear) =
		smallGroupService.getDepartmentSmallGroupSets(module.adminDepartment, Option(academicYear).getOrElse(set.academicYear))

	@ModelAttribute("editSmallGroupSetCommand") def cmd(@PathVariable("module") module: Module, @PathVariable("smallGroupSet") set: SmallGroupSet) =
		ModifySmallGroupSetCommand.edit(module, set)

	private def renderForm(cmd: EditSmallGroupSetCommand, model: (String, _)*) = {
		Mav("groups/admin/groups/edit", model:_*).crumbs(Breadcrumbs.DepartmentForYear(cmd.module.adminDepartment, cmd.academicYear), Breadcrumbs.ModuleForYear(cmd.module, cmd.academicYear))
	}

	@RequestMapping
	def form(@ModelAttribute("editSmallGroupSetCommand") cmd: EditSmallGroupSetCommand) = renderForm(cmd)

	private def submit(cmd: EditSmallGroupSetCommand, errors: Errors, route: SmallGroupSet => String) = {
		if (errors.hasErrors) form(cmd)
		else {
			val set = cmd.apply()
			RedirectForce(route(set))
		}
	}

	@RequestMapping(method = Array(POST), params=Array("action=update"))
	def save(@Valid @ModelAttribute("editSmallGroupSetCommand") cmd: EditSmallGroupSetCommand, errors: Errors) = {
		if (!errors.hasErrors) cmd.apply()

		renderForm(cmd, "saved" -> true)
	}

	@RequestMapping(method = Array(POST), params=Array("action!=refresh", "action!=update"))
	def saveAndExit(@Valid @ModelAttribute("editSmallGroupSetCommand") cmd: EditSmallGroupSetCommand, errors: Errors) =
		submit(cmd, errors, { _ => Routes.admin(cmd.module.adminDepartment, cmd.academicYear) })

	@RequestMapping(method = Array(POST), params = Array(ManageSmallGroupsMappingParameters.editAndAddStudents, "action!=refresh", "action!=update"))
	def submitAndAddStudents(@Valid @ModelAttribute("editSmallGroupSetCommand") cmd: EditSmallGroupSetCommand, errors: Errors) =
		submit(cmd, errors, Routes.admin.editAddStudents(_))

	@RequestMapping(method = Array(POST), params = Array(ManageSmallGroupsMappingParameters.editAndAddGroups, "action!=refresh", "action!=update"))
	def submitAndAddGroups(@Valid @ModelAttribute("editSmallGroupSetCommand") cmd: EditSmallGroupSetCommand, errors: Errors) =
		submit(cmd, errors, Routes.admin.editAddGroups(_))

	@RequestMapping(method = Array(POST), params = Array(ManageSmallGroupsMappingParameters.editAndAddEvents, "action!=refresh", "action!=update"))
	def submitAndAddEvents(@Valid @ModelAttribute("editSmallGroupSetCommand") cmd: EditSmallGroupSetCommand, errors: Errors) =
		submit(cmd, errors, Routes.admin.editAddEvents(_))

	@RequestMapping(method = Array(POST), params = Array(ManageSmallGroupsMappingParameters.editAndAllocate, "action!=refresh", "action!=update"))
	def submitAndAllocate(@Valid @ModelAttribute("editSmallGroupSetCommand") cmd: EditSmallGroupSetCommand, errors: Errors) =
		submit(cmd, errors, Routes.admin.editAllocate(_))
}

@RequestMapping(Array("/groups/admin/module/{module}/groups/{set}/delete"))
@Controller
class DeleteSmallGroupSetController extends GroupsController {

	validatesSelf[DeleteSmallGroupSetCommand]

	@ModelAttribute("smallGroupSet") def set(@PathVariable("set") set: SmallGroupSet) = set

	@ModelAttribute("deleteSmallGroupSetCommand") def cmd(@PathVariable("module") module: Module, @PathVariable("set") set: SmallGroupSet) =
		new DeleteSmallGroupSetCommand(module, set)

	@RequestMapping
	def form(cmd: DeleteSmallGroupSetCommand) =
		Mav("groups/admin/groups/delete")
		.crumbs(Breadcrumbs.DepartmentForYear(cmd.module.adminDepartment, cmd.set.academicYear), Breadcrumbs.ModuleForYear(cmd.module, cmd.set.academicYear))

	@RequestMapping(method = Array(POST))
	def submit(@Valid cmd: DeleteSmallGroupSetCommand, errors: Errors) =
		if (errors.hasErrors) form(cmd)
		else {
			val set = cmd.apply()
			Redirect(Routes.admin(cmd.module.adminDepartment, set.academicYear))
		}

}
