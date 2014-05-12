package uk.ac.warwick.tabula.attendance.web.controllers

import org.springframework.stereotype.Controller
import org.springframework.validation.Errors
import org.springframework.web.bind.annotation.{PathVariable, ModelAttribute, RequestMapping}

import javax.validation.Valid
import uk.ac.warwick.tabula.attendance.commands.SetMonitoringCheckpointForStudentCommand
import uk.ac.warwick.tabula.attendance.web.Routes
import uk.ac.warwick.tabula.commands.{Appliable, PopulateOnForm, SelfValidating}
import uk.ac.warwick.tabula.data.model.{StudentMember, Department}
import uk.ac.warwick.tabula.data.model.attendance.{AttendanceState, MonitoringPoint}
import uk.ac.warwick.tabula.data.model.attendance.MonitoringCheckpoint
import uk.ac.warwick.tabula.web.Mav
import uk.ac.warwick.tabula.services.{AutowiringMonitoringPointServiceComponent, AutowiringTermServiceComponent, AutowiringRelationshipServiceComponent}

@RequestMapping(Array("/{department}/{monitoringPoint}/record/{student}"))
@Controller
class SetMonitoringCheckpointForStudentController extends AttendanceController with AutowiringRelationshipServiceComponent
	with AutowiringTermServiceComponent with AutowiringMonitoringPointServiceComponent {

	validatesSelf[SelfValidating]

	@ModelAttribute("command")
	def command(@PathVariable monitoringPoint: MonitoringPoint, @PathVariable student: StudentMember)
		: Appliable[Seq[MonitoringCheckpoint]] with PopulateOnForm
			= SetMonitoringCheckpointForStudentCommand(monitoringPoint, mandatory(student), user)

	@ModelAttribute("hasReported")
	def hasReported(@PathVariable monitoringPoint: MonitoringPoint, @PathVariable student: StudentMember): Boolean = {
		val period = termService.getTermFromAcademicWeekIncludingVacations(monitoringPoint.validFromWeek, monitoringPoint.pointSet.academicYear).getTermTypeAsString
		val nonReported = monitoringPointService.findNonReported(Seq(student), monitoringPoint.pointSet.academicYear, period)
		!nonReported.contains(student)
	}

	@RequestMapping(method = Array(GET, HEAD))
	def list(@ModelAttribute("command") command: Appliable[Seq[MonitoringCheckpoint]] with PopulateOnForm, @PathVariable department: Department): Mav = {
		command.populate()
		form(command, department)
	}


	def form(@ModelAttribute command: Appliable[Seq[MonitoringCheckpoint]], department: Department): Mav = {
		Mav("home/record_point",
				"command" -> command,
				"allCheckpointStates" -> AttendanceState.values,
				"returnTo" -> getReturnTo(Routes.old.department.view(department)))
	}


	@RequestMapping(method = Array(POST))
	def submit(
		@Valid @ModelAttribute("command") command: Appliable[Seq[MonitoringCheckpoint]],
		errors: Errors,
		@PathVariable monitoringPoint: MonitoringPoint,
		@PathVariable department: Department
	) = {
		if(errors.hasErrors) {
			form(command, department)
		} else {
			command.apply()
			Redirect(Routes.old.department.view(department), "updatedMonitoringPoint" -> monitoringPoint.id)
		}
	}

}