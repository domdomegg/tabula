package uk.ac.warwick.tabula.web.controllers.coursework.admin

import org.springframework.stereotype.Controller
import org.springframework.validation.Errors
import org.springframework.web.bind.annotation.{ModelAttribute, PathVariable, RequestMapping}
import uk.ac.warwick.tabula.commands.coursework.feedback.CheckSitsUploadCommand
import uk.ac.warwick.tabula.commands.{Appliable, SelfValidating}
import uk.ac.warwick.tabula.data.model._
import uk.ac.warwick.tabula.services.AutowiringProfileServiceComponent
import uk.ac.warwick.tabula.web.Mav
import uk.ac.warwick.tabula.web.controllers.coursework.CourseworkController

import scala.collection.JavaConverters._

@Controller
@RequestMapping(Array("/coursework/admin/module/{module}/assignments/{assignment}/feedback/{feedback}/check-sits"))
class CheckAssignmentSitsUploadController extends CourseworkController with AutowiringProfileServiceComponent {

	validatesSelf[SelfValidating]

	@ModelAttribute("command")
	def command(@PathVariable module: Module, @PathVariable assignment: Assignment, @PathVariable feedback: AssignmentFeedback) =
		CheckSitsUploadCommand(mandatory(feedback))

	@RequestMapping
	def page(
		@ModelAttribute("command") cmd: Appliable[CheckSitsUploadCommand.Result],
		errors: Errors,
		@PathVariable feedback: AssignmentFeedback
	): Mav = {
		val sprCodes = profileService.getMemberByUniversityId(feedback.universityId).flatMap {
			case s: StudentMember => Some(s.freshStudentCourseDetails.map(_.sprCode))
			case _ => None
		}.getOrElse(Seq())
		if (errors.hasErrors) {
			Mav("coursework/admin/assignments/publish/check_sits")
		} else {
			Mav("coursework/admin/assignments/publish/check_sits",
				"result" -> cmd.apply(),
				"assessmentGroupPairs" -> feedback.assessmentGroups.asScala.map(assessGroup => (assessGroup.occurrence, assessGroup.assessmentComponent.sequence)),
				"sprCodes" -> sprCodes
			)
		}
	}

}
