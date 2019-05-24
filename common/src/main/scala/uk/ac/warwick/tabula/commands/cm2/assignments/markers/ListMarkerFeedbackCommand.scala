package uk.ac.warwick.tabula.commands.cm2.assignments.markers

import uk.ac.warwick.tabula.JavaImports._
import uk.ac.warwick.tabula.commands._
import uk.ac.warwick.tabula.commands.cm2.assignments.markers.ListMarkerFeedbackCommand.EnhancedFeedbackForOrderAndStage
import uk.ac.warwick.tabula.commands.cm2.{CommandWorkflowStudentsForAssignment, WorkflowStudentsForAssignment}
import uk.ac.warwick.tabula.data.model.markingworkflow.MarkingWorkflowStage
import uk.ac.warwick.tabula.data.model.{Assignment, MarkerFeedback}
import uk.ac.warwick.tabula.helpers.UserOrderingByIds._
import uk.ac.warwick.tabula.helpers.cm2.SubmissionAndFeedbackInfoFilters.OverlapPlagiarismFilter
import uk.ac.warwick.tabula.helpers.cm2.{AssignmentSubmissionStudentInfo, SubmissionAndFeedbackInfoFilter, SubmissionAndFeedbackInfoMarkerFilter, WorkflowItems}
import uk.ac.warwick.tabula.permissions.Permissions
import uk.ac.warwick.tabula.services.cm2.{AutowiringCM2WorkflowProgressServiceComponent, CM2WorkflowProgressServiceComponent}
import uk.ac.warwick.tabula.services.{AutowiringCM2MarkingWorkflowServiceComponent, AutowiringUserLookupComponent, CM2MarkingWorkflowServiceComponent, UserLookupComponent}
import uk.ac.warwick.tabula.system.permissions.{PermissionsChecking, PermissionsCheckingMethods, RequiresPermissionsChecking}
import uk.ac.warwick.tabula.{CurrentUser, WorkflowStages}
import uk.ac.warwick.userlookup.{AnonymousUser, User}

import scala.collection.JavaConverters._
import scala.collection.immutable.SortedMap

case class EnhancedMarkerFeedback(
  markerFeedback: MarkerFeedback,
  workflowStudent: MarkingWorkflowStudent
) {
  def previousMarkerFeedback: Seq[MarkerFeedback] = {
    val previousStages = markerFeedback.stage.previousStages
    markerFeedback.feedback.allMarkerFeedback.filter(s => previousStages.contains(s.stage))
  }
}

case class MarkingWorkflowStudent(
  stages: Seq[WorkflowStages.StageProgress],
  info: AssignmentSubmissionStudentInfo
) {
  def coursework: WorkflowItems = info.coursework

  def assignment: Assignment = info.assignment

  def nextAction: Option[String] = stages.filterNot(_.skipped).filterNot(_.completed).headOption.map(_.stage.actionCode)
}

object ListMarkerFeedbackCommand {

  case class EnhancedFeedbackForOrderAndStage(
    hasFeedback: Boolean,
    enhancedFeedbackByStage: Map[MarkingWorkflowStage, Seq[EnhancedMarkerFeedback]]
  ) {
    lazy val feedbackByActionability: FeedbackByActionability = {
      enhancedFeedbackByStage.map { case (stage, values) =>
        FeedbackByActionability(
          readyToMark = values.filter(_.markerFeedback.feedback.outstandingStages.contains(stage)),
          notReadyToMark = values.filter(emf => emf.markerFeedback.feedback.currentStageIndex < stage.order),
          marked = values.filter(emf =>
            emf.markerFeedback.hasContent && // the marker has added content
              !emf.markerFeedback.feedback.outstandingStages.contains(stage) && // the current stage isn't outstanding
              !(emf.markerFeedback.feedback.currentStageIndex < stage.order) // marking hasn't been sent back to a previous stage
          )
        )
      }.foldLeft(FeedbackByActionability(Nil, Nil, Nil))((a, b) => a.merge(b))
    }

    def headerStage: MarkingWorkflowStage = enhancedFeedbackByStage.keys.head

    def numPreviousMarkers: Int = enhancedFeedbackByStage(headerStage)
      .map(_.previousMarkerFeedback.size)
      .reduceOption(_ max _)
      .getOrElse(0)
  }

  case class FeedbackByActionability(
    readyToMark: Seq[EnhancedMarkerFeedback],
    notReadyToMark: Seq[EnhancedMarkerFeedback],
    marked: Seq[EnhancedMarkerFeedback]
  ) {
    def all: Seq[EnhancedMarkerFeedback] = readyToMark ++ notReadyToMark ++ marked

    def merge(other: FeedbackByActionability): FeedbackByActionability = FeedbackByActionability(
      readyToMark = readyToMark ++ other.readyToMark,
      notReadyToMark = notReadyToMark ++ other.notReadyToMark,
      marked = marked ++ other.marked
    )
  }

  def apply(assignment: Assignment, marker: User, submitter: CurrentUser) = new ListMarkerFeedbackCommandInternal(assignment, marker, submitter)
    with ComposableCommand[Seq[EnhancedFeedbackForOrderAndStage]]
    with ListMarkerFeedbackPermissions
    with AutowiringCM2MarkingWorkflowServiceComponent
    with AutowiringCM2WorkflowProgressServiceComponent
    with AutowiringUserLookupComponent
    with MarkerProgress
    with CommandWorkflowStudentsForAssignment
    with Unaudited with ReadOnly
}

class ListMarkerFeedbackCommandInternal(val assignment: Assignment, val marker: User, val submitter: CurrentUser) extends CommandInternal[Seq[EnhancedFeedbackForOrderAndStage]]
  with ListMarkerFeedbackState {

  self: CM2MarkingWorkflowServiceComponent with CM2WorkflowProgressServiceComponent with MarkerProgress =>

  def applyInternal(): Seq[EnhancedFeedbackForOrderAndStage] = {
    val enhancedFeedbackByStage = enhance(assignment, cm2MarkingWorkflowService.getAllFeedbackForMarker(assignment, marker))
    val filteredEnhancedFeedbackByStage = enhancedFeedbackByStage.map { case (stage, feedback) =>
      val filtered = benchmarkTask(s"Do marker feedback filtering for ${stage.name}") {
        feedback.filter { emf =>
          val info = emf.workflowStudent.info
          val itemExistsInPlagiarismFilters = plagiarismFilters.asScala.isEmpty || plagiarismFilters.asScala.exists(_.predicate(info))
          val itemExistsInSubmissionStatesFilters = submissionStatesFilters.asScala.isEmpty || submissionStatesFilters.asScala.exists(_.predicate(info))
          val itemExistsInMarkerStatusesFilters = markerStateFilters.asScala.isEmpty || markerStateFilters.asScala.exists(_.predicate(info, marker))
          itemExistsInPlagiarismFilters && itemExistsInSubmissionStatesFilters && itemExistsInMarkerStatusesFilters
        }
      }
      stage -> filtered.sortBy(_.markerFeedback.student)
    }

    // squash stages with the same order
    val stages = filteredEnhancedFeedbackByStage
      .groupBy { case (stage, _) => stage.order }
      .map { case (_, map) => EnhancedFeedbackForOrderAndStage(map.values.flatten.nonEmpty, map) }
      .toSeq
      .sortBy(_.headerStage.order)

    if (activeWorkflowPosition == null) {
      val position: Option[JInteger] =
        stages.find(_.feedbackByActionability.readyToMark.nonEmpty)
          .orElse(stages.headOption)
          .map(_.headerStage.order)

      activeWorkflowPosition = position.orNull
    }

    stages
  }
}

trait ListMarkerFeedbackPermissions extends RequiresPermissionsChecking with PermissionsCheckingMethods {
  self: ListMarkerFeedbackState =>

  def permissionsCheck(p: PermissionsChecking) {
    p.PermissionCheck(Permissions.AssignmentMarkerFeedback.Manage, assignment)
    if (submitter.apparentUser != marker) {
      p.PermissionCheck(Permissions.Assignment.MarkOnBehalf, assignment)
    }
  }
}

trait ListMarkerFeedbackState extends CanProxy {
  val assignment: Assignment
  val marker: User
  val submitter: CurrentUser

  var plagiarismFilters: JList[SubmissionAndFeedbackInfoFilter] = JArrayList()
  var submissionStatesFilters: JList[SubmissionAndFeedbackInfoFilter] = JArrayList()
  var markerStateFilters: JList[SubmissionAndFeedbackInfoMarkerFilter] = JArrayList()
  var overlapFilter: OverlapPlagiarismFilter = new OverlapPlagiarismFilter
  var activeWorkflowPosition: JInteger = _
}

trait CanProxy {
  def marker: User

  def submitter: CurrentUser

  def isProxying: Boolean = marker != submitter.apparentUser
}

trait MarkerProgress extends TaskBenchmarking {

  self: WorkflowStudentsForAssignment with CM2WorkflowProgressServiceComponent with UserLookupComponent =>

  type FeedbackByStage = SortedMap[MarkingWorkflowStage, Seq[MarkerFeedback]]
  type EnhancedFeedbackByStage = SortedMap[MarkingWorkflowStage, Seq[EnhancedMarkerFeedback]]

  protected def enhance(assignment: Assignment, feedbackByStage: FeedbackByStage): EnhancedFeedbackByStage = benchmarkTask(s"Get workflow progress information for ${assignment.name}") {
    val allMarkingStages = workflowProgressService.getStagesFor(assignment).filter(_.markingRelated)
    val workflowStudents = workflowStudentsFor(assignment)

    val usercodes = feedbackByStage.values.flatten.map(_.feedback.usercode).toSet
    val students = {
      if (usercodes.isEmpty) Map.empty[String, User]
      else usercodes.toSeq.grouped(100).map(userLookup.getUsersByUserIds).reduce(_ ++ _)
    }.withDefault(new AnonymousUser(_))

    feedbackByStage.mapValues(mfs => mfs.flatMap(mf => {
      workflowStudents.find(_.user == students(mf.feedback.usercode)).map(ws => {
        val markingStages = allMarkingStages.flatMap(ms => ws.stages.get(ms.toString))
        EnhancedMarkerFeedback(mf, MarkingWorkflowStudent(markingStages, ws))
      })
    }))
  }
}