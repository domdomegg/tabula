package uk.ac.warwick.tabula.web.controllers.attendance.agent

import javax.validation.Valid

import org.springframework.stereotype.Controller
import org.springframework.validation.Errors
import org.springframework.web.bind.annotation.{ModelAttribute, PathVariable, RequestMapping}
import uk.ac.warwick.tabula.AcademicYear
import uk.ac.warwick.tabula.commands.attendance.agent.AgentPointRecordCommand
import uk.ac.warwick.tabula.attendance.web.Routes
import uk.ac.warwick.tabula.web.controllers.attendance.{AttendanceController, HasMonthNames}
import uk.ac.warwick.tabula.commands.{Appliable, PopulateOnForm, SelfValidating}
import uk.ac.warwick.tabula.data.model.StudentRelationshipType
import uk.ac.warwick.tabula.data.model.attendance.{AttendanceMonitoringCheckpoint, AttendanceMonitoringPoint}

@Controller
@RequestMapping(Array("/attendance/agent/{relationshipType}/{academicYear}/point/{templatePoint}"))
class AgentPointRecordController extends AttendanceController with HasMonthNames {

	validatesSelf[SelfValidating]

	@ModelAttribute("command")
	def command(
		@PathVariable relationshipType: StudentRelationshipType,
		@PathVariable academicYear: AcademicYear,
		@PathVariable templatePoint: AttendanceMonitoringPoint
	) =
		AgentPointRecordCommand(mandatory(relationshipType), mandatory(academicYear), mandatory(templatePoint), user, currentMember)

	@RequestMapping(method = Array(GET))
	def form(
		@ModelAttribute("command") cmd: Appliable[Seq[AttendanceMonitoringCheckpoint]] with PopulateOnForm,
		@PathVariable relationshipType: StudentRelationshipType,
		@PathVariable academicYear: AcademicYear
	) = {
		cmd.populate()
		render(relationshipType, academicYear)
	}

	private def render(relationshipType: StudentRelationshipType, academicYear: AcademicYear) = {
		Mav("attendance/agent/pointrecord",
			"department" -> currentMember.homeDepartment,
			"returnTo" -> getReturnTo(Routes.Agent.relationshipForYear(relationshipType, academicYear))
		).crumbs(
			Breadcrumbs.Agent.Relationship(relationshipType),
			Breadcrumbs.Agent.RelationshipForYear(relationshipType, academicYear)
		)
	}

	@RequestMapping(method = Array(POST))
	def post(
		@Valid @ModelAttribute("command") cmd: Appliable[Seq[AttendanceMonitoringCheckpoint]],
		errors: Errors,
		@PathVariable relationshipType: StudentRelationshipType,
		@PathVariable academicYear: AcademicYear
	) = {
		if (errors.hasErrors) {
			render(relationshipType, academicYear)
		} else {
			cmd.apply()
			Redirect(Routes.Agent.relationshipForYear(relationshipType, academicYear))
		}
	}

}