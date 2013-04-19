package uk.ac.warwick.tabula.profiles.web.controllers
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import uk.ac.warwick.spring.Wire
import uk.ac.warwick.tabula.data.model.Member
import uk.ac.warwick.tabula.profiles.commands.ViewProfilePhotoCommand
import uk.ac.warwick.tabula.services.fileserver.FileServer
import org.springframework.web.bind.annotation.RequestMethod
import uk.ac.warwick.tabula.profiles.commands.ViewPersonalTutorPhotoCommand

@Controller
@RequestMapping(value = Array("/view/photo/{member}.jpg"))
class PhotoController extends ProfilesController {
	
	var fileServer = Wire[FileServer]
	
	@ModelAttribute("viewProfilePhotoCommand") def command(@PathVariable("member") member: Member) = new ViewProfilePhotoCommand(member)

	@RequestMapping(method = Array(RequestMethod.GET, RequestMethod.HEAD))
	def getPhoto(@ModelAttribute("viewProfilePhotoCommand") command: ViewProfilePhotoCommand)(implicit request: HttpServletRequest, response: HttpServletResponse): Unit = {
		// specify callback so that audit logging happens around file serving
		command.apply { (renderable) => fileServer.stream(renderable) }
	}

}

@Controller
@RequestMapping(value = Array("/view/photo/{member}/tutor.jpg"))
class PersonalTutorPhotoController extends ProfilesController {
	
	var fileServer = Wire[FileServer]
	
	@ModelAttribute("viewPersonalTutorPhotoCommand") def command(@PathVariable("member") member: Member) = new ViewPersonalTutorPhotoCommand(member)

	@RequestMapping(method = Array(RequestMethod.GET, RequestMethod.HEAD))
	def getPhoto(@ModelAttribute("viewPersonalTutorPhotoCommand") command: ViewPersonalTutorPhotoCommand)(implicit request: HttpServletRequest, response: HttpServletResponse): Unit = {
		// specify callback so that audit logging happens around file serving
		command.apply { (renderable) => fileServer.stream(renderable) }
	}

}