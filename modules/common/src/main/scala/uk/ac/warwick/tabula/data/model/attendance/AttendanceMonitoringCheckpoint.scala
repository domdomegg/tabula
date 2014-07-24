package uk.ac.warwick.tabula.data.model.attendance

import javax.persistence.{Column, JoinColumn, FetchType, ManyToOne, Entity}
import uk.ac.warwick.tabula.data.model.{StudentMember, GeneratedId}
import uk.ac.warwick.spring.Wire
import org.hibernate.annotations.Type
import org.joda.time.DateTime
import javax.validation.constraints.NotNull

import uk.ac.warwick.tabula.services.attendancemonitoring.AttendanceMonitoringService

@Entity
class AttendanceMonitoringCheckpoint extends GeneratedId {

	@transient var attendanceMonitoringService = Wire[AttendanceMonitoringService]

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "point_id")
	var point: AttendanceMonitoringPoint = _

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "student_id")
	var student: StudentMember = _

	@NotNull
	@Type(`type` = "uk.ac.warwick.tabula.data.model.attendance.AttendanceStateUserType")
	@Column(name = "state")
	private var _state: AttendanceState = _

	def state = _state
	def state_=(state: AttendanceState) {
		if (attendanceMonitoringService.studentAlreadyReportedThisTerm(student, point)){
			throw new IllegalArgumentException
		}
		_state = state
	}

	@NotNull
	@Column(name = "updated_date")
	var updatedDate: DateTime = _

	@NotNull
	@Column(name = "updated_by")
	var updatedBy: String = _

	var autoCreated: Boolean = false

	@transient
	var activePoint = true

}