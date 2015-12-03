package uk.ac.warwick.tabula.attendance.commands.view

import org.joda.time.DateTime
import org.springframework.validation.Errors
import uk.ac.warwick.tabula.JavaImports._
import uk.ac.warwick.tabula.commands._
import uk.ac.warwick.tabula.data.model.attendance.MonitoringPointReport
import uk.ac.warwick.tabula.data.model.{Department, StudentMember}
import uk.ac.warwick.tabula.helpers.LazyLists
import uk.ac.warwick.tabula.permissions.Permissions
import uk.ac.warwick.tabula.services._
import uk.ac.warwick.tabula.services.attendancemonitoring.{AutowiringAttendanceMonitoringServiceComponent, AttendanceMonitoringServiceComponent}
import uk.ac.warwick.tabula.system.permissions.{PermissionsChecking, PermissionsCheckingMethods, RequiresPermissionsChecking}
import uk.ac.warwick.tabula.{AcademicYear, CurrentUser}

import scala.collection.JavaConverters._

object ReportStudentsConfirmCommand {
	def apply(department: Department, academicYear: AcademicYear, user: CurrentUser) =
		new ReportStudentsConfirmCommandInternal(department, academicYear, user)
			with ComposableCommand[Seq[MonitoringPointReport]]
			with AutowiringTermServiceComponent
			with AutowiringAttendanceMonitoringServiceComponent
			with AutowiringProfileServiceComponent
			with ReportStudentsConfirmValidation
			with ReportStudentsConfirmDescription
			with ReportStudentsConfirmPermissions
			with ReportStudentsConfirmCommandState
}


class ReportStudentsConfirmCommandInternal(val department: Department, val academicYear: AcademicYear, val user: CurrentUser)
	extends CommandInternal[Seq[MonitoringPointReport]] {

	self: ReportStudentsConfirmCommandState with AttendanceMonitoringServiceComponent =>

	override def applyInternal() = {
		studentReportCounts.map{ src => {
			val scd = src.student.mostSignificantCourseDetails.getOrElse(throw new IllegalArgumentException())
			val report = new MonitoringPointReport
			report.academicYear = academicYear
			report.createdDate = DateTime.now
			report.missed = src.missed
			report.monitoringPeriod = period
			report.reporter = user.departmentCode.toUpperCase + user.apparentUser.getWarwickId
			report.student = src.student
			report.studentCourseDetails = scd
			report.studentCourseYearDetails = scd.freshStudentCourseYearDetails.find(_.academicYear == academicYear).getOrElse(throw new IllegalArgumentException())
			attendanceMonitoringService.saveOrUpdate(report)
			report
		}}
	}

}

trait ReportStudentsConfirmValidation extends SelfValidating {

	self: ReportStudentsConfirmCommandState with AttendanceMonitoringServiceComponent =>

	override def validate(errors: Errors) {
		if (!availablePeriods.filter(_._2).map(_._1).contains(period)) {
			errors.rejectValue("availablePeriods", "attendanceMonitoringReport.invalidPeriod")
		}
		if (studentReportCounts.isEmpty) {
			errors.rejectValue("studentReportCounts", "attendanceMonitoringReport.noStudents")
		}
		if (!confirm) {
			errors.rejectValue("confirm", "attendanceMonitoringReport.confirm")
		}
	}

}

trait ReportStudentsConfirmPermissions extends RequiresPermissionsChecking with PermissionsCheckingMethods {

	self: ReportStudentsConfirmCommandState =>

	override def permissionsCheck(p: PermissionsChecking) {
		p.PermissionCheck(Permissions.MonitoringPoints.Report, department)
	}

}

trait ReportStudentsConfirmDescription extends Describable[Seq[MonitoringPointReport]] {

	self: ReportStudentsConfirmCommandState =>

	override lazy val eventName = "ReportStudentsConfirm"

	override def describe(d: Description) {
		d.property("monitoringPeriod", period)
		d.property("academicYear", academicYear)
		d.property("students", studentReportCounts.map{src => src.student.universityId -> src.missed}.toMap)
	}
}

trait ReportStudentsConfirmCommandState extends ReportStudentsChoosePeriodCommandState {

	self: TermServiceComponent with AttendanceMonitoringServiceComponent =>

	def user: CurrentUser

	lazy val currentPeriod = termService.getTermFromDateIncludingVacations(DateTime.now).getTermTypeAsString
	lazy val currentAcademicYear = AcademicYear.findAcademicYearContainingDate(DateTime.now)

	// Bind variables
	var students: JList[StudentMember] = LazyLists.create()
	var filterString: String = _
	var confirm: Boolean = false
	override lazy val allStudents = students.asScala
}
