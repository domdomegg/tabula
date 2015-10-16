package uk.ac.warwick.tabula.api.web.controllers.timetables

import javax.validation.Valid

import org.springframework.stereotype.Controller
import org.springframework.validation.Errors
import org.springframework.web.bind.annotation.{ModelAttribute, PathVariable, RequestMapping}
import uk.ac.warwick.tabula.RequestFailedException
import uk.ac.warwick.tabula.api.web.controllers.ApiController
import uk.ac.warwick.tabula.api.web.helpers.TimetableEventToJsonConverter
import uk.ac.warwick.tabula.commands.timetables.{ViewModuleTimetableCommand, ViewModuleTimetableRequest}
import uk.ac.warwick.tabula.commands.{SelfValidating, Appliable}
import uk.ac.warwick.tabula.data.model.Module
import uk.ac.warwick.tabula.timetables.TimetableEvent

import ModuleTimetableController._
import uk.ac.warwick.tabula.web.views.{JSONView, JSONErrorView}

import scala.util.{Failure, Success, Try}

object ModuleTimetableController {
	type ViewModuleTimetableCommand = Appliable[Try[Seq[TimetableEvent]]] with ViewModuleTimetableRequest with SelfValidating
}

@Controller
@RequestMapping(Array("/v1/module/{module}/timetable"))
class ModuleTimetableController extends ApiController
	with GetModuleTimetableApi
	with TimetableEventToJsonConverter

trait GetModuleTimetableApi {
	self: ApiController with TimetableEventToJsonConverter =>

	validatesSelf[SelfValidating]

	@ModelAttribute("getTimetableCommand")
	def command(@PathVariable module: Module): ViewModuleTimetableCommand =
		ViewModuleTimetableCommand(module)

	@RequestMapping(method = Array(GET), produces = Array("application/json"))
	def showModuleTimetable(@Valid @ModelAttribute("getTimetableCommand") command: ViewModuleTimetableCommand, errors: Errors) = {
		if (errors.hasErrors) {
			Mav(new JSONErrorView(errors))
		} else command.apply() match {
			case Success(events) => Mav(new JSONView(Map(
				"success" -> true,
				"status" -> "ok",
				"events" -> events.map(jsonTimetableEventObject)
			)))
			case Failure(t) => throw new RequestFailedException("The timetabling service could not be reached", t)
		}
	}
}