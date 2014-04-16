package uk.ac.warwick.tabula.attendance.commands

import uk.ac.warwick.tabula.commands._
import uk.ac.warwick.tabula.data.model.attendance.{AttendanceState, MonitoringCheckpoint, MonitoringPoint}
import uk.ac.warwick.tabula.data.model.{AttendanceNote, StudentMember}
import uk.ac.warwick.tabula.permissions.Permissions
import uk.ac.warwick.tabula.services._
import uk.ac.warwick.tabula.system.permissions.{PermissionsChecking, RequiresPermissionsChecking}
import org.springframework.validation.{BindingResult, Errors}
import uk.ac.warwick.tabula.{AcademicYear, ItemNotFoundException, CurrentUser}
import scala.collection.JavaConverters._
import uk.ac.warwick.tabula.JavaImports._
import uk.ac.warwick.tabula.helpers.LazyMaps
import org.joda.time.DateTime
import uk.ac.warwick.tabula.system.BindListener

object SetMonitoringCheckpointForStudentCommand {
	def apply(monitoringPoint: MonitoringPoint, student: StudentMember, user: CurrentUser) =
		new SetMonitoringCheckpointForStudentCommand(monitoringPoint, student, user)
			with ComposableCommand[Seq[MonitoringCheckpoint]]
			with SetMonitoringCheckpointForStudentCommandPermissions
			with SetMonitoringCheckpointForStudentCommandValidation
			with SetMonitoringPointForStudentDescription
			with SetMonitoringCheckpointForStudentState
			with AutowiringUserLookupComponent
			with AutowiringMonitoringPointServiceComponent
			with AutowiringTermServiceComponent
}

abstract class SetMonitoringCheckpointForStudentCommand(
	val monitoringPoint: MonitoringPoint, val student: StudentMember, user: CurrentUser
)	extends CommandInternal[Seq[MonitoringCheckpoint]] with PopulateOnForm with BindListener with CheckpointUpdatedDescription {

	self: SetMonitoringCheckpointForStudentState with UserLookupComponent with MonitoringPointServiceComponent =>

	def populate() {
		if (!monitoringPointService.getPointSetForStudent(student, set.academicYear).exists(
			s => s.points.asScala.contains(monitoringPoint))
		) {
			throw new ItemNotFoundException()
		}
		val checkpoints = monitoringPointService.getCheckpointsByStudent(Seq(monitoringPoint))
		studentsState = Map(student -> Map(monitoringPoint -> {
			val checkpointOption = checkpoints.find{
				case (s, checkpoint) => s == student && checkpoint.point == monitoringPoint
			}
			checkpointOption.map{case (_, checkpoint) => checkpoint.state}.getOrElse(null)
		}).asJava).asJava

	}

	def onBind(result: BindingResult) {
		val checkpoints = monitoringPointService.getCheckpointsByStudent(Seq(monitoringPoint))
		checkpointDescriptions = studentsState.asScala.map{
			case (s, pointMap) => s -> pointMap.asScala.map{
				case(point, state) => point -> {
					checkpoints.find{
						case (s1, checkpoint) => s1 == s && checkpoint.point == point
					}.map{case (_, checkpoint) => describeCheckpoint(checkpoint)}.getOrElse("")
				}
			}.toMap}.toMap
		attendanceNotes = monitoringPointService.findAttendanceNotes(Seq(student), Seq(monitoringPoint)).groupBy(_.student).map{
			case (s, pointMap) => s -> pointMap.groupBy(_.point).map{
				case (point, notes) => point -> notes.head
			}
		}.toMap

	}

	def applyInternal(): Seq[MonitoringCheckpoint] = {
		if (!monitoringPointService.getPointSetForStudent(student, set.academicYear).exists(
			s => s.points.asScala.contains(monitoringPoint))
		) {
			throw new ItemNotFoundException()
		}
		studentsState.asScala.flatMap{ case (_, pointMap) =>
			pointMap.asScala.flatMap{ case (point, state) =>
				if (state == null) {
					monitoringPointService.deleteCheckpoint(student, point)
					None
				} else {
					Option(monitoringPointService.saveOrUpdateCheckpointByUser(student, point, state, user))
				}
			}
		}.toSeq
	}
}

trait SetMonitoringCheckpointForStudentCommandValidation extends SelfValidating {
	self: SetMonitoringCheckpointForStudentState with TermServiceComponent with MonitoringPointServiceComponent =>

	def validate(errors: Errors) {

		val academicYear = templateMonitoringPoint.pointSet.academicYear
		val thisAcademicYear = AcademicYear.guessByDate(DateTime.now)
		val currentAcademicWeek = termService.getAcademicWeekForAcademicYear(DateTime.now(), academicYear)
		studentsState.asScala.foreach{ case(_, pointMap) =>
			val studentPointSet = monitoringPointService.getPointSetForStudent(student, academicYear)
			pointMap.asScala.foreach{ case(point, state) =>
				errors.pushNestedPath(s"studentsState[${student.universityId}][${point.id}]")
				// Check point is valid for student
				if (!studentPointSet.exists(s => s.points.asScala.contains(point))) {
					errors.rejectValue("", "monitoringPoint.invalidStudent")
				}	else {

					if (!nonReportedTerms.contains(
						termService.getTermFromAcademicWeekIncludingVacations(point.validFromWeek, point.pointSet.academicYear).getTermTypeAsString)
					){
						errors.rejectValue("", "monitoringCheckpoint.student.alreadyReportedThisTerm")
					}

					if (thisAcademicYear.startYear <= academicYear.startYear
						&& currentAcademicWeek < point.validFromWeek
						&& !(state == null || state == AttendanceState.MissedAuthorised)
					) {
						errors.rejectValue("", "monitoringCheckpoint.beforeValidFromWeek")
					}
				}
				errors.popNestedPath()
			}}
	}

}

trait SetMonitoringCheckpointForStudentCommandPermissions extends RequiresPermissionsChecking {
	self: SetMonitoringCheckpointForStudentState =>

	def permissionsCheck(p: PermissionsChecking) {
		p.PermissionCheck(Permissions.MonitoringPoints.Record, student)
	}
}


trait SetMonitoringPointForStudentDescription extends Describable[Seq[MonitoringCheckpoint]] {
	self: SetMonitoringCheckpointForStudentState =>

	override lazy val eventName = "SetMonitoringCheckpointForStudent"

	def describe(d: Description) {
		d.property("checkpoints", studentsState.asScala.map{ case (student, pointMap) =>
			student.universityId -> pointMap.asScala.map{ case(point, state) => point -> {
				if (state == null)
					"null"
				else
					state.dbValue
			}}
		})
	}
}


trait SetMonitoringCheckpointForStudentState  extends GroupMonitoringPointsByTerm with MonitoringPointServiceComponent {
	def monitoringPoint: MonitoringPoint
	def student: StudentMember
	lazy val templateMonitoringPoint = monitoringPoint

	var members: Seq[StudentMember] = _
	var studentsState: JMap[StudentMember, JMap[MonitoringPoint, AttendanceState]] =
		LazyMaps.create{student: StudentMember => JHashMap(): JMap[MonitoringPoint, AttendanceState] }.asJava
	var checkpointDescriptions: Map[StudentMember, Map[MonitoringPoint, String]] = _
	var attendanceNotes: Map[StudentMember, Map[MonitoringPoint, AttendanceNote]] = _
	var set = monitoringPoint.pointSet
	def nonReportedTerms = monitoringPointService.findNonReportedTerms(Seq(student), monitoringPoint.pointSet.academicYear)
}
