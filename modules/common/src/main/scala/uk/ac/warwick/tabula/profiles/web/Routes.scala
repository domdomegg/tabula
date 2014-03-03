package uk.ac.warwick.tabula.profiles.web

import uk.ac.warwick.tabula.data.model._
import java.net.URLEncoder
import uk.ac.warwick.tabula.web.RoutesUtils

/**
 * Generates URLs to various locations, to reduce the number of places where URLs
 * are hardcoded and repeated.
 * 
 * For methods called "apply", you can leave out the "apply" and treat the object like a function.
 */
object Routes {
	import RoutesUtils._
	private val context = "/profiles"
	def home = context + "/"
	def search = context + "/search"
		
	object profile {
		def view(member: Member) = context + "/view/%s" format encoded(member.universityId)
		def view(member: Member, meeting: AbstractMeetingRecord) = context + "/view/%s?meeting=%s" format (encoded(member.universityId), encoded(meeting.id))
		def photo(member: Member) = context + "/view/photo/%s.jpg" format encoded(member.universityId)
	}
	
	def students(relationshipType: StudentRelationshipType) = context + "/%s/students" format encoded(relationshipType.urlPart)
		
	object relationships {
		def apply(department: Department, relationshipType: StudentRelationshipType) = context + "/department/%s/%s" format (encoded(department.code), encoded(relationshipType.urlPart))
		def missing(department: Department, relationshipType: StudentRelationshipType) = context + "/department/%s/%s/missing" format (encoded(department.code), encoded(relationshipType.urlPart))
		def allocate(department: Department, relationshipType: StudentRelationshipType) = context + "/department/%s/%s/allocate" format (encoded(department.code), encoded(relationshipType.urlPart))
		def template(department: Department, relationshipType: StudentRelationshipType) = context + "/department/%s/%s/template" format (encoded(department.code), encoded(relationshipType.urlPart))
	}
	
	object admin {
		def apply(department: Department) = Routes.home // TODO https://repo.elab.warwick.ac.uk/projects/TAB/repos/tabula/pull-requests/145/overview?commentId=1012
		def departmentPermissions(department: Department) = context + "/admin/department/%s/permissions" format encoded(department.code)
	}

	object scheduledMeeting {
		def confirm(meetingRecord: ScheduledMeetingRecord, studentCourseDetails: StudentCourseDetails, relationshipType: StudentRelationshipType) =
			context + "/%s/meeting/%s/schedule/%s/confirm" format(encoded(relationshipType.urlPart), encoded(studentCourseDetails.urlSafeId), encoded(meetingRecord.id))

		def reschedule(meetingRecord: ScheduledMeetingRecord, studentCourseDetails: StudentCourseDetails, relationshipType: StudentRelationshipType) =
			context + "/%s/meeting/%s/schedule/%s/edit" format(encoded(relationshipType.urlPart), encoded(studentCourseDetails.urlSafeId), encoded(meetingRecord.id))

		def missed(meetingRecord: ScheduledMeetingRecord, studentCourseDetails: StudentCourseDetails, relationshipType: StudentRelationshipType) =
			context + "/%s/meeting/%s/missed" format(encoded(relationshipType.urlPart), encoded(meetingRecord.id))
	}
}
