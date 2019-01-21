package uk.ac.warwick.tabula.commands.cm2

import java.util.concurrent.TimeUnit

import org.joda.time.{DateTime, LocalDate, Seconds}
import uk.ac.warwick.tabula.JavaImports.{JList, JMap}
import uk.ac.warwick.tabula.WorkflowStages.StageProgress
import uk.ac.warwick.tabula._
import uk.ac.warwick.tabula.commands._
import uk.ac.warwick.tabula.commands.cm2.MarkingSummaryCommand.{MarkingSummaryMarkerInformation, _}
import uk.ac.warwick.tabula.commands.cm2.assignments.SubmissionAndFeedbackCommand
import uk.ac.warwick.tabula.data.model.{Assignment, Member}
import uk.ac.warwick.tabula.helpers.DateTimeOrdering._
import uk.ac.warwick.tabula.helpers.cm2.AssignmentSubmissionStudentInfo
import uk.ac.warwick.tabula.permissions.Permissions
import uk.ac.warwick.tabula.services._
import uk.ac.warwick.tabula.services.cm2.{AutowiringCM2WorkflowProgressServiceComponent, CM2WorkflowProgressServiceComponent, CM2WorkflowStages}
import uk.ac.warwick.tabula.services.permissions.{AutowiringCacheStrategyComponent, CacheStrategyComponent}
import uk.ac.warwick.tabula.system.permissions.{PermissionsChecking, PubliclyVisiblePermissions, RequiresPermissionsChecking}
import uk.ac.warwick.userlookup.User
import uk.ac.warwick.util.cache._
import uk.ac.warwick.util.collections.Pair

import scala.collection.JavaConverters._
import scala.util.{Failure, Success, Try}

object MarkingSummaryCommand {

	case class MarkingStage(
		stage: WorkflowStage,
		progress: Seq[MarkingStageProgress]
	) {
		def started: Boolean = progress.exists(_.progress.started)

		def completed: Boolean = progress.forall(_.progress.completed)
	}

	case class MarkingStageProgress(
		progress: StageProgress,
		count: Int
	)

	case class MarkingNextStage(
		stage: WorkflowStage,
		count: Int
	)

	case class MarkerAssignmentInfo(
		assignment: Assignment,
		feedbackDeadline: Option[LocalDate],
		extensionCount: Int,
		unsubmittedCount: Int,
		lateSubmissionsCount: Int,
		currentStages: Seq[MarkingStage],
		stages: Seq[MarkingStage],
		nextStages: Seq[MarkingNextStage]
	)

	case class MarkingSummaryMarkerInformation(
		upcomingAssignments: Seq[MarkerAssignmentInfo],
		actionRequiredAssignments: Seq[MarkerAssignmentInfo],
		noActionRequiredAssignments: Seq[MarkerAssignmentInfo],
		completedAssignments: Seq[MarkerAssignmentInfo]
	) {
		def isEmpty: Boolean = upcomingAssignments.isEmpty && actionRequiredAssignments.isEmpty && noActionRequiredAssignments.isEmpty && completedAssignments.isEmpty

		def allAssignments: Seq[MarkerAssignmentInfo] = upcomingAssignments ++ actionRequiredAssignments ++ noActionRequiredAssignments ++ completedAssignments
	}

	type Command = Appliable[MarkingSummaryMarkerInformation]

	def apply(marker: Member): Command with PermissionsChecking =
		new MarkingSummaryCommandInternal(MarkingSummaryMemberCommandTarget(marker))
			with ComposableCommand[MarkingSummaryMarkerInformation]
			with AutowiringModuleAndDepartmentServiceComponent
			with MarkingSummaryCommandStateMarkerMember
			with AutowiringAssessmentServiceComponent
			with AutowiringAssessmentMembershipServiceComponent
			with AutowiringCM2MarkingWorkflowServiceComponent
			with MarkingSummaryMarkerProgress
			with CommandWorkflowStudentsForAssignment
			with CachedMarkerWorkflowInformation
			with AutowiringCacheStrategyComponent
			with AutowiringCM2WorkflowProgressServiceComponent
			with MarkingSummaryPermissions with Unaudited with ReadOnly

	def apply(currentUser: CurrentUser): Command =
		new MarkingSummaryCommandInternal(MarkingSummaryCurrentUserCommandTarget(currentUser))
			with ComposableCommand[MarkingSummaryMarkerInformation]
			with AutowiringModuleAndDepartmentServiceComponent
			with MarkingSummaryCommandStateMarkerUser
			with AutowiringAssessmentServiceComponent
			with AutowiringAssessmentMembershipServiceComponent
			with AutowiringCM2MarkingWorkflowServiceComponent
			with MarkingSummaryMarkerProgress
			with CommandWorkflowStudentsForAssignment
			with CachedMarkerWorkflowInformation
			with AutowiringCacheStrategyComponent
			with AutowiringCM2WorkflowProgressServiceComponent
			with PubliclyVisiblePermissions with Unaudited with ReadOnly

}

sealed trait MarkingSummaryCommandTarget {
	def markerUser: User
}

case class MarkingSummaryMemberCommandTarget(marker: Member) extends MarkingSummaryCommandTarget {
	override def markerUser: User = marker.asSsoUser
}

case class MarkingSummaryCurrentUserCommandTarget(currentUser: CurrentUser) extends MarkingSummaryCommandTarget {
	override def markerUser: User = currentUser.apparentUser
}

trait MarkingSummaryCommandState {
	def target: MarkingSummaryCommandTarget

	def markerUser: User = target.markerUser
}

trait MarkingSummaryCommandStateMarkerUser extends MarkingSummaryCommandState {
	def target: MarkingSummaryCommandTarget
}

trait MarkingSummaryCommandStateMarkerMember extends MarkingSummaryCommandState {
	def target: MarkingSummaryMemberCommandTarget

	def marker: Member = target.marker
}

class MarkingSummaryCommandInternal[T <: MarkingSummaryCommandTarget](val target: T)
	extends CommandInternal[MarkingSummaryMarkerInformation]
		with MarkingSummaryMarkerAssignments
		with TaskBenchmarking {
	self: AssessmentServiceComponent
		with CM2MarkingWorkflowServiceComponent
		with MarkingSummaryCommandState
		with MarkingSummaryMarkerProgress =>

	override def applyInternal(): MarkingSummaryMarkerInformation = benchmarkTask("Get marker information") {
		MarkingSummaryMarkerInformation(
			markerUpcomingAssignments,
			markerActionRequiredAssignments,
			markerNoActionRequiredAssignments,
			markerCompletedAssignments
		)
	}
}

trait MarkingSummaryMarkerAssignmentList extends TaskBenchmarking {
	self: MarkingSummaryCommandState with AssessmentServiceComponent with CM2MarkingWorkflowServiceComponent =>

	protected lazy val allCM1MarkerAssignments: Seq[Assignment] = benchmarkTask("Get CM1 assignments for marking") {
		assessmentService.getAssignmentWhereMarker(markerUser, None) // Any academic year
	}

	protected lazy val allCM2MarkerAssignments: Seq[Assignment] = benchmarkTask("Get CM2 assignments for marking") {
		assessmentService.getCM2AssignmentsWhereMarker(markerUser, None) // Any academic year
			.filter { assignment => cm2MarkingWorkflowService.getAllStudentsForMarker(assignment, markerUser).nonEmpty }
	}

	lazy val isMarker: Boolean = allCM1MarkerAssignments.nonEmpty || allCM2MarkerAssignments.nonEmpty
}

trait MarkingSummaryMarkerAssignments extends MarkingSummaryMarkerAssignmentList {
	self: MarkingSummaryCommandState
		with AssessmentServiceComponent
		with CM2MarkingWorkflowServiceComponent
		with MarkingSummaryMarkerProgress =>

	// Upcoming - assignments involving the marker but that are waiting for someone else's action
	lazy val markerUpcomingAssignments: Seq[MarkerAssignmentInfo] = benchmarkTask("Get upcoming assignments") {
		// Include assignments where
		allMarkerAssignments.filter(info =>

			// all feedback items
			info.assignment.allFeedback.forall(feedback =>

				// have outstanding stages, all of which
				feedback.outstandingStages.asScala.forall(outstandingStage =>

					// are earlier in the workflow than the next stage this marker is involved with
					feedback.allMarkerFeedback.filter(_.marker == markerUser).filterNot(_.hasBeenModified)
						.map(_.stage).sortBy(_.order).headOption
						.exists(nextStageInvolvingMarker => outstandingStage.order < nextStageInvolvingMarker.order)
				)
			)
		)
	}

	// Action required - assignments which need an action
	lazy val markerActionRequiredAssignments: Seq[MarkerAssignmentInfo] = benchmarkTask("Get action required assignments") {
		// needed to retrieve CM1 assignments for marking
		lazy val cm1AssignmentsForMarking = benchmarkTask("Get CM1 assignments for marking") {
			assessmentService.getAssignmentWhereMarker(markerUser, None).sortBy(_.closeDate)
		}

		allMarkerAssignments.diff(markerUpcomingAssignments).diff(markerCompletedAssignments).filter { info =>
			info.assignment.allFeedback.exists(_.markingInProgress.exists(_.marker == markerUser)) || cm1AssignmentsForMarking.contains(info.assignment)
		}
	}

	// No action required - Assignments that are in the workflow but aren't in need of an action at this stage
	lazy val markerNoActionRequiredAssignments: Seq[MarkerAssignmentInfo] = benchmarkTask("Get no action required assignments") {
		allMarkerAssignments.diff(markerUpcomingAssignments).diff(markerCompletedAssignments).diff(markerActionRequiredAssignments)
	}

	// Completed - Assignments finished
	lazy val markerCompletedAssignments: Seq[MarkerAssignmentInfo] = benchmarkTask("Get completed assignments") {
		allMarkerAssignments.filter { info =>
			info.stages.nonEmpty && info.stages.last.progress.forall(_.progress.completed)
		}
	}

	private lazy val allEnhancedCM1MarkerAssignments: Seq[MarkerAssignmentInfo] = benchmarkTask("Enhance CM1 assignments for marking") {
		allCM1MarkerAssignments.map { assignment =>
			val markingWorkflow = assignment.markingWorkflow
			val students = markingWorkflow.getMarkersStudents(assignment, markerUser).toSet

			enhance(assignment, students)
		}
	}

	private lazy val allEnhancedCM2MarkerAssignments: Seq[MarkerAssignmentInfo] = benchmarkTask("Enhance CM2 assignments for marking") {
		allCM2MarkerAssignments.map { assignment =>
			enhance(
				assignment = assignment,
				students = cm2MarkingWorkflowService.getAllStudentsForMarker(assignment, markerUser).toSet
			)
		}
	}

	private lazy val allMarkerAssignments: Seq[MarkerAssignmentInfo] = benchmarkTask("Get assignments for marking") {
		(allEnhancedCM1MarkerAssignments ++ allEnhancedCM2MarkerAssignments)
			.sortBy(info => (info.assignment.openEnded, Option(info.assignment.closeDate)))
	}

}

trait MarkingSummaryMarkerProgress extends TaskBenchmarking {
	self: MarkerWorkflowInformation
		with CM2WorkflowProgressServiceComponent =>

	protected def enhance(assignment: Assignment, students: Set[User]): MarkerAssignmentInfo = benchmarkTask(s"Get progress information for ${assignment.name}") {
		val workflowStudents = markerWorkflowInformation(assignment).filterKeys(usercode => students.exists(_.getUserId == usercode))

		val allStages = workflowProgressService.getStagesFor(assignment).filter(_.markingRelated)

		val currentStages = allStages.flatMap { stage =>
			val name = stage.toString

			val progresses = workflowStudents.values.flatMap(_.stages.get(name)).filter(_.preconditionsMet)
			if (progresses.nonEmpty) {
				Seq(MarkingStage(
					stage,
					progresses.groupBy(identity).mapValues(_.size).toSeq.map { case (p, c) => MarkingStageProgress(p, c) }
				))
			} else {
				Nil
			}
		}

		val stages = allStages.flatMap { stage =>
			val name = stage.toString

			val progresses = workflowStudents.values.flatMap(_.stages.get(name))
			if (progresses.nonEmpty) {
				Seq(MarkingStage(
					stage,
					progresses.groupBy(identity).mapValues(_.size).toSeq.map { case (p, c) => MarkingStageProgress(p, c) }
				))
			} else {
				Nil
			}
		}

		val allNextStages =
			workflowStudents.values.flatMap(_.nextStage).groupBy(identity).mapValues(_.size)

		val nextStages =
			allStages.filter(allNextStages.contains).map { stage =>
				MarkingNextStage(
					stage,
					allNextStages(stage)
				)
			}

		MarkerAssignmentInfo(
			assignment,
			assignment.feedbackDeadline,
			extensionCount = assignment.extensions.asScala.count { e => students.exists(e.isForUser) },
			unsubmittedCount = students.count { u => !assignment.submissions.asScala.exists(_.isForUser(u)) },
			lateSubmissionsCount = assignment.submissions.asScala.count { s => s.isLate && students.exists(s.isForUser) },
			currentStages,
			stages,
			nextStages
		)
	}
}

trait MarkingSummaryPermissions extends RequiresPermissionsChecking {
	self: MarkingSummaryCommandStateMarkerMember =>

	override def permissionsCheck(p: PermissionsChecking): Unit = {
		p.PermissionCheck(Permissions.Profiles.Read.Coursework, marker)
	}
}

trait WorkflowStudentsForAssignment {
	def workflowStudentsFor(assignment: Assignment, students: Set[User] = Set.empty): Seq[AssignmentSubmissionStudentInfo]
}

trait CommandWorkflowStudentsForAssignment extends WorkflowStudentsForAssignment {
	def workflowStudentsFor(assignment: Assignment, students: Set[User] = Set.empty): Seq[AssignmentSubmissionStudentInfo] = {
		val cmd = SubmissionAndFeedbackCommand(assignment)
		cmd.students = JList(students.toSeq: _*)
		val result = cmd.apply()
		result.students
	}
}

object MarkerWorkflowInformation {
	type Usercode = String
	type StageName = String

	case class WorkflowProgressInformation(
		stages: Map[StageName, WorkflowStages.StageProgress],
		nextStage: Option[WorkflowStage]
	)

}

trait MarkerWorkflowInformation {

	import MarkerWorkflowInformation._

	def markerWorkflowInformation(assignment: Assignment): Map[Usercode, WorkflowProgressInformation]
}

object MarkerWorkflowCache {
	type AssignmentId = String
	type Json = String

	final val CacheName: String = "MarkerWorkflowInformation"

	/**
		* 7 days in seconds - note we can cache this for a reasonably long time because none of the *marking* related events are time based.
		*/
	final val CacheExpiryTime: Long = 60 * 60 * 24 * 7
}

trait MarkerWorkflowCache {
	self: WorkflowStudentsForAssignment with CacheStrategyComponent with AssessmentServiceComponent =>

	import MarkerWorkflowCache._
	import MarkerWorkflowInformation._

	private val markerWorkflowCacheEntryFactory = new CacheEntryFactory[AssignmentId, Json] {

		override def create(id: AssignmentId): Json = {
			val assignment = assessmentService.getAssignmentById(id).getOrElse {
				throw new CacheEntryUpdateException(s"Could not find assignment $id")
			}

			Try {
				toJson(workflowStudentsFor(assignment).map { student =>
					student.user.getUserId -> WorkflowProgressInformation(student.stages, student.nextStage)
				}.toMap)
			} match {
				case Success(info) => info
				case Failure(e) => throw new CacheEntryUpdateException(e)
			}
		}

		override def create(ids: JList[AssignmentId]): JMap[AssignmentId, Json] =
			JMap(ids.asScala.map(id => (id, create(id))): _*)

		override def isSupportsMultiLookups: Boolean = true

		override def shouldBeCached(info: Json): Boolean = true
	}

	private def toJson(markerInformation: Map[Usercode, WorkflowProgressInformation]): Json =
		JsonHelper.toJson(markerInformation.mapValues { progressInfo =>
			Map(
				"stages" -> progressInfo.stages,
				"nextStage" -> progressInfo.nextStage.map(_.toString)
			)
		})

	lazy val markerWorkflowCache: Cache[AssignmentId, Json] = {
		val cache = Caches.newCache(CacheName, markerWorkflowCacheEntryFactory, CacheExpiryTime, cacheStrategy)
		cache.setExpiryStrategy(new TTLCacheExpiryStrategy[AssignmentId, Json] {
			override def getTTL(entry: CacheEntry[AssignmentId, Json]): Pair[Number, TimeUnit] = {
				// Extend the cache time to the next deadline if it's shorter than the default cache expiry
				val seconds: Number = assessmentService.getAssignmentById(entry.getKey) match {
					case Some(assignment) if !assignment.isClosed && Option(assignment.closeDate).nonEmpty =>
						Seconds.secondsBetween(DateTime.now, assignment.closeDate).getSeconds

					case Some(assignment) =>
						val futureExtensionDate = assignment.extensions.asScala.flatMap(_.expiryDate).sorted.find(_.isAfterNow)

						futureExtensionDate.map[Number] { dt => Seconds.secondsBetween(DateTime.now, dt).getSeconds }
							.getOrElse(CacheExpiryTime)

					case _ => CacheExpiryTime
				}

				Pair.of(seconds, TimeUnit.SECONDS)
			}
		})
		cache
	}
}

trait CachedMarkerWorkflowInformation extends MarkerWorkflowInformation with MarkerWorkflowCache {
	self: WorkflowStudentsForAssignment with CacheStrategyComponent with AssessmentServiceComponent =>

	import MarkerWorkflowCache._
	import MarkerWorkflowInformation._

	private def fromJson(json: Json): Map[Usercode, WorkflowProgressInformation] =
		JsonHelper.toMap[Map[String, Any]](json).mapValues { progressInfo =>
			WorkflowProgressInformation(
				stages = progressInfo("stages").asInstanceOf[Map[StageName, Map[String, Any]]].map { case (stageName, progress) =>
					stageName -> StageProgress(
						stage = CM2WorkflowStages.of(stageName),
						started = progress("started").asInstanceOf[Boolean],
						messageCode = progress("messageCode").asInstanceOf[String],
						health = WorkflowStageHealth.fromCssClass(progress("health").asInstanceOf[Map[String, Any]]("cssClass").asInstanceOf[String]),
						completed = progress("completed").asInstanceOf[Boolean],
						preconditionsMet = progress("preconditionsMet").asInstanceOf[Boolean]
					)
				},
				nextStage = progressInfo.get("nextStage") match {
					case Some(null) => None
					case Some(nextStage: String) => Some(CM2WorkflowStages.of(nextStage))
					case _ => None
				}
			)
		}

	def markerWorkflowInformation(assignment: Assignment): Map[Usercode, WorkflowProgressInformation] =
		fromJson(markerWorkflowCache.get(assignment.id))
}

