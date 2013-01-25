package uk.ac.warwick.tabula.coursework.web.controllers

import org.springframework.stereotype.Controller
import uk.ac.warwick.tabula.web.Mav
import org.springframework.web.bind.annotation.RequestParam
import uk.ac.warwick.tabula.coursework.commands.feedback.RateFeedbackCommand
import org.springframework.web.bind.annotation.ModelAttribute
import uk.ac.warwick.tabula.actions.View
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import uk.ac.warwick.tabula.data.model.Assignment
import uk.ac.warwick.tabula.data.model.Feedback
import uk.ac.warwick.tabula.Features
import org.springframework.beans.factory.annotation.Autowired
import scala.reflect.BeanProperty
import uk.ac.warwick.tabula.data.model.Module
import org.springframework.web.bind.annotation.RequestMethod._
import org.springframework.validation.Errors
import uk.ac.warwick.tabula.CurrentUser
import uk.ac.warwick.tabula.data.FeedbackDao
import uk.ac.warwick.spring.Wire

@RequestMapping(Array("/module/{module}/{assignment}/rate"))
@Controller
class FeedbackRatingController extends CourseworkController {
	
	var feedbackDao = Wire.auto[FeedbackDao]

	hideDeletedItems

	@ModelAttribute def cmd(
		@PathVariable("assignment") assignment: Assignment,
		@PathVariable("module") module: Module,
		user: CurrentUser) = 
		new RateFeedbackCommand(module, assignment, mandatory(feedbackDao.getFeedbackByUniId(assignment, user.universityId).filter(_.released)))

	@RequestMapping(method = Array(GET, HEAD))
	def form(command: RateFeedbackCommand): Mav = 
		Mav("submit/rating").noLayoutIf(ajax)

	@RequestMapping(method = Array(POST))
	def submit(command: RateFeedbackCommand, errors: Errors): Mav = {
		command.validate(errors)
		if (errors.hasErrors) {
			form(command)
		} else {
			command.apply()
			Mav("submit/rating", "rated" -> true).noLayoutIf(ajax)
		}
	}

}