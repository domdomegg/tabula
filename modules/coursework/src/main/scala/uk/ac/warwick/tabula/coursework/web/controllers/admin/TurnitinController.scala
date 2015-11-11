package uk.ac.warwick.tabula.coursework.web.controllers.admin

import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation._
import uk.ac.warwick.tabula.commands.SelfValidating
import uk.ac.warwick.tabula.commands.coursework.turnitin.SubmitToTurnitinCommand
import uk.ac.warwick.tabula.coursework.web.Routes
import uk.ac.warwick.tabula.coursework.web.controllers.CourseworkController
import uk.ac.warwick.tabula.services.jobs.JobService
import org.springframework.beans.factory.annotation.Autowired
import uk.ac.warwick.tabula.CurrentUser
import uk.ac.warwick.tabula.services.AssessmentService
import uk.ac.warwick.tabula.data.model.Module
import uk.ac.warwick.tabula.data.model.Assignment
import javax.validation.Valid
import org.springframework.validation.Errors
import uk.ac.warwick.tabula.services.turnitin.Turnitin

import scala.collection.JavaConverters._

@Controller
@RequestMapping(value = Array("/admin/module/{module}/assignments/{assignment}/turnitin"))
class TurnitinController extends CourseworkController {

	type SubmitToTurnitinCommand = SubmitToTurnitinCommand.CommandType

	@Autowired var jobService: JobService = _
	@Autowired var assignmentService: AssessmentService = _

	validatesSelf[SelfValidating]

	@ModelAttribute("command")
	def model(@PathVariable("module") module: Module, @PathVariable("assignment") assignment: Assignment, user: CurrentUser) =
		SubmitToTurnitinCommand(module, assignment, user)

	@ModelAttribute("incompatibleFiles")
	def incompatibleFiles(@PathVariable("assignment") assignment: Assignment) = {
		val allAttachments = mandatory(assignment).submissions.asScala.flatMap{ _.allAttachments }
		allAttachments.filterNot(a => Turnitin.validFileType(a) && Turnitin.validFileSize(a))
	}

	@RequestMapping(method = Array(GET, HEAD), params = Array("!jobId"))
	def confirm(@Valid @ModelAttribute("command") command: SubmitToTurnitinCommand, errors: Errors) = {
		Mav("admin/assignments/turnitin/form", "errors" -> errors)
	}

	@RequestMapping(method = Array(POST), params = Array("!jobId"))
	def submit(@Valid @ModelAttribute("command") command: SubmitToTurnitinCommand, errors: Errors) = {
		if (errors.hasErrors) {
			confirm(command, errors)
		} else {
			val jobId = command.apply().id
			Redirect(Routes.admin.assignment.turnitin.status(command.assignment) + "?jobId=" + jobId)
		}
	}

	@RequestMapping(params = Array("jobId"))
	def status(@RequestParam jobId: String) = {
		val job = jobService.getInstance(jobId)
		val mav = Mav("admin/assignments/turnitin/status", "job" -> job).noLayoutIf(ajax)
		// add assignment object if we can find it. FIXME This is a bit hacky.
		job foreach { job =>
			assignmentService.getAssignmentById(job.getString("assignment")) foreach { assignment =>
				mav.addObjects("assignment" -> assignment)
			}
		}
		mav
	}

}