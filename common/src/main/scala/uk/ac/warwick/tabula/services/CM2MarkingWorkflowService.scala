package uk.ac.warwick.tabula.services

import org.joda.time.DateTime
import org.springframework.stereotype.Service

import scala.collection.JavaConverters._
import uk.ac.warwick.spring.Wire
import uk.ac.warwick.tabula.AcademicYear
import uk.ac.warwick.tabula.data.AutowiringCM2MarkingWorkflowDaoComponent
import uk.ac.warwick.tabula.data.model.markingworkflow._
import uk.ac.warwick.tabula.data.model._
import uk.ac.warwick.tabula.JavaImports._
import uk.ac.warwick.userlookup.{AnonymousUser, User}

import scala.collection.immutable.{SortedMap, TreeMap}
import CM2MarkingWorkflowService._
import uk.ac.warwick.tabula.commands.TaskBenchmarking
import uk.ac.warwick.tabula.data.model.forms.SavedFormValue

object CM2MarkingWorkflowService {
  type Marker = User
  type Student = User
  type Allocations = Map[Marker, Set[Student]]
}

trait CM2MarkingWorkflowService extends WorkflowUserGroupHelpers {

  def save(workflow: CM2MarkingWorkflow): Unit

  def delete(workflow: CM2MarkingWorkflow): Unit

  def releaseForMarking(feedbacks: Seq[AssignmentFeedback]): Seq[AssignmentFeedback]

  def stopMarking(feedbacks: Seq[AssignmentFeedback]): Seq[AssignmentFeedback]

  /** All assignments using this marking workflow. */
  def getAssignmentsUsingMarkingWorkflow(workflow: CM2MarkingWorkflow): Seq[Assignment]

  def getAssignmentsUsingMarkingWorkflows(workflows: Seq[CM2MarkingWorkflow]): Seq[Assignment]

  // move feedback onto the next stage when all the current stages are finished - returns the markerFeedback for the next stage if applicable
  def progress(markerStage: MarkingWorkflowStage, feedbacks: Seq[Feedback]): Seq[MarkerFeedback]

  // finalise the specified feedback using the marker feedback from the specified stage - allows stages of the workflow to be skipped
  def finish(currentStage: MarkingWorkflowStage, feedbacks: Seq[Feedback]): Seq[Feedback]

  // copy the details from the given MarkerFeedback to it's parent feedback
  def finaliseFeedback(markerFeedback: MarkerFeedback): Feedback

  // essentially an undo for progressFeedback if it was done in error - not a normal step in the workflow
  def returnFeedback(stages: Seq[MarkingWorkflowStage], feedbacks: Seq[AssignmentFeedback]): Seq[MarkerFeedback]

  // add new markers for a workflow stage
  def addMarkersForStage(workflow: CM2MarkingWorkflow, markerStage: MarkingWorkflowStage, markers: Seq[Marker]): Unit

  // remove the specified markers from a stage - they cannot have any existing marker feedback
  def removeMarkersForStage(workflow: CM2MarkingWorkflow, markerStage: MarkingWorkflowStage, markers: Seq[Marker]): Unit

  // for a given assignment and workflow stage specify the markers for each student
  def allocateMarkersForStage(
    assignment: Assignment,
    markerStage: MarkingWorkflowStage,
    allocations: Allocations
  ): Seq[MarkerFeedback]

  // an anonymous Marker may be present in the map if a marker has been unassigned - these need to be handled
  def getMarkerAllocations(assignment: Assignment, stage: MarkingWorkflowStage): Allocations

  // an anonymous Marker may be present in the map if a marker has been unassigned - these need to be handled
  def feedbackByMarker(assignment: Assignment, stage: MarkingWorkflowStage): Map[Marker, Seq[MarkerFeedback]]

  def getAllFeedbackForMarker(assignment: Assignment, marker: User): SortedMap[MarkingWorkflowStage, Seq[MarkerFeedback]]

  def getAllStudentsForMarker(assignment: Assignment, marker: User): Seq[User]

  def getReusableWorkflows(department: Department, academicYear: AcademicYear): Seq[CM2MarkingWorkflow]
}

@Service
class CM2MarkingWorkflowServiceImpl extends CM2MarkingWorkflowService with AutowiringFeedbackServiceComponent
  with WorkflowUserGroupHelpersImpl with AutowiringCM2MarkingWorkflowDaoComponent with AutowiringZipServiceComponent with AutowiringUserLookupComponent with TaskBenchmarking {

  override def save(workflow: CM2MarkingWorkflow): Unit = markingWorkflowDao.saveOrUpdate(workflow)

  override def delete(workflow: CM2MarkingWorkflow): Unit = markingWorkflowDao.delete(workflow)

  override def releaseForMarking(feedbacks: Seq[AssignmentFeedback]): Seq[AssignmentFeedback] = feedbacks.map(f => {
    f.outstandingStages = f.assignment.cm2MarkingWorkflow.initialStages.toSet.asJava
    feedbackService.saveOrUpdate(f)
    f
  })

  override def stopMarking(feedbacks: Seq[AssignmentFeedback]): Seq[AssignmentFeedback] = feedbacks.map(f => {
    f.outstandingStages.clear()
    feedbackService.saveOrUpdate(f)
    f
  })

  def getAssignmentsUsingMarkingWorkflow(workflow: CM2MarkingWorkflow): Seq[Assignment] =
    markingWorkflowDao.getAssignmentsUsingMarkingWorkflow(workflow)

  def getAssignmentsUsingMarkingWorkflows(workflows: Seq[CM2MarkingWorkflow]): Seq[Assignment] =
    markingWorkflowDao.getAssignmentsUsingMarkingWorkflows(workflows)

  override def progress(currentStage: MarkingWorkflowStage, feedbacks: Seq[Feedback]): Seq[MarkerFeedback] = {
    // don't progress if nextStages is empty
    if (currentStage.nextStages.isEmpty)
      throw new IllegalArgumentException("cannot progress feedback past the final stage")

    // don't progress if outstanding stages doesn't contain the one we are trying to complete
    if (feedbacks.exists(f => !f.outstandingStages.asScala.contains(currentStage)))
      throw new IllegalArgumentException(s"some of the specified feedback doesn't have outstanding stage - $currentStage")

    feedbacks.flatMap(f => {
      val remainingStages = f.outstandingStages.asScala diff Set(currentStage)
      val releasedMarkerFeedback: Seq[MarkerFeedback] = if (remainingStages.isEmpty) {
        f.outstandingStages = currentStage.nextStages.toSet.asJava
        f.allMarkerFeedback.filter(mf => currentStage.nextStages.contains(mf.stage))
      } else {
        f.outstandingStages = remainingStages.asJava
        Nil
      }
      feedbackService.saveOrUpdate(f)
      releasedMarkerFeedback
    })
  }

  override def finish(currentStage: MarkingWorkflowStage, feedbacks: Seq[Feedback]): Seq[Feedback] = {

    if (currentStage.isInstanceOf[FinalStage])
      throw new IllegalArgumentException("cannot progress feedback past the final stage")

    // find marker feedback from the specified stage that we want to finalise - make sure it has content
    val mfAtStage = for {
      feedback <- feedbacks
      markerFeedback <- feedback.allMarkerFeedback if markerFeedback.stage == currentStage
    } yield markerFeedback

    val (mfToFinalise, mfToSkip) = mfAtStage.partition(_.hasContent)

    if (mfToSkip.nonEmpty)
      logger.info(s"No feedback is available for ${mfToSkip.map(_.student.getUserId).mkString(", ")} at ${currentStage.description} - skipping")

    // find the final stage for this workflow
    val finalStage: MarkingWorkflowStage = {
      def findFinalStage(stage: MarkingWorkflowStage): MarkingWorkflowStage = stage match {
        case f: FinalStage => f
        case s: MarkingWorkflowStage if s.nextStages.nonEmpty => findFinalStage(s.nextStages.head)
        case _ => throw new IllegalStateException(s"Dead end in workflow at stage ${stage.description}")
      }

      findFinalStage(currentStage)
    }

    // move the stage pointer on feedback to the end of the workflow and finalise the feedback
    mfToFinalise.map(mf => {
      mf.feedback.outstandingStages = JHashSet(finalStage)
      finaliseFeedback(mf)
    })

  }

  override def finaliseFeedback(markerFeedback: MarkerFeedback): Feedback = benchmarkTask(s"Finalise $markerFeedback") {
    val parent = markerFeedback.feedback

    parent.clearCustomFormValues()

    // save custom fields
    parent.customFormValues.addAll(markerFeedback.customFormValues.asScala.map { formValue =>
      val newValue = new SavedFormValue()
      newValue.name = formValue.name
      newValue.feedback = formValue.markerFeedback.feedback
      newValue.value = formValue.value
      newValue
    }.asJava)

    parent.actualGrade = markerFeedback.grade
    parent.actualMark = markerFeedback.mark

    parent.finalStage = markerFeedback.stage
    parent.updatedDate = DateTime.now

    // erase any existing attachments - these will be replaced
    parent.clearAttachments()

    markerFeedback.attachments.asScala.foreach(parent.addAttachment)
    zipService.invalidateIndividualFeedbackZip(parent)

    feedbackService.saveOrUpdate(parent)
    parent
  }

  override def returnFeedback(stages: Seq[MarkingWorkflowStage], feedbacks: Seq[AssignmentFeedback]): Seq[MarkerFeedback] = {
    if (stages.isEmpty)
      throw new IllegalArgumentException(s"Must supply a target stage to return to")

    if (stages.map(_.order).distinct.tail.nonEmpty)
      throw new IllegalArgumentException(s"The following stages aren't all in the same workflow step - ${stages.map(_.name).mkString(", ")}")

    feedbacks.flatMap(f => {
      val beforeStages = f.outstandingStages.asScala
      val targetOrder = stages.headOption.map(_.order).getOrElse(0)
      val afterStages = beforeStages.filter(_.order == targetOrder) ++ stages // keep any current stages at the same step in the workflow as the target
      f.outstandingStages = afterStages.toSet.asJava
      feedbackService.saveOrUpdate(f)
      f.allMarkerFeedback.filter(mf => afterStages.contains(mf.stage) && !beforeStages.contains(mf.stage))
    })
  }


  override def addMarkersForStage(workflow: CM2MarkingWorkflow, stage: MarkingWorkflowStage, markers: Seq[Marker]): Unit = {
    val markersForStage = workflow.stageMarkers.asScala.find(_.stage == stage).getOrElse(
      new StageMarkers(stage, workflow)
    )
    // usergroup handles dupes for us :)
    markers.foreach(markersForStage.markers.add)
    markingWorkflowDao.saveOrUpdate(markersForStage)
  }

  override def removeMarkersForStage(workflow: CM2MarkingWorkflow, stage: MarkingWorkflowStage, markers: Seq[Marker]): Unit = {
    val markersForStage = workflow.stageMarkers.asScala.find(_.stage == stage).getOrElse(
      throw new IllegalArgumentException("Can't remove markers for this stage as none exist")
    )
    markers.foreach(marker => {
      val existing = workflow.assignments.asScala.flatMap(a => feedbackByMarker(a, stage).getOrElse(marker, Nil))
      if (existing.nonEmpty)
        throw new IllegalArgumentException(s"Can't remove marker ${marker.getUserId} for this stage as they have marker feedback in progress")
      markersForStage.markers.remove(marker)
    })
    markingWorkflowDao.saveOrUpdate(markersForStage)
  }

  // for a given assignment and workflow stage specify the markers for each student
  override def allocateMarkersForStage(assignment: Assignment, stage: MarkingWorkflowStage, allocations: Allocations): Seq[MarkerFeedback] = {
    val workflow = assignment.cm2MarkingWorkflow
    if (workflow == null) throw new IllegalArgumentException("Can't assign markers for an assignment with no workflow")

    val existingMarkerFeedback = allMarkerFeedbackForStage(assignment, stage)

    // if any students did have a marker and now don't, remove the marker ID
    existingMarkerFeedback
      .filter(mf => !allocations.values.toSeq.flatten.contains(mf.student))
      .foreach(mf => {
        mf.marker = null
        feedbackService.save(mf)
      })

    for ((marker, students) <- allocations.toSeq; student <- students) yield {
      val parentFeedback = assignment.feedbacks.asScala.find(_.usercode == student.getUserId).getOrElse({
        val newFeedback = new AssignmentFeedback
        newFeedback.assignment = assignment
        newFeedback.uploaderId = marker.getUserId
        newFeedback.usercode = student.getUserId
        newFeedback._universityId = student.getWarwickId
        newFeedback.released = false
        newFeedback.createdDate = DateTime.now
        assignment.feedbacks.add(newFeedback)
        feedbackService.saveOrUpdate(newFeedback)
        newFeedback
      })

      val markerFeedback = existingMarkerFeedback.find(_.student == student).getOrElse({
        val newMarkerFeedback = new MarkerFeedback(parentFeedback)
        newMarkerFeedback.stage = stage
        newMarkerFeedback
      })

      // set the marker (possibly moving the MarkerFeedback to another marker - any existing data remains)
      markerFeedback.marker = if (marker.isFoundUser) marker else null
      feedbackService.save(markerFeedback)
      markerFeedback
    }
  }

  private def allMarkerFeedbackForStage(assignment: Assignment, stage: MarkingWorkflowStage): Seq[MarkerFeedback] =
    markingWorkflowDao.markerFeedbackForAssignmentAndStage(assignment, stage)

  override def getMarkerAllocations(assignment: Assignment, stage: MarkingWorkflowStage): Allocations = {
    val feedback = feedbackByMarker(assignment, stage)
    val usercodes = feedback.values.flatten.map(_.feedback.usercode).toSet

    val students = {
      if (usercodes.isEmpty) Map.empty[String, User]
      else usercodes.toSeq.grouped(100).map(userLookup.getUsersByUserIds).reduce(_ ++ _)
    }.withDefault(new AnonymousUser(_))

    feedback.map { case (marker, markerFeedbacks) =>
      marker -> markerFeedbacks.map { mf => students(mf.feedback.usercode) }.toSet
    }
  }

  // marker can be an Anon marker if marking
  override def feedbackByMarker(assignment: Assignment, stage: MarkingWorkflowStage): Map[Marker, Seq[MarkerFeedback]] = {
    allMarkerFeedbackForStage(assignment, stage).groupBy(_.marker)
  }

  override def getAllFeedbackForMarker(assignment: Assignment, marker: User): SortedMap[MarkingWorkflowStage, Seq[MarkerFeedback]] = {
    val unsortedMap = markingWorkflowDao.markerFeedbackForMarker(assignment, marker).groupBy(_.stage)
    TreeMap(unsortedMap.toSeq.sortBy { case (stage, _) => stage.order }: _*)
  }

  override def getAllStudentsForMarker(assignment: Assignment, marker: User): Seq[User] = {
    val usercodes = getAllFeedbackForMarker(assignment: Assignment, marker: User).values.flatten.map(_.feedback.usercode).toSet
    val students: Map[String, User] = {
      if (usercodes.isEmpty) Map.empty[String, User]
      else usercodes.toSeq.grouped(100).map(userLookup.getUsersByUserIds).reduce(_ ++ _)
    }.withDefault(new AnonymousUser(_))

    usercodes.map(students).toSeq
  }

  override def getReusableWorkflows(department: Department, academicYear: AcademicYear): Seq[CM2MarkingWorkflow] = {
    markingWorkflowDao.getReusableWorkflows(department, academicYear)
  }

}

trait WorkflowUserGroupHelpers {
  val markerHelper: UserGroupMembershipHelper[StageMarkers]
}

trait WorkflowUserGroupHelpersImpl extends WorkflowUserGroupHelpers {
  val markerHelper = new UserGroupMembershipHelper[StageMarkers]("_markers")
}

trait CM2MarkingWorkflowServiceComponent {
  def cm2MarkingWorkflowService: CM2MarkingWorkflowService
}

trait AutowiringCM2MarkingWorkflowServiceComponent extends CM2MarkingWorkflowServiceComponent {
  def cm2MarkingWorkflowService: CM2MarkingWorkflowService = Wire.auto[CM2MarkingWorkflowService]
}