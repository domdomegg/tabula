package uk.ac.warwick.tabula.exams.web.controllers.admin

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Controller
import org.springframework.validation.Errors
import org.springframework.web.bind.annotation.{ModelAttribute, PathVariable, RequestMapping}
import uk.ac.warwick.tabula.{AcademicYear, CurrentUser}
import uk.ac.warwick.tabula.commands.Appliable
import uk.ac.warwick.tabula.coursework.commands.assignments.PostExtractValidation
import uk.ac.warwick.tabula.coursework.commands.feedback.GenerateGradesFromMarkCommand
import uk.ac.warwick.tabula.coursework.services.docconversion.MarkItem
import uk.ac.warwick.tabula.exams.web.Routes
import uk.ac.warwick.tabula.data.model.{Exam, Feedback, Module}
import uk.ac.warwick.tabula.exams.web.controllers.ExamsController
import uk.ac.warwick.tabula.services.{AssessmentMembershipService, FeedbackService}
import uk.ac.warwick.tabula.web.Mav
import uk.ac.warwick.userlookup.User
import uk.ac.warwick.tabula.exams.commands.ExamMarkerAddMarksCommand

@Controller
@RequestMapping(value = Array("/exams/admin/module/{module}/{academicYear}/exams/{exam}/marker/{marker}/marks"))
class ExamMarkerAddMarksController extends ExamsController {

	@Autowired var feedbackService: FeedbackService = _
	@Autowired var examMembershipService: AssessmentMembershipService = _

	type ExamMarkerAddMarksCommand = Appliable[Seq[Feedback]] with PostExtractValidation

	@ModelAttribute("adminAddMarksCommand")
	def command(@PathVariable module: Module,
							@PathVariable exam: Exam,
							@PathVariable marker: User,
							user: CurrentUser): ExamMarkerAddMarksCommand =
		ExamMarkerAddMarksCommand(mandatory(module), mandatory(exam), user, GenerateGradesFromMarkCommand(mandatory(module), mandatory(exam)))

	// Add the common breadcrumbs to the model.
	def crumbed(mav: Mav, module: Module, academicYear: AcademicYear) = mav.crumbs(
		Breadcrumbs.Department(module.adminDepartment, academicYear),
		Breadcrumbs.Module(module, academicYear)
	)

	@RequestMapping(method = Array(HEAD, GET))
	def viewMarkUploadForm(
		@PathVariable module: Module,
		@PathVariable exam: Exam,
		@PathVariable academicYear: AcademicYear,
		@PathVariable marker: User,
		@ModelAttribute("adminAddMarksCommand") cmd: ExamMarkerAddMarksCommand, errors: Errors
	) = {
		val members = examMembershipService.determineMembershipUsersWithOrderForMarker(exam, marker)

		val marksToDisplay = members.map { memberPair =>
			val member = memberPair._1
			val feedback = feedbackService.getStudentFeedback(exam, member.getWarwickId)
			noteMarkItem(member, feedback)
		}

		crumbed(Mav("exams/admin/marks/marksform",
			"marksToDisplay" -> marksToDisplay,
			"seatNumberMap" -> members.map(m => m._1.getWarwickId -> m._2).toMap,
			"isGradeValidation" -> module.adminDepartment.assignmentGradeValidation
		), module, academicYear)

	}

	private def noteMarkItem(member: User, feedback: Option[Feedback]) = {

		logger.debug("in noteMarkItem (logger.debug)")

		val markItem = new MarkItem()
		markItem.universityId = member.getWarwickId

		feedback match {
			case Some(f) =>
				markItem.actualMark = f.actualMark.map { _.toString }.getOrElse("")
				markItem.actualGrade = f.actualGrade.map { _.toString }.getOrElse("")
			case None =>
				markItem.actualMark = ""
				markItem.actualGrade = ""
		}

		markItem
	}

	@RequestMapping(method = Array(POST), params = Array("!confirm"))
	def confirmBatchUpload(
		@PathVariable module: Module,
		@PathVariable exam: Exam,
		@PathVariable academicYear: AcademicYear,
		@PathVariable marker: User,
		@ModelAttribute("adminAddMarksCommand") cmd: ExamMarkerAddMarksCommand, errors: Errors
	) = {
		if (errors.hasErrors) viewMarkUploadForm(module, exam, academicYear, marker, cmd, errors)
		else {
			bindAndValidate(module, cmd, errors)
			crumbed(Mav("exams/admin/marks/markspreview"), module, academicYear)
		}
	}

	@RequestMapping(method = Array(POST), params = Array("confirm=true"))
	def doUpload(
		@PathVariable module: Module,
		@PathVariable exam: Exam,
		@PathVariable academicYear: AcademicYear,
		@PathVariable marker: User,
		@ModelAttribute("adminAddMarksCommand") cmd: ExamMarkerAddMarksCommand, errors: Errors
	) = {
		bindAndValidate(module, cmd, errors)
		cmd.apply()
		Redirect(Routes.home)
	}

	private def bindAndValidate(module: Module, cmd: ExamMarkerAddMarksCommand, errors: Errors) {
		cmd.postExtractValidation(errors)
	}

}