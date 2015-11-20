package uk.ac.warwick.tabula.api.web.controllers.coursework.assignments

import javax.servlet.http.HttpServletResponse

import com.fasterxml.jackson.annotation.JsonAutoDetect
import org.springframework.http.{HttpStatus, MediaType}
import org.springframework.stereotype.Controller
import org.springframework.validation.Errors
import org.springframework.web.bind.annotation.{RequestBody, PathVariable, ModelAttribute, RequestMapping}
import uk.ac.warwick.tabula.api.commands.JsonApiRequest
import uk.ac.warwick.tabula.api.web.controllers.ApiController
import uk.ac.warwick.tabula.api.web.helpers._
import uk.ac.warwick.tabula.commands.SelfValidating
import uk.ac.warwick.tabula.commands.coursework.assignments.SubmissionAndFeedbackCommand
import uk.ac.warwick.tabula.commands.coursework.turnitin.{SubmitToTurnitinRequest, SubmitToTurnitinCommand}
import uk.ac.warwick.tabula.data.model.{Module, Assignment}
import uk.ac.warwick.tabula.web.Routes
import uk.ac.warwick.tabula.web.views.{JSONView, JSONErrorView}
import uk.ac.warwick.userlookup.User

import scala.beans.BeanProperty

import AssignmentTurnitinController._

object AssignmentTurnitinController {
	type SubmitToTurnitinCommand = SubmitToTurnitinCommand.CommandType
}

@Controller
@RequestMapping(Array("/v1/module/{module}/assignments/{assignment}/turnitin"))
class AssignmentTurnitinController extends ApiController
	with GetAssignmentApi with GetAssignmentApiTurnitinOutput // Boom, re-use!
	with CreateAssignmentTurnitinJobApi
	with AssignmentToJsonConverter
	with AssessmentMembershipInfoToJsonConverter
	with AssignmentStudentToJsonConverter
	with ReplacingAssignmentStudentMessageResolver
	with JobInstanceToJsonConverter {
	validatesSelf[SelfValidating]
}

trait CreateAssignmentTurnitinJobApi {
	self: ApiController with JobInstanceToJsonConverter =>

	@ModelAttribute("createCommand")
	def createCommand(@PathVariable module: Module, @PathVariable assignment: Assignment): SubmitToTurnitinCommand  =
		SubmitToTurnitinCommand(module, assignment)

	@RequestMapping(method = Array(POST), consumes = Array(MediaType.APPLICATION_JSON_VALUE), produces = Array("application/json"))
	def create(@RequestBody request: CreateAssignmentTurnitinJobRequest, @ModelAttribute("createCommand") command: SubmitToTurnitinCommand, errors: Errors)(implicit response: HttpServletResponse) = {
		request.copyTo(command, errors)
		command.validate(errors)

		if (errors.hasErrors) {
			Mav(new JSONErrorView(errors))
		} else {
			val job = command.apply()

			response.setStatus(HttpStatus.ACCEPTED.value())
			response.addHeader("Location", toplevelUrl + Routes.api.job(job))

			Mav(new JSONView(Map(
				"success" -> true,
				"status" -> "ok",
				"job" -> jsonJobInstanceObject(job)
			)))
		}
	}

}

@JsonAutoDetect
class CreateAssignmentTurnitinJobRequest extends JsonApiRequest[SubmitToTurnitinRequest] {

	@BeanProperty var submitter: User = _

	override def copyTo(state: SubmitToTurnitinRequest, errors: Errors): Unit = {
		Option(submitter).foreach { state.submitter = _ }
	}

}

trait GetAssignmentApiTurnitinOutput extends GetAssignmentApiOutput {
	self: ApiController with AssignmentToJsonConverter with AssignmentStudentToJsonConverter =>

	def outputJson(assignment: Assignment, results: SubmissionAndFeedbackCommand.SubmissionAndFeedbackResults) = Map(
		"students" -> results.students.map { student =>
			jsonAssignmentStudentObject(student).filterKeys { key => key == "universityId" || key == "submission" } // only include the universityId and submission keys
		}
	)
}