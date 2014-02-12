package uk.ac.warwick.tabula.profiles.web.controllers.relationships

import org.springframework.web.bind.annotation.{PathVariable, InitBinder, ModelAttribute}
import uk.ac.warwick.tabula.profiles.commands.{ViewMeetingRecordCommand, ModifyMeetingRecordCommand, CreateMeetingRecordCommand}
import uk.ac.warwick.tabula.data.model._
import uk.ac.warwick.tabula.profiles.web.controllers.CurrentMemberComponent
import javax.validation.Valid
import org.springframework.validation.Errors
import uk.ac.warwick.tabula.data.Transactions._
import uk.ac.warwick.tabula.profiles.web.Routes
import org.springframework.web.bind.WebDataBinder
import uk.ac.warwick.util.web.bind.AbstractPropertyEditor
import uk.ac.warwick.tabula.services.{MonitoringPointMeetingRelationshipTermServiceComponent, RelationshipServiceComponent, ProfileServiceComponent}
import uk.ac.warwick.tabula.data.model.StudentCourseDetails
import scala.Some
import uk.ac.warwick.tabula.commands.Appliable
import uk.ac.warwick.tabula.web.controllers.{ControllerViews, ControllerImports, ControllerMethods}


trait MeetingRecordModal  {

	this:ProfileServiceComponent with RelationshipServiceComponent with ControllerMethods with ControllerImports
		with CurrentMemberComponent with ControllerViews with MonitoringPointMeetingRelationshipTermServiceComponent =>
	/**
	 * Contains all of the request mappings needed to drive meeting record modals (including iframe stuff)
	 *
	 * Implementers must have a validatesSelf clause and a ModelAttribute definition that returns an implementation of
	 * ModifyAssignmentCommand
	 *
	 * e.g.
	 *
	 * validatesSelf[CreateMeetingRecordCommand]
	 * 	\@ModelAttribute("command")
	 *	def getCommand(@PathVariable("meeting") meetingRecord: MeetingRecord) = new EditMeetingRecordCommand(meetingRecord)
	 *
	 */


	@ModelAttribute("allRelationships")
	def allRelationships(@PathVariable("studentCourseDetails") studentCourseDetails: StudentCourseDetails,
						 @PathVariable("relationshipType") relationshipType: StudentRelationshipType) = {

		relationshipService.findCurrentRelationships(relationshipType, studentCourseDetails.student)
	}

	@ModelAttribute("viewMeetingRecordCommand")
	def viewMeetingRecordCommand(@PathVariable("studentCourseDetails") studentCourseDetails: StudentCourseDetails,
								 @PathVariable("relationshipType") relationshipType: StudentRelationshipType) = {
		restricted(ViewMeetingRecordCommand(studentCourseDetails, optionalCurrentMember, relationshipType))
	}

	// modal chrome
	@RequestMapping(method = Array(GET, HEAD), params = Array("modal"))
	def showModalChrome(@ModelAttribute("command") command: ModifyMeetingRecordCommand,
						@PathVariable("studentCourseDetails") studentCourseDetails: StudentCourseDetails,
						@PathVariable("relationshipType") relationshipType: StudentRelationshipType) = {

		Mav("related_students/meeting/edit",
			"modal" -> true,
			"agentName" -> (if (command.considerAlternatives) "" else command.relationship.agentName),
			"studentCourseDetails" -> studentCourseDetails,
			"isStudent" -> (studentCourseDetails.student == currentMember),
			"relationshipType" -> relationshipType
		).noLayout()
	}

	// modal iframe form
	@RequestMapping(method = Array(GET, HEAD), params = Array("iframe"))
	def showIframeForm(@ModelAttribute("command") command: ModifyMeetingRecordCommand,
					   @PathVariable("studentCourseDetails") studentCourseDetails:StudentCourseDetails,
					   @PathVariable("relationshipType") relationshipType: StudentRelationshipType) = {

		val formats = MeetingFormat.members

		Mav("related_students/meeting/edit",
			"iframe" -> true,
			"command" -> command,
			"studentCourseDetails" -> studentCourseDetails,
			"isStudent" -> (studentCourseDetails.student == currentMember),
			"relationshipType"->relationshipType,
			"creator" -> command.creator,
			"formats" -> formats
		).noNavigation()
	}

	// submit async
	@RequestMapping(method = Array(POST), params = Array("modal"))
	def saveModalMeetingRecord(@Valid @ModelAttribute("command") command: ModifyMeetingRecordCommand,
	                           errors: Errors,
	                           @ModelAttribute("viewMeetingRecordCommand") viewCommand: Option[Appliable[Seq[AbstractMeetingRecord]]],
	                           @PathVariable("studentCourseDetails") studentCourseDetails: StudentCourseDetails,
							   @PathVariable("relationshipType") relationshipType: StudentRelationshipType) =transactional() {
		if (errors.hasErrors) {
			showIframeForm(command, studentCourseDetails, relationshipType)
		} else {
			val modifiedMeeting = command.apply()
			val meetingList = viewCommand match {
				case None => Seq()
				case Some(cmd) => cmd.apply()
			}

			Mav("related_students/meeting/list",
        	"studentCourseDetails" -> studentCourseDetails,
 			    "role" -> relationshipType,
				  "meetings" -> meetingList,
					"meetingApprovalWillCreateCheckpoint" -> meetingList.map {
						case (meeting: MeetingRecord) => meeting.id -> monitoringPointMeetingRelationshipTermService.willCheckpointBeCreated(meeting)
						case (meeting: ScheduledMeetingRecord) => meeting.id -> false
					}.toMap,
				  "viewer" -> currentMember,
				  "openMeeting" -> modifiedMeeting).noLayout()
		}
	}

	// blank sync form
	@RequestMapping(method = Array(GET, HEAD))
	def showForm(@ModelAttribute("command") command: ModifyMeetingRecordCommand,
				 @PathVariable("studentCourseDetails") studentCourseDetails: StudentCourseDetails,
				 @PathVariable("relationshipType") relationshipType: StudentRelationshipType) = {

		val formats = MeetingFormat.members

		Mav("related_students/meeting/edit",
			"command" -> command,
			"studentCourseDetails" -> studentCourseDetails,
			"isStudent" -> (studentCourseDetails.student == currentMember),
		  "relationshipType"->relationshipType,
			"agentName" -> command.relationship.agentName,
			"creator" -> command.creator,
			"formats" -> formats
		)
	}

	// cancel sync
	@RequestMapping(method = Array(POST), params = Array("!submit", "!modal"))
	def cancel(@PathVariable("student") student: Member) = {
		Redirect(Routes.profile.view(student))
	}

	// submit sync
	@RequestMapping(method = Array(POST), params = Array("submit"))
	def saveMeetingRecord(@Valid @ModelAttribute("command") createCommand: CreateMeetingRecordCommand,
						  errors: Errors,
						  @PathVariable("studentCourseDetails") studentCourseDetails: StudentCourseDetails,
						  @PathVariable("relationshipType") relationshipType: StudentRelationshipType) = {
		transactional() {
			if (errors.hasErrors) {
				showForm(createCommand, studentCourseDetails, relationshipType)
			} else {
				val meeting = createCommand.apply()
				Redirect(Routes.profile.view(studentCourseDetails.student, meeting))
			}
		}
	}


	@InitBinder
	def initRelationshipsEditor(binder: WebDataBinder,
								@PathVariable("studentCourseDetails") studentCourseDetails: StudentCourseDetails, 
								@PathVariable("relationshipType") relationshipType: StudentRelationshipType) {
		binder.registerCustomEditor(classOf[StudentRelationship[_]], new AbstractPropertyEditor[StudentRelationship[_]] {
			override def fromString(agent: String) = 
				allRelationships(studentCourseDetails, relationshipType).find(_.agent == agent).orNull
			override def toString(rel: StudentRelationship[_]) = rel.agent
		})
	}
}
