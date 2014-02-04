package uk.ac.warwick.tabula.profiles.commands

import uk.ac.warwick.tabula.{Mockito, TestBase}
import uk.ac.warwick.tabula.data.model._
import uk.ac.warwick.tabula.services.{MeetingRecordService, MeetingRecordServiceComponent, RelationshipService, RelationshipServiceComponent, ProfileService, ProfileServiceComponent}

class ViewMeetingRecordCommandTest extends TestBase with Mockito {

	@Test
	def listsAllMeetingsForRequestedStudentAndCurrentUser() {
		withUser("test"){
			val studentCourseDetails = new StudentCourseDetails()
			val requestor = new StaffMember()
			val relationship = new StudentRelationship()
			val relationshipType = StudentRelationshipType("tutor", "tutor", "personal tutor", "personal tutee")

			val meeting = new MeetingRecord
			val command = new ViewMeetingRecordCommandInternal(studentCourseDetails, Some(requestor), relationshipType)
				with RelationshipServiceComponent with ProfileServiceComponent with MeetingRecordServiceComponent {
				val profileService = mock[ProfileService]
				var relationshipService = mock[RelationshipService]
				val meetingRecordService = mock[MeetingRecordService]
			}

			// these are the calls we expect the applyInternal method to make
			command.relationshipService.getRelationships(relationshipType, studentCourseDetails.sprCode) returns Seq(relationship)
			command.meetingRecordService.list(Set(relationship), requestor) returns  Seq(meeting)

			command.applyInternal()  should be(Seq(meeting))
		}
	}

}
