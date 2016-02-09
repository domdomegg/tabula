package uk.ac.warwick.tabula.web.controllers.attendance.agent

import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.{PathVariable, ModelAttribute, RequestMapping}
import uk.ac.warwick.tabula.CurrentUser
import uk.ac.warwick.tabula.commands.attendance.{HomeInformation, HomeCommand}
import uk.ac.warwick.tabula.web.controllers.attendance.AttendanceController
import uk.ac.warwick.tabula.commands.Appliable
import uk.ac.warwick.tabula.data.model.StudentRelationshipType

/**
 * Displays the agent relationship screen, allowing users to choose academic year to view.
 */
@Controller
@RequestMapping(Array("/attendance/agent/{relationshipType}"))
class AgentRelationshipController extends AttendanceController {

	@ModelAttribute("command")
	def createCommand(user: CurrentUser) = HomeCommand(user)

	@RequestMapping
	def home(@ModelAttribute("command") cmd: Appliable[HomeInformation], @PathVariable relationshipType: StudentRelationshipType) = {
		val info = cmd.apply()

		Mav("attendance/agent/years",
			"relationshipTypesMap" -> info.relationshipTypesMap
		).crumbs(
			Breadcrumbs.Agent.Relationship(mandatory(relationshipType))
		)
	}

}