package uk.ac.warwick.tabula.coursework.web.controllers.admin

import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation._
import javax.servlet.http.HttpServletResponse
import uk.ac.warwick.spring.Wire
import uk.ac.warwick.tabula.{CurrentUser, ItemNotFoundException}
import uk.ac.warwick.tabula.coursework.commands.feedback._
import uk.ac.warwick.tabula.coursework.web.controllers.CourseworkController
import uk.ac.warwick.tabula.data.FeedbackDao
import uk.ac.warwick.tabula.data.model._
import uk.ac.warwick.tabula.services.fileserver.{RenderableZip, FileServer}
import javax.servlet.http.HttpServletRequest
import uk.ac.warwick.userlookup.User
import uk.ac.warwick.tabula.commands.{ApplyWithCallback, Appliable}
import uk.ac.warwick.tabula.web.Mav
import uk.ac.warwick.tabula.web.controllers.BaseController
import org.springframework.beans.factory.annotation.Autowired

@Controller
@RequestMapping(Array("/admin/module/{module}/assignments/{assignment}/feedback/download/{feedbackId}/{filename}.zip"))
class DownloadSelectedFeedbackController extends CourseworkController {
	var feedbackDao = Wire.auto[FeedbackDao]
	var fileServer = Wire.auto[FileServer]
	
	@ModelAttribute
	def singleFeedbackCommand(
		@PathVariable("module") module: Module,
		@PathVariable("assignment") assignment: Assignment,
		@PathVariable("feedbackId") feedbackId: String
	) = new AdminGetSingleFeedbackCommand(module, assignment, mandatory(feedbackDao.getFeedback(feedbackId)))

	@RequestMapping(method = Array(RequestMethod.GET, RequestMethod.HEAD))
	def get(
		cmd: AdminGetSingleFeedbackCommand,
		@PathVariable("filename") filename: String
	)(implicit request: HttpServletRequest, response: HttpServletResponse) {
		fileServer.serve(cmd.apply())
	}
}


@Controller
@RequestMapping(Array("/admin/module/{module}/assignments/{assignment}/feedback/download/{feedbackId}/{filename}"))
class DownloadSelectedFeedbackFileController extends CourseworkController {

	var feedbackDao = Wire.auto[FeedbackDao]
	var fileServer = Wire.auto[FileServer]
	
	@ModelAttribute def singleFeedbackCommand(
		@PathVariable("module") module: Module,
		@PathVariable("assignment") assignment: Assignment,
		@PathVariable("feedbackId") feedbackId: String
	) =
		new AdminGetSingleFeedbackFileCommand(module, assignment, mandatory(feedbackDao.getFeedback(feedbackId)))

	@RequestMapping(method = Array(RequestMethod.GET, RequestMethod.HEAD))
	def get(
		cmd: AdminGetSingleFeedbackFileCommand,
		@PathVariable("filename") filename: String,
		req: HttpServletRequest,
		res: HttpServletResponse
	) {
		cmd.callback = { (renderable) => fileServer.serve(renderable)(req, res) }
		cmd.apply().orElse { throw new ItemNotFoundException() }
	}
}



@Controller
@RequestMapping(Array("/admin/module/{module}/assignments/{assignment}/feedbacks.zip"))
class DownloadAllFeedbackController extends CourseworkController {

	var fileServer = Wire.auto[FileServer]

	@ModelAttribute def selectedFeedbacksCommand(@PathVariable("module") module: Module, @PathVariable("assignment") assignment: Assignment) =
		new DownloadSelectedFeedbackCommand(module, assignment)

	@RequestMapping
	def getSelected(command: DownloadSelectedFeedbackCommand)(implicit request: HttpServletRequest, response: HttpServletResponse) {
		command.apply { renderable =>
			fileServer.serve(renderable)
		}
	}
}

@Controller
@RequestMapping( value = Array("/admin/module/{module}/assignments/{assignment}/marker/{marker}/feedback/download/{feedbackId}/{filename}"))
class DownloadMarkerFeedbackController extends CourseworkController {

	var fileServer = Wire.auto[FileServer]
	var feedbackDao = Wire.auto[FeedbackDao]

	@RequestMapping
	def markerFeedback(
		@PathVariable module: Module,
		@PathVariable assignment: Assignment,
		@PathVariable feedbackId: String,
		@PathVariable filename: String,
		@PathVariable marker: User,
		req: HttpServletRequest,
		res: HttpServletResponse
	) = {
		feedbackDao.getMarkerFeedback(feedbackId) match {
			case Some(markerFeedback) =>
				val renderable = new AdminGetSingleMarkerFeedbackCommand(module, assignment, markerFeedback).apply()
				fileServer.serve(renderable)(req, res)
			case None => throw new ItemNotFoundException
		}
	}
}

@Controller
class DownloadMarkerFeebackFilesController extends BaseController {

	@Autowired var fileServer: FileServer = _

	@ModelAttribute def command( @PathVariable module: Module,
															 @PathVariable assignment: Assignment,
															 @PathVariable markerFeedback: String,
															 @PathVariable marker: User)
	= new DownloadMarkerFeedbackFilesCommand(module, assignment, markerFeedback)

	// the difference between the RequestMapping paths for these two methods is a bit subtle - the first has
	// attachments plural, the second has attachments singular.
	@RequestMapping(value = Array("/admin/module/{module}/assignments/{assignment}/marker/{marker}/feedback/download/{markerFeedback}/attachments/*"))
	def getAll(command: DownloadMarkerFeedbackFilesCommand)(implicit request: HttpServletRequest, response: HttpServletResponse) {
		getOne(command, null)
	}

	@RequestMapping(value = Array("/admin/module/{module}/assignments/{assignment}/marker/{marker}/feedback/download/{markerFeedback}/attachment/{filename}"))
	def getOne(command: DownloadMarkerFeedbackFilesCommand, @PathVariable("filename") filename: String)
						(implicit request: HttpServletRequest, response: HttpServletResponse): Unit = {
		// specify callback so that audit logging happens around file serving
		val prefixFileName = "-" + Option(filename).getOrElse(command.module.code)
		command.callback = {
			(renderable) => fileServer.serve(renderable, Some(s"${command.markerFeedback.feedback.universityId}$prefixFileName"))(request, response)
		}
		command.apply().orElse { throw new ItemNotFoundException() }
	}
}


@Controller
@RequestMapping(Array("/admin/module/{module}/assignments/{assignment}/marker/{marker}/{position}/feedbacks.zip"))
class DownloadFirstMarkersFeedbackController extends CourseworkController {

	var fileServer = Wire.auto[FileServer]

	@ModelAttribute("command")
	def downloadFirstMarkersFeedbackCommand(
		@PathVariable module: Module,
		@PathVariable assignment: Assignment,
		@PathVariable position: String,
		@PathVariable marker: User,
		user: CurrentUser
	) = {
		val feedbackPosition = position match {
			case "firstmarker" => FirstFeedback
			case "secondmarker" => SecondFeedback
		}
		DownloadMarkersFeedbackForPositionCommand(module, assignment, marker, user, feedbackPosition)
	}

	@RequestMapping
	def getSelected(@ModelAttribute("command") command: ApplyWithCallback[RenderableZip])
		(implicit request: HttpServletRequest, response: HttpServletResponse): Unit = {

		command.apply { renderable =>
			fileServer.serve(renderable)
		}

	}
}

@Controller
@RequestMapping(value = Array("/admin/module/{module}/assignments/{assignment}/feedback/download-zip/{filename}"))
class DownloadAllFeedback extends CourseworkController {
	var fileServer = Wire.auto[FileServer]
	
	@ModelAttribute def command(@PathVariable("module") module: Module, @PathVariable("assignment") assignment: Assignment) =
		new AdminGetAllFeedbackCommand(module, assignment)
	
	@RequestMapping
	def download(
			cmd: AdminGetAllFeedbackCommand, 
			@PathVariable("filename") filename: String)(implicit request: HttpServletRequest, response: HttpServletResponse) {
		fileServer.serve(cmd.apply())
	}
}

// A read only view of all feedback fields and attachments
@Controller
@RequestMapping(value = Array("/admin/module/{module}/assignments/{assignment}/feedback/summary/{student}"))
class FeedbackSummaryController extends CourseworkController {

	@ModelAttribute("command")
	def command(@PathVariable("module") module: Module, @PathVariable assignment: Assignment, @PathVariable student: User)
		= FeedbackSummaryCommand(assignment, student)

	@RequestMapping(method = Array(GET, HEAD))
	def showForm(@ModelAttribute("command") command: Appliable[Option[Feedback]]): Mav = {
		val feedback = command.apply()
		Mav("admin/assignments/feedback/read_only", "feedback" -> feedback).noLayout()
	}

}