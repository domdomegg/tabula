package uk.ac.warwick.tabula.coursework.web.controllers.admin


import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Controller
import org.springframework.validation.Errors
import org.springframework.web.bind.annotation.{ModelAttribute, PathVariable, RequestMapping}
import uk.ac.warwick.tabula.CurrentUser
import uk.ac.warwick.tabula.commands.Appliable
import uk.ac.warwick.tabula.coursework.commands.assignments.{MarkerAddMarksCommand, PostExtractValidation}
import uk.ac.warwick.tabula.coursework.commands.feedback.GenerateGradesFromMarkCommand
import uk.ac.warwick.tabula.coursework.services.docconversion.MarkItem
import uk.ac.warwick.tabula.coursework.web.Routes
import uk.ac.warwick.tabula.coursework.web.controllers.CourseworkController
import uk.ac.warwick.tabula.data.model.MarkingState._
import uk.ac.warwick.tabula.data.model.{Assignment, MarkerFeedback, Module}
import uk.ac.warwick.tabula.services.{AssignmentService, UserLookupService}
import uk.ac.warwick.userlookup.User

@Controller
@RequestMapping(value = Array("/admin/module/{module}/assignments/{assignment}/marker/{marker}/marks"))
class MarkerAddMarksController extends CourseworkController {

	@Autowired var assignmentService: AssignmentService = _
	@Autowired var userLookup: UserLookupService = _

	type MarkerAddMarksCommand = Appliable[List[MarkerFeedback]] with PostExtractValidation

	@ModelAttribute def command(
		@PathVariable("module") module: Module,
		@PathVariable("assignment") assignment: Assignment,
		@PathVariable("marker") marker: User,
		submitter: CurrentUser
	) = MarkerAddMarksCommand(
		mandatory(module),
		mandatory(assignment),
		marker,
		submitter,
		assignment.isFirstMarker(marker),
		GenerateGradesFromMarkCommand(mandatory(module), mandatory(assignment))
	)

	@RequestMapping(method = Array(HEAD, GET))
	def viewMarkUploadForm(
		@PathVariable module: Module,
		@PathVariable assignment: Assignment,
		@PathVariable marker: User,
		@ModelAttribute cmd: MarkerAddMarksCommand, errors: Errors
	) = {
		val submissions = assignment.getMarkersSubmissions(user.apparentUser)
		val markerFeedbacks = submissions.flatMap(s => assignment.getMarkerFeedbackForCurrentPosition(s.universityId, user.apparentUser))
		val filteredFeedbackId = markerFeedbacks.filter(_.state != MarkingCompleted).map(_.feedback.universityId)
		val filteredSubmissions = submissions.filter(s => filteredFeedbackId.contains(s.universityId))

		val marksToDisplay:Seq[MarkItem] = filteredSubmissions.map{ submission =>
			val universityId = submission.universityId
			val member = userLookup.getUserByWarwickUniId(universityId)

			val markerFeedback = markerFeedbacks.find(_.feedback.universityId == universityId)
			markerFeedback match  {
				case Some(f) if f.state != MarkingCompleted => noteMarkItem(member, Option(f))
				case None => noteMarkItem(member, None)
			}
		}.sortBy(_.universityId)

		Mav("admin/assignments/markerfeedback/marksform",
			"marksToDisplay" -> marksToDisplay,
			"isGradeValidation" -> module.adminDepartment.assignmentGradeValidation
		).crumbs(
			Breadcrumbs.Standard(s"Marking for ${assignment.name}", Some(Routes.admin.assignment.markerFeedback(assignment, marker)), "")
		)
	}

	private def noteMarkItem(member: User, markerFeedback: Option[MarkerFeedback]) = {
		val markItem = new MarkItem()
		markItem.universityId = member.getWarwickId
		markerFeedback match {
			case Some(f) =>
				markItem.actualMark = f.mark.map { _.toString }.getOrElse("")
				markItem.actualGrade = f.grade.getOrElse("")
			case None =>
				markItem.actualMark = ""
				markItem.actualGrade = ""
		}
		markItem
	}

	@RequestMapping(method = Array(POST), params = Array("!confirm"))
	def confirmBatchUpload(
		@PathVariable module: Module,
		@PathVariable assignment: Assignment,
		@PathVariable marker: User,
		@ModelAttribute cmd: MarkerAddMarksCommand,
		errors: Errors
	) = {
		if (errors.hasErrors) viewMarkUploadForm(module, assignment, marker, cmd, errors)
		else {
			bindAndValidate(assignment, cmd, errors)
			Mav("admin/assignments/markerfeedback/markspreview")
		}
	}

	@RequestMapping(method = Array(POST), params = Array("confirm=true"))
	def doUpload(
		@PathVariable module: Module,
		@PathVariable assignment: Assignment,
		@PathVariable marker: User,
		@ModelAttribute cmd: MarkerAddMarksCommand, errors: Errors
	) = {
		bindAndValidate(assignment, cmd, errors)
		cmd.apply()
		Redirect(Routes.admin.assignment.markerFeedback(assignment, marker))
	}

	private def bindAndValidate(assignment: Assignment, cmd: MarkerAddMarksCommand, errors: Errors) {
		cmd.postExtractValidation(errors)
	}
}

// Redirects users trying to access a marking workflow using the old style URL
@Controller
@RequestMapping(value = Array("/admin/module/{module}/assignments/{assignment}/marker/marks"))
class MarkerAddMarksControllerCurrentUser extends CourseworkController {
	@RequestMapping
	def redirect(@PathVariable assignment: Assignment, currentUser: CurrentUser) = {
		Redirect(Routes.admin.assignment.markerFeedback.marks(assignment, currentUser.apparentUser))
	}
}
