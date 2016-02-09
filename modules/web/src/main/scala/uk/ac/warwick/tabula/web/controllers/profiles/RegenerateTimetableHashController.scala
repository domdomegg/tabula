package uk.ac.warwick.tabula.web.controllers.profiles

import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.RequestMapping
import uk.ac.warwick.tabula.data.Transactions._
import uk.ac.warwick.tabula.profiles.web.Routes

@RequestMapping(Array("/profiles/timetable/regeneratehash"))
@Controller
class RegenerateTimetableHashController extends ProfilesController {


	@RequestMapping(method=Array(POST))
	def generateNewHash() = transactional() {
		profileService.regenerateTimetableHash(currentMember)
		Redirect(Routes.profile.view(currentMember))
	}

}
