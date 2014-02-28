package uk.ac.warwick.tabula.admin.web.controllers.modules

import org.springframework.stereotype.Controller
import org.springframework.validation.Errors
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import uk.ac.warwick.tabula.admin.web.Routes
import uk.ac.warwick.tabula.data.model.{Module, Department}
import uk.ac.warwick.tabula.web.Mav
import uk.ac.warwick.tabula.web.controllers.BaseController
import javax.validation.Valid
import uk.ac.warwick.tabula.admin.web.controllers.AdminController
import uk.ac.warwick.tabula.admin.commands.modules.{SortModulesCommandState, SortModulesCommand}
import uk.ac.warwick.tabula.commands.{GroupsObjects, SelfValidating, Appliable}

/**
 * The interface for sorting department modules into
 * child departments (or module groups, whatever you want to call them).
 */
@Controller
@RequestMapping(value=Array("/department/{department}/sort-modules"))
class SortModulesController extends AdminController {

	type SortModulesCommand = Appliable[Unit] with GroupsObjects[Module, Department] with SortModulesCommandState
	validatesSelf[SelfValidating]
	
	@ModelAttribute
	def command(@PathVariable department: Department): SortModulesCommand = SortModulesCommand(department)

	@RequestMapping(method=Array(GET, HEAD))
	def showForm(@ModelAttribute cmd: SortModulesCommand, errors: Errors):Mav = {
		cmd.populate()
		cmd.sort()
		form(cmd)
	}

	@RequestMapping(method=Array(POST))
	def submit(@Valid @ModelAttribute cmd: SortModulesCommand, errors: Errors): Mav = {
		cmd.sort()
		if (errors.hasErrors()) {
			form(cmd)
		} else {
			cmd.apply()
			form(cmd).addObjects("saved" -> true)
		}
	}
		
	def form(cmd: SortModulesCommand): Mav = {
		if (cmd.department.hasParent) {
			// Sorting is done from the POV of the top department.
			Redirect(Routes.department.sortModules(cmd.department.parent))
		} else {
			Mav("admin/modules/arrange/form")
		}
	}
	
}
