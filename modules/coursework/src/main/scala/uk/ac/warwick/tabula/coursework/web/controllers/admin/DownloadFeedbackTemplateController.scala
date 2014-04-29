package uk.ac.warwick.tabula.coursework.web.controllers.admin

import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.{RequestMapping, ModelAttribute}
import uk.ac.warwick.tabula.CurrentUser
import uk.ac.warwick.tabula.ItemNotFoundException
import org.springframework.beans.factory.annotation.Autowired
import uk.ac.warwick.tabula.services.fileserver.FileServer
import scala.Array
import javax.servlet.http.HttpServletResponse
import uk.ac.warwick.tabula.coursework.web.controllers.CourseworkController
import uk.ac.warwick.tabula.coursework.commands.departments.DownloadFeedbackTemplateCommand
import javax.servlet.http.HttpServletRequest
import uk.ac.warwick.tabula.data.model.Department
import org.springframework.web.bind.annotation.PathVariable
import uk.ac.warwick.tabula.data.model.FeedbackTemplate

@Controller
@RequestMapping(Array("/admin/department/{department}/settings/feedback-templates/download/{template}/{filename}"))
class DownloadFeedbackTemplateController extends CourseworkController {
	
	@Autowired var fileServer:FileServer =_

	@ModelAttribute def command(
		@PathVariable("department") department: Department, 
		@PathVariable("template") template: FeedbackTemplate, 
		@PathVariable("filename") filename: String, 
		user:CurrentUser) = 
			new DownloadFeedbackTemplateCommand(department, template, filename, user)	

	@RequestMapping(method = Array(GET, HEAD))
	def getAttachment(command:DownloadFeedbackTemplateCommand, user:CurrentUser)(implicit request: HttpServletRequest, response: HttpServletResponse): Unit = {
		// specify callback so that audit logging happens around file serving
		command.callback = {(renderable) => fileServer.serve(renderable)}
		command.apply().orElse{ throw new ItemNotFoundException() }
	}

}
