package uk.ac.warwick.tabula.services.permissions

import uk.ac.warwick.tabula.TestBase
import uk.ac.warwick.tabula.Mockito
import uk.ac.warwick.tabula.Fixtures
import uk.ac.warwick.tabula.roles.StudentRelationshipAgent
import uk.ac.warwick.tabula.data.model.StudentRelationship
import uk.ac.warwick.tabula.services.{RelationshipServiceComponent, RelationshipService}
import uk.ac.warwick.tabula.data.model.StudentRelationshipType

trait StudentRelationshipRoleTestBase extends TestBase with Mockito {
	val provider : RoleProvider with RelationshipServiceComponent

	val student = Fixtures.student(universityId = "111111")
	val staff = Fixtures.staff(universityId = "0123456", userId = "cuslaj")
	val oldStaff = Fixtures.staff(universityId = "7891011", userId = "cusxad")

	val relService = smartMock[RelationshipService]

	val personalTutor = StudentRelationshipType("1", "tutor", "personal tutor", "personal tutee")
	val rel = StudentRelationship(staff, personalTutor, student)
	val oldRel = StudentRelationship(oldStaff, personalTutor, student)
}


class StudentRelationshipAgentRoleProviderTest extends StudentRelationshipRoleTestBase {

	val provider = new StudentRelationshipAgentRoleProvider {
		this.relationshipService = relService
	}
	relService.getCurrentRelationships(student, staff.universityId) returns Seq(rel)
	relService.getCurrentRelationships(student, oldStaff.universityId) returns Nil
	relService.listAllStudentRelationshipsWithUniversityId("0123456") returns Nil

	@Test
	def agent() = withUser("cuslaj", "0123456") {
		provider.getRolesFor(currentUser, student).force should be (Seq(StudentRelationshipAgent(student, personalTutor)))
	}

	@Test
	def notAgent() = withUser("cusxad", "7891011") {
		provider.getRolesFor(currentUser, student) should be (Nil)
	}

	@Test
	def handlesDefault() = withUser("cuscav", "0123456") {
		provider.getRolesFor(currentUser, Fixtures.department("in", "IN202")) should be (Seq())
	}

}