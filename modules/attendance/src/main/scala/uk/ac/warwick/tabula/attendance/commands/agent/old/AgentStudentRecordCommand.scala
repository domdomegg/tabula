package uk.ac.warwick.tabula.attendance.commands.agent.old

import org.joda.time.DateTime
import org.springframework.validation.{BindingResult, Errors}
import uk.ac.warwick.tabula.JavaImports._
import uk.ac.warwick.tabula.attendance.commands.old.{CheckpointUpdatedDescription, GroupMonitoringPointsByTerm}
import uk.ac.warwick.tabula.commands._
import uk.ac.warwick.tabula.data.model.attendance.{AttendanceState, MonitoringCheckpoint, MonitoringPoint}
import uk.ac.warwick.tabula.data.model.{AttendanceNote, Member, StudentMember, StudentRelationshipType}
import uk.ac.warwick.tabula.permissions.Permissions
import uk.ac.warwick.tabula.services._
import uk.ac.warwick.tabula.system.BindListener
import uk.ac.warwick.tabula.system.permissions.{PermissionsChecking, RequiresPermissionsChecking}
import uk.ac.warwick.tabula.{AcademicYear, ItemNotFoundException}

import scala.collection.JavaConverters._

object AgentStudentRecordCommand {
	def apply(agent: Member, relationshipType: StudentRelationshipType, student: StudentMember, academicYearOption: Option[AcademicYear]) =
		new AgentStudentRecordCommand(agent, relationshipType, student, academicYearOption)
		with ComposableCommand[Seq[MonitoringCheckpoint]]
		with AgentStudentRecordPermissions
		with AgentStudentRecordDescription
		with AgentStudentRecordValidation
		with AutowiringMonitoringPointServiceComponent
		with AutowiringUserLookupComponent
		with AutowiringTermServiceComponent
		with GroupMonitoringPointsByTerm
}

abstract class AgentStudentRecordCommand(val agent: Member, val relationshipType: StudentRelationshipType,
	val student: StudentMember, val academicYearOption: Option[AcademicYear]
) extends CommandInternal[Seq[MonitoringCheckpoint]] with PopulateOnForm with BindListener
	with CheckpointUpdatedDescription with AgentStudentRecordCommandState {

	this: MonitoringPointServiceComponent with UserLookupComponent =>

	def populate() = {
		val checkpoints = monitoringPointService.getCheckpoints(Seq(student), pointSet)(student)
		checkpointMap = checkpoints.map{ case(point, checkpointOption) =>
			point -> checkpointOption.map(c => c.state).getOrElse(null)
		}.asJava
	}

	def onBind(result: BindingResult) = {
		val checkpoints = monitoringPointService.getCheckpoints(Seq(student), pointSet)(student)
		attendanceNotes = monitoringPointService.findAttendanceNotes(Seq(student), pointSet.points.asScala)
			.map{ note =>	note.point -> note }.toMap
		checkpointDescriptions = checkpoints.map{case (point, checkpointOption) =>
			point -> checkpointOption.map{c => describeCheckpoint(c)}.getOrElse("")
		}
	}

	def applyInternal() = {
		checkpointMap.asScala.flatMap{case(point, state) =>
			if (state == null) {
				monitoringPointService.deleteCheckpoint(student, point)
				None
			} else {
				Option(monitoringPointService.saveOrUpdateCheckpointByMember(student, point, state, agent))
			}
		}.toSeq
	}

}

trait AgentStudentRecordValidation extends SelfValidating {
	self: AgentStudentRecordCommandState =>

	override def validate(errors: Errors) = {
		val currentAcademicWeek = termService.getAcademicWeekForAcademicYear(DateTime.now(), pointSet.academicYear)
		val points = pointSet.points.asScala
		checkpointMap.asScala.foreach{case (point, state) =>
			errors.pushNestedPath(s"checkpointMap[${point.id}]")
			if (!points.contains(point)) {
				errors.rejectValue("", "monitoringPointSet.invalidPoint")
			}
			if (!nonReportedTerms.contains(termService.getTermFromAcademicWeekIncludingVacations(point.validFromWeek, pointSet.academicYear).getTermTypeAsString)){
				errors.rejectValue("", "monitoringCheckpoint.student.alreadyReportedThisTerm")
			}

			if (thisAcademicYear.startYear <= pointSet.academicYear.startYear
				&& currentAcademicWeek < point.validFromWeek
				&& !(state == null || state == AttendanceState.MissedAuthorised)
			) {
				errors.rejectValue("", "monitoringCheckpoint.beforeValidFromWeek")
			}
			errors.popNestedPath()
		}}
}

trait AgentStudentRecordPermissions extends RequiresPermissionsChecking with PermissionsChecking {
	this: AgentStudentRecordCommandState =>

	def permissionsCheck(p: PermissionsChecking) {
		p.PermissionCheck(Permissions.MonitoringPoints.Record, student)
	}
}

trait AgentStudentRecordDescription extends Describable[Seq[MonitoringCheckpoint]] {
	self: AgentStudentRecordCommandState =>

	override lazy val eventName = "AgentStudentRecordCheckpoints"

	def describe(d: Description) {
		d.monitoringPointSet(pointSet)
		d.studentIds(Seq(student.universityId))
		d.property("checkpoints", checkpointMap.asScala.map{ case (point, state) =>
			if (state == null)
				point.id -> "null"
			else
				point.id -> state.dbValue
		})
	}
}

trait AgentStudentRecordCommandState extends GroupMonitoringPointsByTerm with MonitoringPointServiceComponent {
	def agent: Member
	def relationshipType: StudentRelationshipType
	def student: StudentMember
	def academicYearOption: Option[AcademicYear]
	val thisAcademicYear = AcademicYear.guessByDate(new DateTime())
	val academicYear = academicYearOption.getOrElse(thisAcademicYear)
	lazy val pointSet = monitoringPointService.getPointSetForStudent(student, academicYear).getOrElse(throw new ItemNotFoundException)

	var checkpointMap: JMap[MonitoringPoint, AttendanceState] =  JHashMap()
	var checkpointDescriptions: Map[MonitoringPoint, String] = _
	var attendanceNotes: Map[MonitoringPoint, AttendanceNote] = _

	def monitoringPointsByTerm = groupByTerm(pointSet.points.asScala, pointSet.academicYear)
	def nonReportedTerms = monitoringPointService.findNonReportedTerms(Seq(student), pointSet.academicYear)

}
