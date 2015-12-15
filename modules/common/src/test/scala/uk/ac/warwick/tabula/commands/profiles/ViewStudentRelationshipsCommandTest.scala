package uk.ac.warwick.tabula.commands.profiles

import org.joda.time.DateTime
import uk.ac.warwick.tabula.data.model.{Department, MemberStudentRelationship, StaffMember, StudentRelationshipType}
import uk.ac.warwick.tabula.helpers.Tap
import uk.ac.warwick.tabula.helpers.Tap.tap
import uk.ac.warwick.tabula.services.{ProfileService, RelationshipService}
import uk.ac.warwick.tabula.{Fixtures, Mockito, TestBase}

class ViewStudentRelationshipsCommandTest extends TestBase with Mockito {

	private trait Fixture {
		val profileService = mock[ProfileService]
		val relationshipService =  mock[RelationshipService]

		val departmentOfXXX = new Department().tap(_.code = "xxx")
		val tutorRelType = new StudentRelationshipType().tap(_.description = "tutor")

		// give them both the same surname, just to prove that we don't accidentally merge users when sorting
		val staff1 = new StaffMember(id = "111").tap(_.lastName = "Smith")
		val staff2 = new StaffMember(id = "222").tap(_.lastName = "Smith")

		val student1 = Fixtures.student(universityId = "1")
		val student2 = Fixtures.student(universityId = "2")
		val student3 = Fixtures.student(universityId = "3")

		val studentRel1 = new MemberStudentRelationship().tap(r => {
			r.id = "1"
			r.agentMember = staff1
			r.studentMember = student1
			r.startDate = DateTime.now
		})

		val studentRel2 = new MemberStudentRelationship().tap(r => {
			r.id = "2"
			r.agentMember = staff2
			r.studentMember = student2
			r.startDate = DateTime.now
		})
	}

	@Test //thisTestHasARidculouslyLongNameButICantThinkOfASensibleWayToShortenItWhichProbablyMeansTheCommandNeedsRefactoring...
	def applyCombinesStudentsInDepartmentWithStudentsWhoHaveTutorsInDepartment() {
		new Fixture {



			// sprCode 1 is in the department, and has a tutor
			relationshipService.listStudentRelationshipsByDepartment(tutorRelType, departmentOfXXX) returns Seq(studentRel1)

			// sprCode 2 is not in the department, but does have a tutor in the department
			relationshipService.listStudentRelationshipsByStaffDepartment(tutorRelType, departmentOfXXX) returns Seq(studentRel2)

			//
			relationshipService.listStudentsWithoutRelationship(tutorRelType, departmentOfXXX) returns Seq(student3)

			val command = new ViewStudentRelationshipsCommand(departmentOfXXX, tutorRelType)

			command.profileService = profileService
			command.relationshipService = relationshipService


			//When I invoke the command
			val relationshipInfo = command.applyInternal()

			//Then I should get the results I expect
			relationshipInfo.studentMap(SortableAgentIdentifier("111",Some("Smith"))) should be(Seq(studentRel1))
			relationshipInfo.studentMap(SortableAgentIdentifier("222",Some("Smith"))) should be(Seq(studentRel2))
			relationshipInfo.missingCount should be(1) // the other one
		}
	}

}
