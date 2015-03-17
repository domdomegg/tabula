package uk.ac.warwick.tabula.coursework.commands.assignments

import org.joda.time.DateTime
import org.springframework.util.StringUtils
import uk.ac.warwick.tabula.CurrentUser
import uk.ac.warwick.tabula.commands._
import uk.ac.warwick.tabula.coursework.services.docconversion.{AutowiringMarksExtractorComponent, MarkItem}
import uk.ac.warwick.tabula.data.HibernateHelpers
import uk.ac.warwick.tabula.data.Transactions._
import uk.ac.warwick.tabula.data.model._
import uk.ac.warwick.tabula.data.model.notifications.coursework.FeedbackChangeNotification
import uk.ac.warwick.tabula.permissions.Permissions
import uk.ac.warwick.tabula.services._
import uk.ac.warwick.tabula.system.permissions.{PermissionsChecking, PermissionsCheckingMethods, RequiresPermissionsChecking}

import scala.collection.JavaConversions._

object AdminAddMarksCommand {
	def apply(module: Module, assessment: Assessment, submitter: CurrentUser, gradeGenerator: GeneratesGradesFromMarks) =
		new AdminAddMarksCommandInternal(module, assessment, submitter, gradeGenerator)
			with AutowiringFeedbackServiceComponent
			with AutowiringUserLookupComponent
			with AutowiringMarksExtractorComponent
			with ComposableCommand[Seq[Feedback]]
			with AdminAddMarksDescription
			with AdminAddMarksPermissions
			with AdminAddMarksCommandValidation
			with AdminAddMarksNotifications
			with AdminAddMarksCommandState
			with PostExtractValidation
			with AddMarksCommandBindListener
}

class AdminAddMarksCommandInternal(val module: Module, val assessment: Assessment, val submitter: CurrentUser, val gradeGenerator: GeneratesGradesFromMarks)
	extends CommandInternal[Seq[Feedback]] {

	self: AdminAddMarksCommandState with FeedbackServiceComponent =>

	override def applyInternal(): List[Feedback] = transactional() {
		def saveFeedback(universityId: String, actualMark: String, actualGrade: String, isModified: Boolean) = {
			val feedback = assessment.findFeedback(universityId).getOrElse({
				val newFeedback = assessment match {
					case assignment: Assignment =>
						val f = new AssignmentFeedback
						f.assignment = assignment
						f
					case exam: Exam =>
						val f = new ExamFeedback
						f.exam = exam
						f
				}
				newFeedback.uploaderId = submitter.apparentId
				newFeedback.universityId = universityId
				newFeedback.released = false
				newFeedback.createdDate = DateTime.now
				newFeedback
			})

			feedback.actualMark = StringUtils.hasText(actualMark) match {
				case true => Some(actualMark.toInt)
				case false => None
			}

			feedback.actualGrade = Option(actualGrade)

			feedback.updatedDate = DateTime.now

			feedbackService.saveOrUpdate(feedback)

			if (feedback.released && isModified) {
				updatedReleasedFeedback = feedback +: updatedReleasedFeedback
			}

			feedback
		}

		// persist valid marks
		val markList = marks filter (_.isValid) map {
			(mark) => saveFeedback(mark.universityId, mark.actualMark, mark.actualGrade, mark.isModified)
		}

		markList.toList
	}
	
}

trait AdminAddMarksCommandValidation extends ValidatesMarkItem {
	
	self: AdminAddMarksCommandState with UserLookupComponent =>
	
	override def checkMarkUpdated(mark: MarkItem) {
		// Warn if marks for this student are already uploaded
		assessment.allFeedback.find { (feedback) => feedback.universityId == mark.universityId && (feedback.hasMark || feedback.hasGrade) } match {
			case Some(feedback) =>
				val markChanged = feedback.actualMark match {
					case Some(m) if m.toString != mark.actualMark => true
					case _ => false
				}
				val gradeChanged = {
					if (module.adminDepartment.assignmentGradeValidation) {
						markChanged
					} else {
						feedback.actualGrade match {
							case Some(g) if g != mark.actualGrade => true
							case _ => false
						}
					}
				}
				if (markChanged || gradeChanged){
					mark.isModified = true
					mark.isPublished = feedback.released
					mark.hasAdjustment = feedback.hasPublicOrPrivateAdjustments
				}
			case None =>
		}
	}
}

trait AdminAddMarksDescription extends Describable[Seq[Feedback]] {

	self: AdminAddMarksCommandState =>

	override lazy val eventName = "AdminAddMarks"

	override def describe(d: Description) {
		assessment match {
			case assignment: Assignment => d.assignment(assignment)
			case exam: Exam => d.exam(exam)
		}

	}
}

trait AdminAddMarksNotifications extends Notifies[Seq[Feedback], Feedback] {
	
	self: AdminAddMarksCommandState =>
	
	def emit(updatedFeedback: Seq[Feedback]) = updatedReleasedFeedback.flatMap { feedback => HibernateHelpers.initialiseAndUnproxy(feedback) match {
		case assignmentFeedback: AssignmentFeedback =>
			Option(Notification.init(new FeedbackChangeNotification, submitter.apparentUser, assignmentFeedback, assignmentFeedback.assignment))
		case _ =>
			None
	}}
}

trait AdminAddMarksPermissions extends RequiresPermissionsChecking with PermissionsCheckingMethods {

	self: AdminAddMarksCommandState =>

	override def permissionsCheck(p: PermissionsChecking) {
		p.mustBeLinked(assessment, module)
		p.PermissionCheck(Permissions.Marks.Create, assessment)
	}

}

trait AdminAddMarksCommandState extends AddMarksCommandState {
	def submitter: CurrentUser
	var updatedReleasedFeedback: Seq[Feedback] = Nil
}
