package uk.ac.warwick.tabula.helpers.cm2

import org.joda.time.LocalDate
import uk.ac.warwick.tabula.CaseObjectEqualityFixes
import uk.ac.warwick.tabula.data.model.Assignment
import uk.ac.warwick.tabula.data.model.MarkingState.MarkingCompleted
import uk.ac.warwick.tabula.data.model.forms.ExtensionState
import uk.ac.warwick.tabula.data.model.markingworkflow.MarkingWorkflowStage.{SelectedModerationAdmin, SelectedModerationModerator}
import uk.ac.warwick.tabula.data.model.markingworkflow.MarkingWorkflowType.SelectedModeratedMarking
import uk.ac.warwick.tabula.data.model.markingworkflow.{MarkingWorkflowStage, MarkingWorkflowType}
import uk.ac.warwick.userlookup.User

import scala.collection.JavaConverters._

/**
  * Filters a set of "Student" case objects (which are a representation of the current
  * state of a single student's submission workflow on an assignment, containing the
  * submission, extension and feedback where available). Provides a predicate for
  * filtering Student objects, and an applies() method to see whether it is even relevant
  * for an assignment (for example, if an assignment doesn't take submissions, there's no
  * point offering a filter for Unsubmitted students).
  */


sealed abstract class SubmissionAndFeedbackInfoFilter extends CaseObjectEqualityFixes[SubmissionAndFeedbackInfoFilter] {
  def getName: String = SubmissionAndFeedbackInfoFilters.shortName(getClass)

  def description: String

  def predicateWithAdditionalFilters(item: AssignmentSubmissionStudentInfo, additionalFilters: Seq[SubmissionAndFeedbackInfoFilter]): Boolean = true

  def predicate(item: AssignmentSubmissionStudentInfo): Boolean

  def apply(assignment: Assignment): Boolean

}

sealed abstract class SubmissionAndFeedbackInfoMarkerFilter extends SubmissionAndFeedbackInfoFilter {

  def predicate(item: AssignmentSubmissionStudentInfo): Boolean = true

  def predicate(item: AssignmentSubmissionStudentInfo, marker: User): Boolean

  def apply(assignment: Assignment): Boolean = true
}


object SubmissionAndFeedbackInfoFilters {

  private val ObjectClassPrefix = SubmissionAndFeedbackInfoFilters.getClass.getName

  def shortName(clazz: Class[_ <: SubmissionAndFeedbackInfoFilter]): String
  = clazz.getName.substring(ObjectClassPrefix.length, clazz.getName.length - 1).replace('$', '.')

  def of(name: String): SubmissionAndFeedbackInfoFilter = {

    // CM2 Workflowfilter have different logic as they are dynamically generated unlike other fixed case classes
    val workflowFilter = SubmissionAndFeedbackInfoFilters.Statuses.allWorkflowFilters.get(name)

    workflowFilter.getOrElse(try {
      // Go through the magical hierarchy
      val clz = Class.forName(ObjectClassPrefix + name.replace('.', '$') + "$")
      clz.getDeclaredField("MODULE$").get(null).asInstanceOf[SubmissionAndFeedbackInfoFilter]
    } catch {
      case _: ClassNotFoundException => throw new IllegalArgumentException("CM2TestFilter " + name + " not recognised")
      case _: ClassCastException => throw new IllegalArgumentException("CM2TestFilter " + name + " is not an endpoint of the hierarchy")
    })
  }

  object SubmissionStates {

    case object Submitted extends SubmissionAndFeedbackInfoFilter {
      val description = "Submitted"

      def predicate(item: AssignmentSubmissionStudentInfo): Boolean = {
        item.coursework.enhancedSubmission.isDefined
      }

      def apply(assignment: Assignment): Boolean = assignment.collectSubmissions
    }

    case object Unsubmitted extends SubmissionAndFeedbackInfoFilter {
      val description = "No submission"

      def predicate(item: AssignmentSubmissionStudentInfo): Boolean = {
        item.coursework.enhancedSubmission.isEmpty
      }

      def apply(assignment: Assignment): Boolean = assignment.collectSubmissions
    }

    case object OnTime extends SubmissionAndFeedbackInfoFilter {
      val description = "Submitted on time"

      def predicate(item: AssignmentSubmissionStudentInfo): Boolean = {
        item.coursework.enhancedSubmission.exists(item => !item.submission.isLate && !item.submission.isAuthorisedLate)
      }

      def apply(assignment: Assignment): Boolean = assignment.collectSubmissions
    }

    case object WithExtension extends SubmissionAndFeedbackInfoFilter {
      val description = "Submitted with extension"

      def predicate(item: AssignmentSubmissionStudentInfo): Boolean = {
        item.coursework.enhancedExtension.isDefined
      }

      def apply(assignment: Assignment): Boolean = assignment.collectSubmissions && assignment.allowExtensions
    }

    case object LateSubmission extends SubmissionAndFeedbackInfoFilter {
      val description = "Late submission"

      def apply(assignment: Assignment): Boolean = assignment.collectSubmissions

      def predicate(item: AssignmentSubmissionStudentInfo): Boolean =
        item.coursework.enhancedSubmission.exists(item => item.submission.isLate && !item.submission.isAuthorisedLate)
    }

    case object DisabilityDisclosed extends SubmissionAndFeedbackInfoFilter {
      val description = "Disability disclosed"

      def apply(assignment: Assignment): Boolean = assignment.collectSubmissions

      def predicate(item: AssignmentSubmissionStudentInfo): Boolean =
        item.coursework.enhancedSubmission.exists(_.submission.useDisability)
    }

    case object ExtensionRequested extends SubmissionAndFeedbackInfoFilter {
      val description = "Extension requested"

      def apply(assignment: Assignment): Boolean = assignment.collectSubmissions && assignment.allowExtensions

      def predicate(item: AssignmentSubmissionStudentInfo): Boolean =
        item.coursework.enhancedExtension.isDefined && !item.coursework.enhancedExtension.get.extension.isManual
    }

    case object ExtensionDenied extends SubmissionAndFeedbackInfoFilter {
      val description = "Extension denied"

      def apply(assignment: Assignment): Boolean = assignment.collectSubmissions && assignment.allowExtensions

      def predicate(item: AssignmentSubmissionStudentInfo): Boolean =
        item.coursework.enhancedExtension.isDefined && (item.coursework.enhancedExtension.get.extension.state == ExtensionState.Rejected)
    }

    case object ExtensionGranted extends SubmissionAndFeedbackInfoFilter {
      val description = "Extension granted"

      def apply(assignment: Assignment): Boolean = assignment.collectSubmissions && assignment.allowExtensions

      def predicate(item: AssignmentSubmissionStudentInfo): Boolean =
        item.coursework.enhancedExtension.isDefined && (item.coursework.enhancedExtension.get.extension.state == ExtensionState.Approved)
    }

    lazy val allSubmissionStates = Seq(Submitted, Unsubmitted, LateSubmission, DisabilityDisclosed)
  }


  object PlagiarismStatuses {

    case object NotCheckedForPlagiarism extends SubmissionAndFeedbackInfoFilter {
      val description = "Unchecked"

      def predicate(item: AssignmentSubmissionStudentInfo): Boolean =
        item.coursework.enhancedSubmission.exists(!_.submission.hasOriginalityReport.booleanValue)

      def apply(assignment: Assignment): Boolean =
        assignment.collectSubmissions && assignment.module.adminDepartment.plagiarismDetectionEnabled
    }

    case object CheckedForPlagiarism extends SubmissionAndFeedbackInfoFilter {
      val description = "Checked"

      def predicate(item: AssignmentSubmissionStudentInfo): Boolean =
        item.coursework.enhancedSubmission.exists(_.submission.hasOriginalityReport.booleanValue)

      def apply(assignment: Assignment): Boolean =
        assignment.collectSubmissions && assignment.module.adminDepartment.plagiarismDetectionEnabled
    }

    case object MarkedPlagiarised extends SubmissionAndFeedbackInfoFilter {
      val description = "Suspected plagiarism"

      def predicate(item: AssignmentSubmissionStudentInfo): Boolean =
        item.coursework.enhancedSubmission.exists(_.submission.suspectPlagiarised.booleanValue)

      def apply(assignment: Assignment): Boolean = assignment.collectSubmissions
    }

    case object WithOverlapPercentage extends SubmissionAndFeedbackInfoFilter {
      val description = "Plagiarism overlap percentage between..."

      def predicate(item: AssignmentSubmissionStudentInfo): Boolean = true

      override def predicateWithAdditionalFilters(item: AssignmentSubmissionStudentInfo, additionalFilters: Seq[SubmissionAndFeedbackInfoFilter]): Boolean = {
        additionalFilters.exists {
          case additionalFilter: OverlapPlagiarismFilter =>
            additionalFilter.predicate(item)
          case _ => true
        }
      }

      def apply(assignment: Assignment): Boolean = assignment.collectSubmissions
    }

    lazy val allPlagiarismStatuses = Seq(NotCheckedForPlagiarism, CheckedForPlagiarism, MarkedPlagiarised, WithOverlapPercentage)
  }

  object ExtensionStatuses {

    case object NoExtension extends SubmissionAndFeedbackInfoFilter {
      override def description: String = "No extension"

      override def predicate(item: AssignmentSubmissionStudentInfo): Boolean = !item.coursework.enhancedExtension.map(_.extension).exists(_.relevant)

      def apply(assignment: Assignment): Boolean = assignment.extensionsPossible
    }

    case object ExtensionAwaitingReview extends SubmissionAndFeedbackInfoFilter {
      override def description: String = "Extension awaiting review"

      override def predicate(item: AssignmentSubmissionStudentInfo): Boolean = item.coursework.enhancedExtension.exists(_.extension.awaitingReview)

      def apply(assignment: Assignment): Boolean = assignment.extensionsPossible && assignment.module.adminDepartment.allowExtensionRequests
    }

    case object ExtensionApproved extends SubmissionAndFeedbackInfoFilter {
      override def description: String = "Extension approved"

      override def predicate(item: AssignmentSubmissionStudentInfo): Boolean = item.coursework.enhancedExtension.map(_.extension).filter(_.relevant).exists(_.approved)

      def apply(assignment: Assignment): Boolean = assignment.extensionsPossible
    }

    case object ExtensionRejected extends SubmissionAndFeedbackInfoFilter {
      override def description: String = "Extension rejected"

      override def predicate(item: AssignmentSubmissionStudentInfo): Boolean = item.coursework.enhancedExtension.exists(ee => ee.extension.rejected)

      def apply(assignment: Assignment): Boolean = assignment.extensionsPossible && assignment.module.adminDepartment.allowExtensionRequests
    }

    case object ExtensionRevoked extends SubmissionAndFeedbackInfoFilter {
      override def description: String = "Extension revoked"

      override def predicate(item: AssignmentSubmissionStudentInfo): Boolean = item.coursework.enhancedExtension.exists(ee => ee.extension.revoked)

      def apply(assignment: Assignment): Boolean = assignment.extensionsPossible
    }

    val allExtensionStatuses = Seq(NoExtension, ExtensionAwaitingReview, ExtensionApproved, ExtensionRejected, ExtensionRevoked)
  }

  object Statuses {

    case object NotReleasedForMarking extends SubmissionAndFeedbackInfoFilter {
      def description = "Not released to markers"

      def predicate(item: AssignmentSubmissionStudentInfo): Boolean =
        if (item.assignment.cm2Assignment) {
          item.coursework.enhancedFeedback.head.feedback.notReleasedToMarkers
        } else {
          !item.assignment.isReleasedForMarking(item.user.getUserId)
        }

      def apply(assignment: Assignment): Boolean = assignment.collectSubmissions && assignment.markingWorkflow != null
    }

    case object MarkedByFirst extends SubmissionAndFeedbackInfoFilter {
      def description = "Marked by first marker"

      def predicate(item: AssignmentSubmissionStudentInfo): Boolean =
        item.coursework.enhancedFeedback.exists(_.feedback.getFirstMarkerFeedback.exists(_.state == MarkingCompleted))

      def apply(assignment: Assignment): Boolean = assignment.collectSubmissions && assignment.markingWorkflow != null && !assignment.cm2Assignment
    }

    case object MarkedBySecond extends SubmissionAndFeedbackInfoFilter {
      def description = "Marked by second marker"

      def predicate(item: AssignmentSubmissionStudentInfo): Boolean =
        item.coursework.enhancedFeedback.exists(_.feedback.getSecondMarkerFeedback.exists(_.state == MarkingCompleted))

      def apply(assignment: Assignment): Boolean = assignment.collectSubmissions && assignment.markingWorkflow != null &&
        assignment.markingWorkflow.hasSecondMarker && !assignment.cm2Assignment
    }

    case object NoFeedback extends SubmissionAndFeedbackInfoFilter {
      def description = "No feedback"

      def predicate(item: AssignmentSubmissionStudentInfo): Boolean =
        if (item.assignment.cm2Assignment) {
          //submissions where the marking has not been finalised. If there is no marking workflow we still want this (just shows ones that the admin hasn't uploaded feedback for)
          !item.coursework.enhancedFeedback.exists(_.feedback.hasContent)
        } else {
          // existing cm1 filter logic as it is
          item.coursework.enhancedFeedback.forall(_.feedback.isPlaceholder)
        }

      def apply(assignment: Assignment) = true
    }

    case object AdjustedFeedback extends SubmissionAndFeedbackInfoFilter {
      def description = "Adjusted feedback"

      def predicate(item: AssignmentSubmissionStudentInfo): Boolean =
        item.coursework.enhancedFeedback.exists(_.feedback.latestPrivateOrNonPrivateAdjustment.isDefined)

      def apply(assignment: Assignment) = true
    }

    case object UnreleasedFeedback extends SubmissionAndFeedbackInfoFilter {
      def description = "Unreleased feedback"

      def predicate(item: AssignmentSubmissionStudentInfo): Boolean =
        item.coursework.enhancedFeedback.filterNot(_.feedback.isPlaceholder).exists(!_.feedback.released)

      def apply(assignment: Assignment) = true
    }

    case object LateFeedback extends SubmissionAndFeedbackInfoFilter {
      def description = "Late feedback"

      def predicate(item: AssignmentSubmissionStudentInfo): Boolean = (for {
        enhancedSubmission <- item.coursework.enhancedSubmission
        feedbackDeadline <- enhancedSubmission.submission.feedbackDeadline
        enhancedFeedback <- item.coursework.enhancedFeedback
      } yield {
        val feedback = enhancedFeedback.feedback

        if (feedback.released) {
          val feedbackReleasedDate = feedback.releasedDate.toLocalDate
          // Was the feedback released late?
          feedbackDeadline.isBefore(feedbackReleasedDate)
        } else {
          // Would the feedback be late if it were released now?
          feedbackDeadline.isBefore(LocalDate.now)
        }
      }).getOrElse(false) // There is no feedback deadline

      def apply(assignment: Assignment) = true
    }

    case object ReleasedFeedback extends SubmissionAndFeedbackInfoFilter {
      def description = "Released feedback"

      def predicate(item: AssignmentSubmissionStudentInfo): Boolean =
        if (item.assignment.cm2Assignment) {
          item.coursework.enhancedFeedback.exists(_.feedback.released)
        } else {
          item.coursework.enhancedFeedback.filterNot(_.feedback.isPlaceholder).exists(_.feedback.released)
        }

      def apply(assignment: Assignment) = true
    }

    case object SubmissionNotDownloaded extends SubmissionAndFeedbackInfoFilter {
      def description = "Submission not downloaded"

      def predicate(item: AssignmentSubmissionStudentInfo): Boolean =
        item.coursework.enhancedSubmission.exists(!_.downloaded)

      def apply(assignment: Assignment) = (!assignment.cm2Assignment && Option(assignment.markingWorkflow).isDefined) || (assignment.cm2Assignment && Option(assignment.cm2MarkingWorkflow).isDefined)
    }

    case object SubmissionDownloaded extends SubmissionAndFeedbackInfoFilter {
      def description = "Submission downloaded"

      def predicate(item: AssignmentSubmissionStudentInfo): Boolean =
        item.coursework.enhancedSubmission.exists(_.downloaded)

      def apply(assignment: Assignment) = (!assignment.cm2Assignment && Option(assignment.markingWorkflow).isDefined) || (assignment.cm2Assignment && Option(assignment.cm2MarkingWorkflow).isDefined)
    }

    case object FeedbackNotDownloaded extends SubmissionAndFeedbackInfoFilter {
      def description = "Feedback not downloaded"

      def predicate(item: AssignmentSubmissionStudentInfo): Boolean =
        if (item.assignment.cm2Assignment) {
          !item.coursework.enhancedFeedback.exists { i => i.downloaded || (i.feedback.released && !i.feedback.hasAttachments && i.onlineViewed) }
        } else {
          item.coursework.enhancedFeedback.filterNot(_.feedback.isPlaceholder).exists(!_.downloaded)
        }

      def apply(assignment: Assignment) = true
    }

    case object FeedbackDownloaded extends SubmissionAndFeedbackInfoFilter {
      def description = "Feedback downloaded"

      def predicate(item: AssignmentSubmissionStudentInfo): Boolean =
        if (item.assignment.cm2Assignment) {
          item.coursework.enhancedFeedback.exists { i => i.downloaded || (i.feedback.released && !i.feedback.hasAttachments && i.onlineViewed) }
        } else {
          item.coursework.enhancedFeedback.filterNot(_.feedback.isPlaceholder).exists(_.downloaded)
        }

      def apply(assignment: Assignment) = true
    }

    // Filter options for cm2 workflows
    def allWorkflowFilters: Map[String, SubmissionAndFeedbackInfoFilter] = {
      case class WorkflowFilter(stage: MarkingWorkflowStage) extends SubmissionAndFeedbackInfoFilter {

        override def getName: String = stage.name

        override def description: String = s"${stage.pastVerb.capitalize} by ${stage.description.toLowerCase}"

        override def predicate(item: AssignmentSubmissionStudentInfo): Boolean = item.coursework.enhancedFeedback.isDefined &&
          item.coursework.enhancedFeedback.get.feedback.isMarkedByStage(stage)

        override def apply(assignment: Assignment): Boolean = assignment.cm2MarkingWorkflow != null && assignment.cm2MarkingWorkflow.workflowType.allStages.contains(stage)
      }

      val allPossibleStages = MarkingWorkflowType.allPossibleStages.values.flatten.toSeq
        .filterNot(_ == SelectedModerationAdmin) // exclude this stage so it can't be confused with SelectedForModeration
      allPossibleStages.sortBy(s => s.order).map(s => s.name -> WorkflowFilter(s)).distinct.toMap

    }

    lazy val allStatuses: Seq[SubmissionAndFeedbackInfoFilter] = Seq(NotReleasedForMarking, MarkedByFirst, MarkedBySecond, NoFeedback, AdjustedFeedback, UnreleasedFeedback, LateFeedback, ReleasedFeedback, SubmissionNotDownloaded, SubmissionDownloaded, FeedbackNotDownloaded, FeedbackDownloaded, SelectedForModeration) ++ allWorkflowFilters.values

    // marker specific filters
    case object MarkedByMarker extends SubmissionAndFeedbackInfoMarkerFilter {
      def description = "Marked"

      def predicate(item: AssignmentSubmissionStudentInfo, marker: User): Boolean =
        item.coursework.enhancedFeedback.exists(_.feedback.allMarkerFeedback.exists(mf => mf.marker == marker && mf.hasMarkOrGrade))

      override def apply(assignment: Assignment): Boolean = assignment.collectMarks
    }

    case object NotMarkedByMarker extends SubmissionAndFeedbackInfoMarkerFilter {
      def description = "Not marked"

      def predicate(item: AssignmentSubmissionStudentInfo, marker: User): Boolean =
        item.coursework.enhancedFeedback.exists(ef => !ef.feedback.markerFeedback.asScala.exists(mf => mf.marker == marker && mf.hasMarkOrGrade))

      override def apply(assignment: Assignment): Boolean = assignment.collectMarks
    }

    case object FeedbackByMarker extends SubmissionAndFeedbackInfoMarkerFilter {
      def description = "Feedback"

      def predicate(item: AssignmentSubmissionStudentInfo, marker: User): Boolean =
        item.coursework.enhancedFeedback.exists(_.feedback.allMarkerFeedback.exists(mf => mf.marker == marker && mf.hasFeedbackOrComments))
    }

    case object NoFeedbackByMarker extends SubmissionAndFeedbackInfoMarkerFilter {
      def description = "No feedback"

      def predicate(item: AssignmentSubmissionStudentInfo, marker: User): Boolean =
        item.coursework.enhancedFeedback.exists(ef => !ef.feedback.markerFeedback.asScala.exists(mf => mf.marker == marker && mf.hasFeedbackOrComments))
    }

    case object NotSentByMarker extends SubmissionAndFeedbackInfoMarkerFilter {
      def description = "Not sent"

      def predicate(item: AssignmentSubmissionStudentInfo, marker: User): Boolean = item.coursework.enhancedFeedback.exists(ef => {
        val outstandingStages = ef.feedback.outstandingStages.asScala
        ef.feedback.allMarkerFeedback.exists(mf => mf.marker == marker && mf.hasContent && outstandingStages.contains(mf.stage))
      })
    }

    case object SelectedForModeration extends SubmissionAndFeedbackInfoFilter {
      override def description: String = "Selected for moderation"

      override def apply(assignment: Assignment): Boolean = assignment.cm2MarkingWorkflow.workflowType == SelectedModeratedMarking

      override def predicate(item: AssignmentSubmissionStudentInfo): Boolean = item.coursework.enhancedFeedback.exists(ef => ef.feedback.outstandingStages.asScala.contains(SelectedModerationModerator) || ef.feedback.wasModerated)
    }
  }


  class OverlapPlagiarismFilter extends SubmissionAndFeedbackInfoFilter {
    var min: Int = _
    var max: Int = _

    override def description: String = "Between two numbers"

    def predicate(item: AssignmentSubmissionStudentInfo): Boolean = {
      item.coursework.enhancedSubmission.exists(item => {
        item.submission.allAttachments
          .flatMap(a => Option(a.originalityReport))
          .flatMap(_.overlap)
          .map(overlap => overlap >= min && overlap <= max)
          .exists(b => b)
      })

    }

    def apply(assignment: Assignment): Boolean = assignment.collectSubmissions && assignment.module.adminDepartment.plagiarismDetectionEnabled
  }


}