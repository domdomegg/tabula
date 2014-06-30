package uk.ac.warwick.tabula.attendance.commands.view

import uk.ac.warwick.tabula.commands._
import uk.ac.warwick.tabula.system.permissions.{PermissionsChecking, PermissionsCheckingMethods, RequiresPermissionsChecking}
import uk.ac.warwick.tabula.permissions.Permissions
import org.springframework.validation.Errors
import uk.ac.warwick.tabula.data.model.attendance.{AttendanceMonitoringNote, AttendanceState, AttendanceMonitoringPoint, AttendanceMonitoringCheckpoint}
import uk.ac.warwick.tabula.data.model.{StudentMember, Department}
import uk.ac.warwick.tabula.{CurrentUser, AcademicYear}
import uk.ac.warwick.tabula.JavaImports._
import uk.ac.warwick.tabula.attendance.commands.GroupedPoint
import uk.ac.warwick.tabula.services._
import collection.JavaConverters._
import uk.ac.warwick.tabula.helpers.LazyMaps

object RecordMonitoringPointCommand {
	def apply(department: Department, academicYear: AcademicYear, templatePoint: AttendanceMonitoringPoint, user: CurrentUser) =
		new RecordMonitoringPointCommandInternal(department, academicYear, templatePoint, user)
			with ComposableCommand[Seq[AttendanceMonitoringCheckpoint]]
			with AutowiringAttendanceMonitoringServiceComponent
			with AutowiringProfileServiceComponent
			with AutowiringTermServiceComponent
			with AutowiringSecurityServiceComponent
			with RecordMonitoringPointValidation
			with RecordMonitoringPointDescription
			with RecordMonitoringPointPermissions
			with RecordMonitoringPointCommandState
			with PopulateRecordMonitoringPointCommand
			with SetFilteredPointsOnRecordMonitoringPointCommand
}


class RecordMonitoringPointCommandInternal(val department: Department, val academicYear: AcademicYear, val templatePoint: AttendanceMonitoringPoint, val user: CurrentUser)
	extends CommandInternal[Seq[AttendanceMonitoringCheckpoint]] {

	self: RecordMonitoringPointCommandState with AttendanceMonitoringServiceComponent =>

	override def applyInternal() = {
		checkpointMap.asScala.flatMap{ case(student, pointMap) =>
			attendanceMonitoringService.setAttendance(student, pointMap.asScala.toMap, user)
		}.toSeq
	}

}

trait SetFilteredPointsOnRecordMonitoringPointCommand {

	self: RecordMonitoringPointCommandState =>

	def setFilteredPoints(points: Map[String, Seq[GroupedPoint]]) = {
		filteredPoints = points
	}
}

trait PopulateRecordMonitoringPointCommand extends PopulateOnForm {

	self: RecordMonitoringPointCommandState =>

	override def populate() = {
		val studentPointStateTuples: Seq[(StudentMember, AttendanceMonitoringPoint, AttendanceState)] =
			studentMap.flatMap { case (point, students) =>
				students.map(student => (student, point, {
					val pointMapOption = studentPointCheckpointMap.get(student)
					val checkpointOption = pointMapOption.flatMap{ pointMap => pointMap.get(point) }
					val stateOption = checkpointOption.map{ checkpoint => checkpoint.state }
					stateOption.orNull
				}))
			}.toSeq
		checkpointMap = studentPointStateTuples.groupBy(_._1).mapValues(
			_.groupBy(_._2).mapValues(_.head._3).toMap.asJava
		).toMap.asJava
	}
}

trait RecordMonitoringPointValidation extends SelfValidating with GroupedPointRecordValidation {

	self: RecordMonitoringPointCommandState with AttendanceMonitoringServiceComponent with TermServiceComponent with SecurityServiceComponent =>

	override def validate(errors: Errors) {
		validateGroupedPoint(errors, templatePoint, checkpointMap.asScala.mapValues(_.asScala.toMap).toMap, user)
	}

}

trait RecordMonitoringPointPermissions extends RequiresPermissionsChecking with PermissionsCheckingMethods {

	self: RecordMonitoringPointCommandState =>

	override def permissionsCheck(p: PermissionsChecking) {
		p.PermissionCheck(Permissions.MonitoringPoints.Record, department)
	}

}

trait RecordMonitoringPointDescription extends Describable[Seq[AttendanceMonitoringCheckpoint]] {

	self: RecordMonitoringPointCommandState =>

	override lazy val eventName = "RecordMonitoringPoint"

	override def describe(d: Description) {
		d.property("checkpoints", checkpointMap.asScala.map{ case (student, pointMap) =>
			student.universityId -> pointMap.asScala.map{ case(point, state) => point -> {
				if (state == null)
					"null"
				else
					state.dbValue
			}}
		})
	}
}

trait RecordMonitoringPointCommandState {

	self: AttendanceMonitoringServiceComponent with ProfileServiceComponent with TermServiceComponent =>

	def department: Department
	def academicYear: AcademicYear
	def templatePoint: AttendanceMonitoringPoint
	def user: CurrentUser

	var filteredPoints: Map[String, Seq[GroupedPoint]] = _

	lazy val pointsToRecord = filteredPoints.values.flatten
		.find(p => p.templatePoint.id == templatePoint.id)
		.getOrElse(throw new IllegalArgumentException)
		.points

	lazy val studentMap: Map[AttendanceMonitoringPoint, Seq[StudentMember]] =
		pointsToRecord.map { point =>
			point -> profileService.getAllMembersWithUniversityIds(point.scheme.members.members).flatMap {
				case student: StudentMember => Option(student)
				case _ => None
			}
		}.toMap

	lazy val studentPointCheckpointMap: Map[StudentMember, Map[AttendanceMonitoringPoint, AttendanceMonitoringCheckpoint]] =
		attendanceMonitoringService.getCheckpoints(pointsToRecord, studentMap.values.flatten.toSeq.distinct)

	lazy val attendanceNoteMap: Map[StudentMember, Map[AttendanceMonitoringPoint, AttendanceMonitoringNote]] =
		studentMap.flatMap(_._2).map(student => student -> attendanceMonitoringService.getAttendanceNoteMap(student)).toMap

	lazy val hasReportedMap: Map[StudentMember, Boolean] =
		studentMap.flatMap(_._2).map(student =>
			student -> {
				val nonReportedTerms = attendanceMonitoringService.findNonReportedTerms(Seq(student), academicYear)
				!nonReportedTerms.contains(termService.getTermFromDateIncludingVacations(templatePoint.startDate.toDateTimeAtStartOfDay).getTermTypeAsString)
			}
		).toMap

	// Bind variables
	var checkpointMap: JMap[StudentMember, JMap[AttendanceMonitoringPoint, AttendanceState]] =
		LazyMaps.create{student: StudentMember => JHashMap(): JMap[AttendanceMonitoringPoint, AttendanceState] }.asJava
}
