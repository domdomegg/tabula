package uk.ac.warwick.tabula.services.timetables

import org.joda.time.LocalDateTime
import uk.ac.warwick.tabula.data.model.{AbstractMeetingRecord, StudentMember, StudentRelationship, StudentRelationshipType}
import uk.ac.warwick.tabula.permissions.Permissions
import uk.ac.warwick.tabula.services.{MeetingRecordService, MeetingRecordServiceComponent, RelationshipService, RelationshipServiceComponent, SecurityService, SecurityServiceComponent}
import uk.ac.warwick.tabula.timetables.TimetableEvent.Parent
import uk.ac.warwick.tabula.timetables.{EventOccurrence, TimetableEvent, TimetableEventType}
import uk.ac.warwick.tabula.{CurrentUser, Fixtures, Mockito, TestBase}

import scala.util.Success

class MeetingRecordServiceScheduledMeetingEventSourceComponentTest extends TestBase with Mockito {
	val student = new StudentMember
	student.universityId = "university ID"

	val user = mock[CurrentUser]
	user.profile returns Some(student)

	val occurrence = EventOccurrence("", "", "", "", TimetableEventType.Meeting, LocalDateTime.now, LocalDateTime.now, None, TimetableEvent.Parent(), None, Nil)

	val relationshipType = StudentRelationshipType("t", "t", "t", "t")
	val relationships = Seq(StudentRelationship(Fixtures.staff(), relationshipType, student))
	val meetings = Seq(new AbstractMeetingRecord {
		relationship = relationships.head

		override def toEventOccurrence(context: TimetableEvent.Context) = Some(occurrence)
	})

	val source = new MeetingRecordServiceScheduledMeetingEventSourceComponent
		with RelationshipServiceComponent
		with MeetingRecordServiceComponent
		with SecurityServiceComponent {

		val relationshipService = mock[RelationshipService]
		val meetingRecordService = mock[MeetingRecordService]
		val securityService = mock[SecurityService]

	}

	source.relationshipService.getAllPastAndPresentRelationships(student) returns relationships
	source.relationshipService.listAllStudentRelationshipsWithMember(student) returns Nil
	source.securityService.can(user, Permissions.Profiles.MeetingRecord.Read(relationshipType), student) returns true
	source.meetingRecordService.listAll(relationships.toSet, Some(student)) returns meetings

	@Test
	def callsBothServicesAndGeneratesOccurrence(){
		source.scheduledMeetingEventSource.occurrencesFor(student, user, TimetableEvent.Context.Staff).futureValue should be (Seq(occurrence))
	}


}