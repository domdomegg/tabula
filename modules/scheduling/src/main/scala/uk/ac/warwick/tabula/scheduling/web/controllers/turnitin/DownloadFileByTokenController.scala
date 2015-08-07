package uk.ac.warwick.tabula.scheduling.web.controllers.turnitin

import org.springframework.stereotype.Controller
import uk.ac.warwick.tabula.services.fileserver.RenderableFile
import uk.ac.warwick.tabula.web.controllers.BaseController
import org.springframework.web.bind.annotation._
import uk.ac.warwick.tabula.helpers.Logging
import uk.ac.warwick.tabula.data.model._
import uk.ac.warwick.tabula.scheduling.commands.turnitin.DownloadFileByTokenCommand
import uk.ac.warwick.tabula.system.RenderableFileView
import uk.ac.warwick.tabula.commands.{SelfValidating, Appliable}
import javax.validation.Valid

@Controller
@RequestMapping(value = Array("/turnitin/submission/{submission}/attachment/{fileAttachment}"))
class DownloadFileByTokenController extends BaseController with Logging {

	validatesSelf[SelfValidating]

	@ModelAttribute("command")
	def command(
		@PathVariable submission: Submission,
		@PathVariable fileAttachment: FileAttachment,
		@RequestParam(value="token", required=true) token: FileAttachmentToken)	=	{
			mustBeLinked(mandatory(fileAttachment), mandatory(submission))
			DownloadFileByTokenCommand(submission, fileAttachment, token)
	}

	@RequestMapping(method = Array(GET))
	def serve(@Valid @ModelAttribute("command") command: Appliable[RenderableFile]) =
		Mav(new RenderableFileView(command.apply()))
}