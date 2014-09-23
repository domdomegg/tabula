package uk.ac.warwick.tabula.attendance.commands.report

import uk.ac.warwick.tabula.data.model.{StudentMember, Department}
import uk.ac.warwick.tabula.commands.{Description, SelfValidating, Describable, ComposableCommand, CommandInternal}
import uk.ac.warwick.tabula.data.model.attendance.MonitoringPointReport
import uk.ac.warwick.tabula.system.permissions.{PermissionsChecking, PermissionsCheckingMethods, RequiresPermissionsChecking}
import uk.ac.warwick.tabula.permissions.Permissions
import uk.ac.warwick.tabula.{CurrentUser, AcademicYear}
import org.springframework.validation.Errors
import org.joda.time.DateTime
import uk.ac.warwick.tabula.services.{AutowiringSecurityServiceComponent, SecurityServiceComponent, TermService, AutowiringMonitoringPointServiceComponent, MonitoringPointServiceComponent}

object CreateMonitoringPointReportCommand {
	def apply(department: Department, currentUser: CurrentUser) =
		new CreateMonitoringPointReportCommandInternal(department, currentUser)
			with ComposableCommand[Seq[MonitoringPointReport]]
			with CreateMonitoringPointReportCommandValidation
			with CreateMonitoringPointReportCommandDescription
			with CreateMonitoringPointReportCommandPermissions
			with AutowiringMonitoringPointServiceComponent
			with AutowiringSecurityServiceComponent
}

trait CreateMonitoringPointReportRequestState {
	var period: String = _
	var academicYear: AcademicYear = _
	var missedPoints: Map[StudentMember, Int] = _
}

trait CreateMonitoringPointReportCommandState extends CreateMonitoringPointReportRequestState {
	def department: Department
	def currentUser: CurrentUser
}

class CreateMonitoringPointReportCommandInternal(val department: Department, val currentUser: CurrentUser) extends CommandInternal[Seq[MonitoringPointReport]] with CreateMonitoringPointReportCommandState {
	self: MonitoringPointServiceComponent =>

	def applyInternal() = {
		missedPoints.map { case (student, missedCount) =>
			val scd = student.mostSignificantCourseDetails.orNull
			val report = new MonitoringPointReport
			report.academicYear = academicYear
			report.createdDate = DateTime.now
			report.missed = missedCount
			report.monitoringPeriod = period
			report.reporter = currentUser.departmentCode.toUpperCase + currentUser.apparentUser.getWarwickId
			report.student = student
			report.studentCourseDetails = scd
			report.studentCourseYearDetails = scd.freshStudentCourseYearDetails.find(_.academicYear == academicYear).orNull
			monitoringPointService.saveOrUpdate(report)
			report
		}.toSeq
	}
}

trait CreateMonitoringPointReportCommandValidation extends SelfValidating {
	self: MonitoringPointServiceComponent with SecurityServiceComponent with CreateMonitoringPointReportCommandState =>

	override def validate(errors: Errors) = {
		val allStudents = missedPoints.keySet.toSeq

		if (allStudents.isEmpty) {
			errors.rejectValue("missedPoints", "monitoringPointReport.noStudents")
		}

		if (!TermService.orderedTermNames.contains(period)) {
			errors.rejectValue("period", "monitoringPointReport.invalidPeriod")
		}

		if (academicYear == null) {
			errors.rejectValue("academicYear", "NotEmpty")
		}

		if (!errors.hasErrors) {
			allStudents.foreach { student =>
				val nonReported = monitoringPointService.findNonReportedTerms(Seq(student), academicYear)

				if (!nonReported.contains(period)) {
					errors.rejectValue("missedPoints", "monitoringPointReport.period.alreadyReported", Array(student.universityId), "")
				}

				if (!securityService.can(currentUser, Permissions.MonitoringPoints.Report, student)) {
					errors.rejectValue("missedPoints", "monitoringPointReport.student.noPermission", Array(student.universityId), "")
				}
			}
		}

		if (missedPoints.exists { case (_, points) => points <= 0 }) {
			errors.rejectValue("missedPoints", "monitoringPointReport.missedPointsZero")
		}
	}
}

trait CreateMonitoringPointReportCommandDescription extends Describable[Seq[MonitoringPointReport]] {
	self: CreateMonitoringPointReportCommandState =>

	override lazy val eventName = "MonitoringPointReport"

	def describe(d: Description) {
		val students = missedPoints.map { case (student, count) => student.universityId -> count }

		d.department(department)
		d.property("monitoringPeriod", period)
		d.property("academicYear", academicYear)
		d.property("students", students)
	}
}

trait CreateMonitoringPointReportCommandPermissions extends RequiresPermissionsChecking with PermissionsCheckingMethods {
	self: CreateMonitoringPointReportCommandState =>

	def permissionsCheck(p: PermissionsChecking) {
		p.PermissionCheck(Permissions.MonitoringPoints.Report, mandatory(department))
	}
}