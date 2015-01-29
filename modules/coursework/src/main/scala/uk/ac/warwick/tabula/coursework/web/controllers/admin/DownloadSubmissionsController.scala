package uk.ac.warwick.tabula.coursework.web.controllers.admin

import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.RequestMapping
import javax.servlet.http.HttpServletResponse
import uk.ac.warwick.tabula.coursework.commands.assignments.{DownloadFeedbackSheetsCommand, DownloadAllSubmissionsCommand, DownloadSubmissionsCommand}
import uk.ac.warwick.tabula.coursework.web.Routes
import uk.ac.warwick.tabula.services.fileserver.{RenderableZip, FileServer}
import uk.ac.warwick.tabula.coursework.web.controllers.CourseworkController
import uk.ac.warwick.spring.Wire
import uk.ac.warwick.tabula.services.{ProfileService, UserLookupService, SubmissionService}
import org.springframework.web.bind.annotation.PathVariable
import uk.ac.warwick.tabula.data.model.{Module, Assignment}
import uk.ac.warwick.tabula.coursework.commands.assignments.AdminGetSingleSubmissionCommand
import javax.servlet.http.HttpServletRequest
import org.springframework.web.bind.annotation.ModelAttribute
import uk.ac.warwick.tabula.coursework.commands.assignments.DownloadMarkersSubmissionsCommand
import uk.ac.warwick.tabula.coursework.commands.assignments.DownloadAttachmentCommand
import uk.ac.warwick.tabula.{CurrentUser, ItemNotFoundException}
import uk.ac.warwick.tabula.commands.ApplyWithCallback
import uk.ac.warwick.userlookup.{User, AnonymousUser}


@Controller
@RequestMapping(value = Array("/admin/module/{module}/assignments/{assignment}/submissions.zip"))
class DownloadSubmissionsController extends CourseworkController {

	var fileServer = Wire.auto[FileServer]

	@ModelAttribute def getSingleSubmissionCommand(@PathVariable("module") module: Module, @PathVariable("assignment") assignment: Assignment) =
		new DownloadSubmissionsCommand(module, assignment)

	@RequestMapping
	def download(command: DownloadSubmissionsCommand)(implicit request: HttpServletRequest, response: HttpServletResponse): Unit = {
		command.apply { renderable =>
			fileServer.serve(renderable)
		}
	}
}

@Controller
@RequestMapping(value = Array("/admin/module/{module}/assignments/{assignment}/marker/{marker}/submissions.zip"))
class DownloadMarkerSubmissionsController extends CourseworkController {

	var fileServer = Wire.auto[FileServer]
	
	@ModelAttribute("command")
	def getMarkersSubmissionCommand(
			@PathVariable("module") module: Module, 
			@PathVariable("assignment") assignment: Assignment,
			@PathVariable("marker") marker: User,
			submitter: CurrentUser
	) =	DownloadMarkersSubmissionsCommand(module, assignment, marker, submitter)

	@RequestMapping
	def downloadMarkersSubmissions(@ModelAttribute("command") command: ApplyWithCallback[RenderableZip])
		(implicit request: HttpServletRequest, response: HttpServletResponse): Unit = {
		command.apply { renderable =>
			fileServer.serve(renderable)
		}
	}
	
}

@Controller
@RequestMapping(value = Array("/admin/module/{module}/assignments/{assignment}/marker/submissions.zip"))
class DownloadMarkerSubmissionsControllerCurrentUser extends CourseworkController {
	@RequestMapping
	def redirect(@PathVariable assignment: Assignment, currentUser: CurrentUser) = {
		Redirect(Routes.admin.assignment.markerFeedback.submissions(assignment, currentUser.apparentUser))
	}
}

@Controller
@RequestMapping(value = Array("/admin/module/{module}/assignments/{assignment}/submissions/download-zip/{filename}"))
class DownloadAllSubmissionsController extends CourseworkController {

	var fileServer = Wire.auto[FileServer]

	@ModelAttribute def getAllSubmissionsSubmissionCommand(
			@PathVariable("module") module: Module, 
			@PathVariable("assignment") assignment: Assignment, 
			@PathVariable("filename") filename: String) = 
		new DownloadAllSubmissionsCommand(module, assignment, filename)

	@RequestMapping
	def downloadAll(command: DownloadAllSubmissionsCommand)(implicit request: HttpServletRequest, response: HttpServletResponse): Unit = {
		command.apply { renderable =>
			fileServer.serve(renderable)
		}
	}
	
}

@Controller
@RequestMapping(value = Array("/admin/module/{module}/assignments/{assignment}/submissions/download/{submissionId}/{filename}.zip"))
class DownloadSingleSubmissionController extends CourseworkController {

	var fileServer = Wire.auto[FileServer]
	var submissionService = Wire.auto[SubmissionService]
	var userLookup = Wire[UserLookupService]
	
	@ModelAttribute def getSingleSubmissionCommand(
			@PathVariable("module") module: Module,
			@PathVariable("assignment") assignment: Assignment, 
			@PathVariable("submissionId") submissionId: String) = 
		new AdminGetSingleSubmissionCommand(module, assignment, mandatory(submissionService.getSubmission(submissionId)))

	@RequestMapping
	def downloadSingle(
			cmd: AdminGetSingleSubmissionCommand, 
			@PathVariable("filename") filename: String)(implicit request: HttpServletRequest, response: HttpServletResponse): Unit = {
		val moduleCode = cmd.assignment.module.code
		val user = userLookup.getUserByUserId(cmd.submission.userId)

		val userIdentifier = if(!cmd.assignment.module.adminDepartment.showStudentName || (user==null || !user.isInstanceOf[AnonymousUser])) {
			cmd.submission.universityId
		} else {
			s"${user.getFullName} - ${cmd.submission.universityId}"
		}

		fileServer.serve(cmd.apply(), Some(s"$moduleCode - $userIdentifier - $filename.zip"))
	}
	
}

@Controller
@RequestMapping(value = Array("/admin/module/{module}/assignments/{assignment}/submissions/download/{submissionId}/{filename}"))
class DownloadSingleSubmissionFileController extends CourseworkController {

	var fileServer = Wire.auto[FileServer]
	var submissionService = Wire.auto[SubmissionService]
	var userLookup = Wire[UserLookupService]
	var profileService = Wire.auto[ProfileService]

	@ModelAttribute def getSingleSubmissionCommand(
			@PathVariable("module") module: Module, 
			@PathVariable("assignment") assignment: Assignment, 
			@PathVariable("submissionId") submissionId: String) = {
		val submission = submissionService.getSubmission(submissionId).get
		new DownloadAttachmentCommand(module, assignment, mandatory(submission), profileService.getMemberByUser(userLookup.getUserByUserId(submission.userId)).get)
	}

	@RequestMapping
	def downloadSingle(
			cmd: DownloadAttachmentCommand, 
			@PathVariable("filename") filename: String,
			request: HttpServletRequest, 
		response: HttpServletResponse): Unit = {
		val moduleCode = cmd.assignment.module.code
		val user = userLookup.getUserByUserId(cmd.submission.userId)

		val userIdentifier = if(!cmd.assignment.module.adminDepartment.showStudentName || (user==null || !user.isInstanceOf[AnonymousUser])) {
			cmd.submission.universityId
		} else {
			s"${user.getFullName} - ${cmd.submission.universityId}"
		}

		cmd.callback = { (renderable) => fileServer.serve(renderable, Some(s"$moduleCode - $userIdentifier - $filename"))(request, response) }
		cmd.apply().orElse { throw new ItemNotFoundException() }
	}

}

@Controller
@RequestMapping(value = Array("/admin/module/{module}/assignments/{assignment}"))
class DownloadFeedbackSheetsController extends CourseworkController {

	var fileServer = Wire.auto[FileServer]
	var userLookup = Wire.auto[UserLookupService]
		
	@ModelAttribute def feedbackSheetsCommand(@PathVariable("module") module: Module, @PathVariable("assignment") assignment: Assignment) =
		new DownloadFeedbackSheetsCommand(module, assignment)

	@RequestMapping(value = Array("/feedback-templates.zip"))
	def downloadFeedbackTemplatesOnly(command: DownloadFeedbackSheetsCommand)(implicit request: HttpServletRequest, response: HttpServletResponse): Unit = {
		command.apply { renderable =>
			fileServer.serve(renderable)
		}
	}

	@RequestMapping(value = Array("/marker-templates.zip"))
	def downloadMarkerFeedbackTemplates(command: DownloadFeedbackSheetsCommand)(implicit request: HttpServletRequest, response: HttpServletResponse): Unit = {
		val assignment = command.assignment

		val submissions = assignment.getMarkersSubmissions(user.apparentUser)

		val users = submissions.map(s => userLookup.getUserByUserId(s.userId))
		command.members = users
		command.apply { renderable =>
			fileServer.serve(renderable)
		}
	}

}
