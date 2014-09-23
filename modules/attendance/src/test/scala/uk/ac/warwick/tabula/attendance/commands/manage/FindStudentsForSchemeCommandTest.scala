package uk.ac.warwick.tabula.attendance.commands.manage

import uk.ac.warwick.tabula.commands.MemberOrUser
import uk.ac.warwick.tabula.services.attendancemonitoring.{AttendanceMonitoringServiceComponent, AttendanceMonitoringService}
import uk.ac.warwick.tabula.{MockUserLookup, Fixtures, CurrentUser, Mockito, TestBase}
import uk.ac.warwick.tabula.services.{UserLookupComponent, ProfileService, ProfileServiceComponent}
import uk.ac.warwick.tabula.data.model.attendance.AttendanceMonitoringScheme
import uk.ac.warwick.tabula.data._
import uk.ac.warwick.tabula.permissions.Permission
import uk.ac.warwick.tabula.data.model.{Route, Department}
import uk.ac.warwick.tabula.data.SchemeMembershipItem

class FindStudentsForSchemeCommandTest extends TestBase with Mockito {

	trait CommandTestSupport extends ProfileServiceComponent
		with FindStudentsForSchemeCommandState with AttendanceMonitoringServiceComponent with UserLookupComponent {

		val profileService = smartMock[ProfileService]
		val attendanceMonitoringService = smartMock[AttendanceMonitoringService]
		val userLookup = new MockUserLookup
		def routesForPermission(user: CurrentUser, p: Permission, dept: Department): Set[Route] = {
			Set()
		}
		def deserializeFilter(filter: String) = {

		}

	}

	trait Fixture {
		val scheme = new AttendanceMonitoringScheme
		scheme.department = new Department
		val student1 = Fixtures.student(universityId = "1234", userId = "1234")
		val student2 = Fixtures.student(universityId = "2345", userId = "2345")
		val student3 = Fixtures.student(universityId = "3456", userId = "3456")
	}

	@Test
	def apply() { withUser("cusfal") { new Fixture {
		val command = new FindStudentsForSchemeCommandInternal(scheme, currentUser) with CommandTestSupport

		command.routes.add(new Route(){ this.code = "a100" })

		command.profileService.findAllUniversityIdsByRestrictionsInAffiliatedDepartments(
			any[Department], any[Seq[ScalaRestriction]], any[Seq[ScalaOrder]]
		) returns Seq(student1.universityId, student2.universityId)

		command.attendanceMonitoringService.findSchemeMembershipItems(
			Seq(student1.universityId, student2.universityId), SchemeMembershipStaticType
		) returns Seq(
			SchemeMembershipItem(SchemeMembershipStaticType, student1.firstName, student1.lastName, student1.universityId, student1.userId, Seq()),
			SchemeMembershipItem(SchemeMembershipStaticType, student2.firstName, student2.lastName, student2.universityId, student2.userId, Seq())
		)

		command.userLookup.registerUserObjects(
			MemberOrUser(student1).asUser,
			MemberOrUser(student2).asUser,
			MemberOrUser(student3).asUser
		)

		command.includedStudentIds.add(student3.universityId)
		command.excludedStudentIds.add(student2.universityId)

		command.findStudents = "submit"

		val result = command.applyInternal()
		// 2 results from search, even with 1 removed
		result.membershipItems.size should be (2)
		// 1 marked static
		result.membershipItems.count(_.itemType == SchemeMembershipStaticType) should be (1)
		// 1 marked removed
		result.membershipItems.count(_.itemType == SchemeMembershipExcludeType) should be (1)
		// 0 marked included (not displayed if not in search)
		result.membershipItems.count(_.itemType == SchemeMembershipIncludeType) should be (0)

		result.membershipItems.size should be (result.staticStudentIds.size)
	}}}

}