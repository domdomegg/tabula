package uk.ac.warwick.tabula.profiles.web.controllers.relationships

import org.mockito.Mockito._
import uk.ac.warwick.tabula.{Mockito, TestBase}
import uk.ac.warwick.tabula.Fixtures
import uk.ac.warwick.tabula.data.model._
import uk.ac.warwick.tabula.services.{RelationshipService, ProfileService}
import uk.ac.warwick.tabula.ItemNotFoundException

class CreateMeetingRecordControllerTest extends TestBase with Mockito {

	val student = Fixtures.student()
	val studentCourseDetails = student.mostSignificantCourseDetails.get

	val controller = new CreateMeetingRecordController

	val profileService = mock[ProfileService]
	controller.profileService = profileService
	val relationshipService  = mock[RelationshipService]
	controller.relationshipService = relationshipService
	
	val relationshipType = StudentRelationshipType("tutor", "tutor", "personal tutor", "personal tutee")

	when(profileService.getAllMembersWithUserId("tutor", true)).thenReturn(Seq())
	when(profileService.getAllMembersWithUserId("supervisor", true)).thenReturn(Seq())

	@Test(expected=classOf[ItemNotFoundException])
	def throwsWithoutRelationships() {
		withUser("tutor") {
			when(relationshipService.findCurrentRelationships(relationshipType, student)).thenReturn(Nil)

			val tutorCommand = controller.getCommand(relationshipType, studentCourseDetails)
		}
	}

	@Test
	def passesRelationshipTypeToCommand() {
		withUser("tutor") {
			val relationshipType = StudentRelationshipType("tutor", "tutor", "personal tutor", "personal tutee")
			
			val relationship = new MemberStudentRelationship
			relationship.studentMember = student
			relationship.relationshipType = relationshipType
			when(relationshipService.findCurrentRelationships(relationshipType, student)).thenReturn(Seq(relationship))

			val tutorCommand = controller.getCommand(relationshipType, studentCourseDetails)
			tutorCommand.relationship.relationshipType should be(relationshipType)
			tutorCommand.considerAlternatives should be(false)
		}
	}

	@Test
	def passesFirstRelationshipTypeToCommand() {
		val uniId = "1234765"
		withUser("supervisor", uniId) {
			val relationshipType = StudentRelationshipType("tutor", "tutor", "personal tutor", "personal tutee")
			
			val firstAgent = "first"
			// use non-numeric agent in test to avoid unecessary member lookup
			val rel1 = new ExternalStudentRelationship
			rel1.studentMember = student
			rel1.relationshipType = relationshipType
			rel1.agent = firstAgent

			val secondAgent = "second"
			val rel2 = new ExternalStudentRelationship
			rel2.studentMember = student
			rel2.relationshipType = relationshipType
			rel2.agent = secondAgent

			when(relationshipService.findCurrentRelationships(relationshipType, student)).thenReturn(Seq(rel1, rel2))

			studentCourseDetails.relationshipService = relationshipService
			controller.profileService = profileService

			val supervisorCommand = controller.getCommand(relationshipType, studentCourseDetails)
			supervisorCommand.relationship.relationshipType should be(relationshipType)
			supervisorCommand.relationship.agent should be(firstAgent)
			supervisorCommand.considerAlternatives should be(true)
			supervisorCommand.creator.universityId should be(uniId)
		}
	}

}
