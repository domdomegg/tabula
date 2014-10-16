package uk.ac.warwick.tabula.attendance.commands.manage

import uk.ac.warwick.tabula.commands._
import uk.ac.warwick.tabula.services.attendancemonitoring.{AutowiringAttendanceMonitoringServiceComponent, AttendanceMonitoringServiceComponent}
import uk.ac.warwick.tabula.system.permissions.{PermissionsChecking, PermissionsCheckingMethods, RequiresPermissionsChecking}
import uk.ac.warwick.tabula.permissions.Permissions
import org.springframework.validation.Errors
import uk.ac.warwick.tabula.data.model.attendance.AttendanceMonitoringScheme
import uk.ac.warwick.tabula.JavaImports._
import uk.ac.warwick.tabula.helpers.LazyLists
import collection.JavaConverters._
import org.joda.time.DateTime
import uk.ac.warwick.tabula.services._
import uk.ac.warwick.tabula.data.{SchemeMembershipIncludeType, SchemeMembershipStaticType, SchemeMembershipItem}
import uk.ac.warwick.tabula.CurrentUser
import uk.ac.warwick.tabula.data.model.StudentMember

object AddStudentsToSchemeCommand {
	def apply(scheme: AttendanceMonitoringScheme, user: CurrentUser) =
		new AddStudentsToSchemeCommandInternal(scheme, user)
			with AutowiringAttendanceMonitoringServiceComponent
			with AutowiringProfileServiceComponent
			with AutowiringSecurityServiceComponent
			with ComposableCommand[AttendanceMonitoringScheme]
			with AddStudentsToSchemeValidation
			with AddStudentsToSchemeDescription
			with AddStudentsToSchemePermissions
			with AddStudentsToSchemeCommandState
}


class AddStudentsToSchemeCommandInternal(val scheme: AttendanceMonitoringScheme, val user: CurrentUser)
	extends CommandInternal[AttendanceMonitoringScheme] with TaskBenchmarking with UpdatesAttendanceMonitoringScheme {

	self: AddStudentsToSchemeCommandState with AttendanceMonitoringServiceComponent	with ProfileServiceComponent =>

	override def applyInternal() = {
		if (doFind && linkToSits) {
			scheme.members.staticUserIds = staticStudentIds.asScala
			scheme.members.includedUserIds = includedStudentIds.asScala
			scheme.members.excludedUserIds = excludedStudentIds.asScala
			scheme.memberQuery = filterQueryString
		} else {
			scheme.members.staticUserIds = Seq()
			scheme.members.includedUserIds = Seq()
			scheme.members.excludedUserIds = Seq()
			scheme.memberQuery = ""
			scheme.members.includedUserIds = ((staticStudentIds.asScala diff excludedStudentIds.asScala) ++ includedStudentIds.asScala).distinct
		}

		scheme.updatedDate = DateTime.now
		attendanceMonitoringService.saveOrUpdate(scheme)

		afterUpdate(Seq(scheme))

		scheme
	}

}

trait AddStudentsToSchemeValidation extends SelfValidating with TaskBenchmarking {

	self: AddStudentsToSchemeCommandState with ProfileServiceComponent with SecurityServiceComponent =>

	override def validate(errors: Errors) {
		// In practice there should be no students that fail this validation
		// but this protects against hand-rolled POSTs
		val members = benchmark("profileService.getAllMembersWithUniversityIds") {
			profileService.getAllMembersWithUniversityIds(
				((staticStudentIds.asScala
					diff excludedStudentIds.asScala)
					diff includedStudentIds.asScala)
				++ includedStudentIds.asScala
			)
		}
		val noPermissionMembers = benchmark("noPermissionMembers") {
			members.filter {
				case student: StudentMember =>
					!student.affiliatedDepartments.contains(scheme.department) &&
						!securityService.can(user, Permissions.MonitoringPoints.Manage, student)
				case _ => false
			}
		}
		if (noPermissionMembers.nonEmpty) {
			errors.rejectValue(
				"staticStudentIds",
				"attendanceMonitoringScheme.student.noPermission",
				Array(noPermissionMembers.map(_.universityId).mkString(", ")),
				"You do not have permission to manage these students' attendance"
			)
		}
	}

}

trait AddStudentsToSchemePermissions extends RequiresPermissionsChecking with PermissionsCheckingMethods {

	self: AddStudentsToSchemeCommandState =>

	override def permissionsCheck(p: PermissionsChecking) {
		p.PermissionCheck(Permissions.MonitoringPoints.Manage, scheme)
	}

}

trait AddStudentsToSchemeDescription extends Describable[AttendanceMonitoringScheme] {

	self: AddStudentsToSchemeCommandState =>

	override lazy val eventName = "AddStudentsToScheme"

	override def describe(d: Description) {
		d.attendanceMonitoringScheme(scheme)
	}
}

trait AddStudentsToSchemeCommandState {

	self: AttendanceMonitoringServiceComponent =>

	def scheme: AttendanceMonitoringScheme
	def user: CurrentUser

	def membershipItems: Seq[SchemeMembershipItem] = {
		val staticMemberItems = attendanceMonitoringService.findSchemeMembershipItems(
			(staticStudentIds.asScala diff excludedStudentIds.asScala) diff includedStudentIds.asScala,
			SchemeMembershipStaticType
		)
		val includedMemberItems = attendanceMonitoringService.findSchemeMembershipItems(includedStudentIds.asScala, SchemeMembershipIncludeType)

		(staticMemberItems ++ includedMemberItems).sortBy(membershipItem => (membershipItem.lastName, membershipItem.firstName))
	}

	// Bind variables

	var includedStudentIds: JList[String] = LazyLists.create()
	var excludedStudentIds: JList[String] = LazyLists.create()
	var staticStudentIds: JList[String] = LazyLists.create()
	var filterQueryString: String = ""
	var linkToSits: Boolean = _
	var doFind: Boolean = _
}
