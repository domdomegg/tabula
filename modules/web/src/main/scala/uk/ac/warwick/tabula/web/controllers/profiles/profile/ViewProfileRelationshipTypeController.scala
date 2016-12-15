package uk.ac.warwick.tabula.web.controllers.profiles.profile

import org.joda.time.DateTime
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.{ModelAttribute, PathVariable, RequestMapping}
import uk.ac.warwick.tabula.AcademicYear
import uk.ac.warwick.tabula.commands.profiles.relationships.meetings.ViewMeetingRecordCommand
import uk.ac.warwick.tabula.data.model._
import uk.ac.warwick.tabula.permissions.Permissions
import uk.ac.warwick.tabula.permissions.Permissions.Profiles
import uk.ac.warwick.tabula.profiles.web.Routes
import uk.ac.warwick.tabula.services.{AutowiringRelationshipServiceComponent, AutowiringTermServiceComponent}
import uk.ac.warwick.tabula.services.attendancemonitoring.AutowiringAttendanceMonitoringMeetingRecordServiceComponent
import uk.ac.warwick.tabula.web.Mav
import uk.ac.warwick.tabula.web.controllers.profiles.ProfileBreadcrumbs
import uk.ac.warwick.util.termdates.{Term, TermNotFoundException}
import uk.ac.warwick.tabula.helpers.DateTimeOrdering._

@Controller
@RequestMapping(Array("/profiles/view"))
class ViewProfileRelationshipTypeController extends AbstractViewProfileController
	with AutowiringTermServiceComponent
	with AutowiringAttendanceMonitoringMeetingRecordServiceComponent
	with AutowiringRelationshipServiceComponent {

	@RequestMapping(Array("/{member}/{relationshipType}"))
	def viewByMemberMapping(
		@PathVariable member: Member,
		@PathVariable relationshipType: StudentRelationshipType,
		@ModelAttribute("activeAcademicYear") activeAcademicYear: Option[AcademicYear]
	): Mav = {
		mandatory(member) match {
			case student: StudentMember if student.mostSignificantCourseDetails.isDefined =>
				viewByCourse(student.mostSignificantCourseDetails.get, activeAcademicYear, relationshipType)
			case student: StudentMember if student.freshOrStaleStudentCourseDetails.nonEmpty =>
				viewByCourse(student.freshOrStaleStudentCourseDetails.lastOption.get, activeAcademicYear, relationshipType)
			case _ =>
				Redirect(Routes.Profile.identity(member))
		}
	}

	@RequestMapping(Array("/course/{studentCourseDetails}/{academicYear}/{relationshipType}"))
	def viewByCourseMapping(
		@PathVariable studentCourseDetails: StudentCourseDetails,
		@PathVariable academicYear: AcademicYear,
		@PathVariable relationshipType: StudentRelationshipType
	): Mav = {
		viewByCourse(studentCourseDetails, Some(mandatory(academicYear)), mandatory(relationshipType))
	}

	private def viewByCourse(
		studentCourseDetails: StudentCourseDetails,
		activeAcademicYear: Option[AcademicYear],
		relationshipType: StudentRelationshipType
	): Mav = {

		val thisAcademicYear = scydToSelect(studentCourseDetails, activeAcademicYear).get.academicYear

		val canReadMeetings = securityService.can(user, ViewMeetingRecordCommand.RequiredPermission(relationshipType), studentCourseDetails)
		val isSelf = user.universityId.maybeText.getOrElse("") == studentCourseDetails.student.universityId
		val studentCourseYearDetails = scydToSelect(studentCourseDetails, activeAcademicYear)
		val relationshipsToDisplay = studentCourseYearDetails.toSeq.flatMap(_.relationships(relationshipType))
		val allRelationships = relationshipService.getAllPastAndPresentRelationships(relationshipType, studentCourseDetails)
			.sortBy(r => Option(r.endDate).getOrElse(new DateTime(Integer.MAX_VALUE))).reverse

		val department = studentCourseYearDetails.map(_.enrolmentDepartment)
		val relationshipSource = department.map(_.getStudentRelationshipSource(relationshipType))

		val canEditRelationship = relationshipSource.contains(StudentRelationshipSource.Local) &&
			securityService.can(user, Permissions.Profiles.StudentRelationship.Manage(relationshipType), studentCourseDetails)

		if (!canReadMeetings) {
			applyCrumbs(Mav("profiles/profile/relationship_type_student",
				"member" -> studentCourseDetails.student,
				"isSelf" -> isSelf,
				"relationshipsToDisplay" -> relationshipsToDisplay,
				"allRelationships" -> allRelationships,
				"canReadMeetings" -> false,
				"canEditRelationship" -> canEditRelationship
			), studentCourseDetails, relationshipType)
		} else {
			val meetings = ViewMeetingRecordCommand(
				mandatory(studentCourseDetails),
				optionalCurrentMember,
				mandatory(relationshipType)
			).apply().filterNot(meetingNotInAcademicYear(thisAcademicYear))

			// User can schedule meetings provided they have the appropriate permission and...
			// either they aren't the student themseleves, or all of the agents for this relationship type are in a department that allows this
			val canCreateScheduledMeetings = securityService.can(user, Profiles.ScheduledMeetingRecord.Manage(relationshipType), studentCourseDetails) && (
				!isSelf ||	studentCourseDetails.relationships(relationshipType).forall(relationship =>
					relationship.agentMember.isEmpty || relationship.agentMember.get.homeDepartment.studentsCanScheduleMeetings
				)
			)

			applyCrumbs(Mav("profiles/profile/relationship_type_student",
				"studentCourseDetails" -> studentCourseDetails,
				"member" -> studentCourseDetails.student,
				"currentMember" -> currentMember,
				"thisAcademicYear" -> thisAcademicYear,
				"relationshipsToDisplay" -> relationshipsToDisplay,
				"allRelationships" -> allRelationships,
				"meetings" -> meetings,
				"meetingApprovalWillCreateCheckpoint" -> meetings.map {
					case (meeting: MeetingRecord) => meeting.id -> attendanceMonitoringMeetingRecordService.getCheckpoints(meeting).nonEmpty
					case (meeting: ScheduledMeetingRecord) => meeting.id -> false
				}.toMap,
				"isSelf" -> isSelf,
				"canEditRelationship" -> canEditRelationship,
				"canCreateMeetings" -> securityService.can(user, Profiles.MeetingRecord.Manage(relationshipType), studentCourseDetails),
				"canCreateScheduledMeetings" -> canCreateScheduledMeetings
			), studentCourseDetails, relationshipType)
		}

	}

	private def applyCrumbs(mav: Mav, studentCourseDetails: StudentCourseDetails, relationshipType: StudentRelationshipType): Mav =
		mav.crumbs(breadcrumbsStudent(activeAcademicYear, studentCourseDetails, ProfileBreadcrumbs.Profile.RelationshipTypeIdentifier(relationshipType)): _*)
		.secondCrumbs(secondBreadcrumbs(activeAcademicYear, studentCourseDetails)(scyd => Routes.Profile.relationshipType(scyd, relationshipType)): _*)

	private def meetingNotInAcademicYear(academicYear: AcademicYear)(meeting: AbstractMeetingRecord) = {
		try {
			termService.getAcademicWeekForAcademicYear(meeting.meetingDate, academicYear) match {
				case Term.WEEK_NUMBER_AFTER_END =>
					true
				case Term.WEEK_NUMBER_BEFORE_START if meeting.relationship.studentCourseDetails.freshStudentCourseYearDetails.nonEmpty =>
					meeting.relationship.studentCourseDetails.freshStudentCourseYearDetails.min.academicYear != academicYear
				case _ =>
					false
			}
		} catch {
			case e: TermNotFoundException =>
				// TAB-2465 Don't include this meeting - this happens if you are looking at a year before we recorded term dates
				true
		}
	}
}
