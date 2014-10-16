package uk.ac.warwick.tabula.home.web.controllers

import uk.ac.warwick.spring.Wire
import org.springframework.web.bind.annotation.{ModelAttribute, RequestMapping}
import org.springframework.validation.Errors
import uk.ac.warwick.tabula.home.commands.UserSettingsCommand
import uk.ac.warwick.tabula.CurrentUser
import uk.ac.warwick.tabula.data.model.UserSettings
import uk.ac.warwick.tabula.services.UserSettingsService
import javax.validation.Valid
import org.springframework.stereotype.Controller
import uk.ac.warwick.tabula.services.ModuleAndDepartmentService
import uk.ac.warwick.tabula.permissions.Permissions
import uk.ac.warwick.tabula.web.controllers.BaseController
import uk.ac.warwick.tabula.commands.{SelfValidating, Appliable}
import uk.ac.warwick.tabula.web.views.JSONView

@Controller
class UserSettingsController extends BaseController {

	type UserSettingsCommand = Appliable[UserSettings]

	validatesSelf[SelfValidating]
	
	hideDeletedItems
	
	var userSettingsService = Wire.auto[UserSettingsService]
	var moduleService = Wire[ModuleAndDepartmentService]
	
	private def getUserSettings(user: CurrentUser) = 
		userSettingsService.getByUserId(user.apparentId) 
		
		
	@ModelAttribute("userSettingsCommand")
	def command(user: CurrentUser): UserSettingsCommand = {
		val usersettings = getUserSettings(user)
		usersettings match { 
			case Some(setting) => UserSettingsCommand(user, setting)
			case None => UserSettingsCommand(user, new UserSettings(user.apparentId))
		}
	}
	
	@RequestMapping(value = Array("/settings"), method = Array(GET, HEAD))
	def viewSettings(user: CurrentUser, @ModelAttribute("userSettingsCommand") command: UserSettingsCommand, errors:Errors) = {
		Mav("usersettings/form",
			"isCourseworkModuleManager" -> !moduleService.modulesWithPermission(user, Permissions.Module.ManageAssignments).isEmpty,
			"isDepartmentalAdmin" -> !moduleService.departmentsWithPermission(user, Permissions.Module.ManageAssignments).isEmpty
		)
	}

	@RequestMapping(value = Array("/settings"), method=Array(POST))
	def saveSettings(@ModelAttribute("userSettingsCommand") @Valid command: UserSettingsCommand, errors:Errors) = {
		if (errors.hasErrors){
			viewSettings(user, command, errors)
		}
		else{
			command.apply()
			Redirect("/home")
		}
	}

	@RequestMapping(value = Array("/settings.json"), method = Array(GET, HEAD))
	def viewSettingsJson(user: CurrentUser) = {
		val usersettings =
			getUserSettings(user) match {
				case Some(setting) => JSONUserSettings(setting)
				case None => JSONUserSettings(new UserSettings(user.apparentId))
			}

		Mav(new JSONView(usersettings))
	}

	@RequestMapping(value = Array("/settings.json"), method=Array(POST))
	def saveSettingsJson(@ModelAttribute("userSettingsCommand") @Valid command: UserSettingsCommand, errors: Errors) = {
		if (!errors.hasErrors) command.apply()

		viewSettingsJson(user)
	}
}

case class JSONUserSettings(
	alertsSubmission: String,
	weekNumberingSystem: String,
	bulkEmailSeparator: String,
	profilesDefaultView: String
)

object JSONUserSettings {
	def apply(u: UserSettings): JSONUserSettings = {
		JSONUserSettings(
			alertsSubmission = u.alertsSubmission,
			weekNumberingSystem = u.weekNumberingSystem,
			bulkEmailSeparator = u.bulkEmailSeparator,
			profilesDefaultView = u.profilesDefaultView
		)
	}
}