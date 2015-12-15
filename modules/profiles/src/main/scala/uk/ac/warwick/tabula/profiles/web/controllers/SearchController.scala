package uk.ac.warwick.tabula.profiles.web.controllers

import org.springframework.validation.Errors
import uk.ac.warwick.tabula.helpers.profiles.SearchJSONHelpers
import uk.ac.warwick.tabula.commands.profiles.SearchProfilesCommand
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.stereotype.Controller
import javax.validation.Valid
import uk.ac.warwick.tabula.profiles.web.Routes
import uk.ac.warwick.util.core.StringUtils

@Controller
class SearchController extends ProfilesController with SearchJSONHelpers {

	val formMav = Mav("profiles/search/", "displayOptionToSave" -> false)

	@ModelAttribute("searchProfilesCommand") def searchProfilesCommand = new SearchProfilesCommand(currentMember, user)

	@RequestMapping(value=Array("/search"), params=Array("!query"))
	def home = Redirect(Routes.home)

	@RequestMapping(value=Array("/search"), params=Array("query"))
	def submitSearch(@Valid @ModelAttribute cmd: SearchProfilesCommand, errors: Errors) = {
		if (!StringUtils.hasText(cmd.query)) home
		else submit(cmd, errors, "profile/search/results")
	}

	@RequestMapping(value=Array("/search.json"), params=Array("query"))
	def submitSearchJSON(@Valid @ModelAttribute cmd: SearchProfilesCommand, errors: Errors) = {
		submitJson(cmd, errors)
	}

}