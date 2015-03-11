package uk.ac.warwick.tabula.coursework.web.controllers

import uk.ac.warwick.tabula.{Fixtures, Mockito, TestBase}
import uk.ac.warwick.tabula.data.model.{Assignment, Module}
import uk.ac.warwick.tabula.coursework.commands.feedback.GenericFeedbackCommand
import uk.ac.warwick.tabula.coursework.web.controllers.admin.GenericFeedbackController
import org.springframework.validation.Errors

class GenericFeedbackControllerTest extends TestBase with Mockito {

	trait Fixture {
		val department = Fixtures.department("hz", "Heron studies")
		val module = new Module
		module.code = "hn101"
		module.adminDepartment = department
		val assignment = new Assignment
		assignment.module = module

		val command = mock[GenericFeedbackCommand]
	}

	@Test def controllerShowsForm() {
		new Fixture {
			val controller = new GenericFeedbackController
			val mav = controller.showForm(assignment, command, null)
			mav.map("command") should be(command)
			mav.viewName should be ("admin/assignments/feedback/generic_feedback")
		}
	}

	@Test def controllerAppliesCommand() {
		new Fixture {
			val controller = new GenericFeedbackController { override val ajax = true }
			val errors = mock[Errors]

			val mav = controller.submit(assignment, command, errors)

			verify(command, times(1)).apply()

			mav.viewName should be ("ajax_success")
		}
	}

}
