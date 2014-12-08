package uk.ac.warwick.tabula.profiles.commands

import uk.ac.warwick.tabula.{AcademicYear, Mockito, TestBase}
import uk.ac.warwick.tabula.data.model.{StudentCourseDetails, StudentMember}

import uk.ac.warwick.tabula.system.permissions.PermissionsChecking
import uk.ac.warwick.tabula.permissions.Permissions.Profiles

class ViewModuleRegistrationsCommandTest extends TestBase with Mockito {

	val testStudent = new StudentMember
	val scd = new StudentCourseDetails(testStudent, "student")
	val year = AcademicYear(2014)

	@Test
	def requiresModuleRegistrationCoreReadPermissions() {
		val perms = new ViewModuleRegistrationsCommandPermissions with ViewModuleRegistrationsCommandState {
			val studentCourseDetails = scd
			val academicYear = year
		}

		val checking = mock[PermissionsChecking]
		perms.permissionsCheck(checking)
		there was one(checking).PermissionCheck(Profiles.Read.ModuleRegistration.Core, scd)
	}

	@Test
	def mixesCorrectPermissionsIntoCommand() {
		val composedCommand = ViewModuleRegistrationsCommand(scd, year)
		composedCommand should be(anInstanceOf[ViewModuleRegistrationsCommandPermissions])
	}

}
