package uk.ac.warwick.tabula.attendance.commands.check

import org.joda.time.DateTime
import uk.ac.warwick.tabula.commands._
import uk.ac.warwick.tabula.data.model._
import uk.ac.warwick.tabula.permissions.Permissions
import uk.ac.warwick.tabula.services.{AutowiringMonitoringPointMeetingRelationshipTermServiceComponent, MonitoringPointMeetingRelationshipTermServiceComponent}
import uk.ac.warwick.tabula.services.attendancemonitoring.{AttendanceMonitoringMeetingRecordServiceComponent, AutowiringAttendanceMonitoringMeetingRecordServiceComponent}
import uk.ac.warwick.tabula.system.permissions.{PermissionsChecking, PermissionsCheckingMethods, RequiresPermissionsChecking}

// TODO: When old-style points are retired have this command return a Seq[AttendanceMonitoringCheckpoint]
object CheckMeetingRecordCheckpointCommand {
	def apply(student: StudentMember, relationshipType: StudentRelationshipType, meetingFormat: MeetingFormat, meetingDate: DateTime) =
		new CheckMeetingRecordCheckpointCommandInternal(student, relationshipType, meetingFormat, meetingDate)
			with ComposableCommand[Boolean]
			with AutowiringAttendanceMonitoringMeetingRecordServiceComponent
			with AutowiringMonitoringPointMeetingRelationshipTermServiceComponent
			with CheckMeetingRecordCheckpointPermissions
			with CheckMeetingRecordCheckpointCommandState
			with ReadOnly with Unaudited
}

class CheckMeetingRecordCheckpointCommandInternal(val student: StudentMember, val relationshipType: StudentRelationshipType, val meetingFormat: MeetingFormat, val meetingDate: DateTime)
	extends CommandInternal[Boolean] {

	self: AttendanceMonitoringMeetingRecordServiceComponent with MonitoringPointMeetingRelationshipTermServiceComponent =>

	override def applyInternal() = {
		val oldCheckpoints = monitoringPointMeetingRelationshipTermService.willCheckpointBeCreated(student, relationshipType, meetingFormat, meetingDate, None)

		val newCheckpoints = {
			val relationship = new MemberStudentRelationship
			relationship.relationshipType = relationshipType
			relationship.studentMember = student
			val meeting = new MeetingRecord
			meeting.relationship = relationship
			meeting.format = meetingFormat
			meeting.meetingDate = meetingDate
			attendanceMonitoringMeetingRecordService.getCheckpoints(meeting)
				.map(c => CheckpointResult(c.student))
		}.nonEmpty

		oldCheckpoints || newCheckpoints
	}

}

trait CheckMeetingRecordCheckpointPermissions extends RequiresPermissionsChecking with PermissionsCheckingMethods {

	self: CheckMeetingRecordCheckpointCommandState =>

	override def permissionsCheck(p: PermissionsChecking) {
		p.PermissionCheck(Permissions.Profiles.MeetingRecord.Read(mandatory(relationshipType)), student)
	}

}

trait CheckMeetingRecordCheckpointCommandState {
	def student: StudentMember
	def relationshipType: StudentRelationshipType
	def meetingFormat: MeetingFormat
	def meetingDate: DateTime
}