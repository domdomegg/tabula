package uk.ac.warwick.tabula.profiles.web.controllers.tutor

import org.springframework.stereotype.Controller
import org.springframework.validation.Errors
import org.springframework.web.bind.annotation._
import javax.validation.Valid
import uk.ac.warwick.tabula.ItemNotFoundException
import uk.ac.warwick.tabula.data.Transactions._
import uk.ac.warwick.tabula.data.model.Member
import uk.ac.warwick.tabula.data.model.RelationshipType._
import uk.ac.warwick.tabula.data.model.StudentMember
import uk.ac.warwick.tabula.profiles.commands.CreateMeetingRecordCommand
import uk.ac.warwick.tabula.profiles.web.Routes
import uk.ac.warwick.tabula.profiles.web.controllers.ProfilesController
import uk.ac.warwick.tabula.profiles.commands.ViewMeetingRecordCommand
import uk.ac.warwick.tabula.data.model.MeetingFormat

@Controller
@RequestMapping(value = Array("/tutor/meeting/{student}/create"))
class MeetingRecordController extends ProfilesController {
	
	validatesSelf[CreateMeetingRecordCommand]

	@ModelAttribute("createMeetingRecordCommand")
	def getCommand(@PathVariable("student") member: Member) = member match {
		case student: StudentMember => {
			profileService.findCurrentRelationship(PersonalTutor, student.studyDetails.sprCode) match {
				case Some(rel) => new CreateMeetingRecordCommand(currentMember, rel)
				case None => throw new ItemNotFoundException
			}
		}
		case _ => throw new ItemNotFoundException
	}
	
	@ModelAttribute("viewMeetingRecordCommand")
	def viewMeetingRecordCommand(@PathVariable("student") member: Member) = member match {
		case student: StudentMember => restricted(new ViewMeetingRecordCommand(student))
		case _ => None
	}
	
	// blank async form
	@RequestMapping(method = Array(GET, HEAD), params = Array("modal"))
	def showModalForm(@ModelAttribute("createMeetingRecordCommand") createCommand: CreateMeetingRecordCommand, @PathVariable("student") student: Member) = {
		val formats = MeetingFormat.members
		
		Mav("tutor/meeting/edit",
			"modal" -> true,
			"command" -> createCommand,
			"student" -> student,
			"tutorName" -> createCommand.relationship.agentName,
			"creator" -> createCommand.creator,
			"formats" -> formats).noLayout()
	}
	
	// submit async
	@RequestMapping(method = Array(POST), params = Array("modal"))
	def saveModalMeetingRecord(@Valid @ModelAttribute("createMeetingRecordCommand") createCommand: CreateMeetingRecordCommand, errors: Errors, @ModelAttribute("viewMeetingRecordCommand") viewCommand: Option[ViewMeetingRecordCommand], @PathVariable("student") student: Member) = {
		transactional() {
			if (errors.hasErrors) {
				showModalForm(createCommand, student)
			} else {
				val newMeeting = createCommand.apply()
				val meetingList = viewCommand match {
					case None => Seq()
					case Some(cmd) => cmd.apply
				}
				
				Mav("tutor/meeting/list",
					"profile" -> student,
					"meetings" -> meetingList,
					"openMeeting" -> newMeeting).noLayout()
			}
		}
	}

	// blank sync form
	@RequestMapping(method = Array(GET, HEAD))
	def showForm(@ModelAttribute("createMeetingRecordCommand") createCommand: CreateMeetingRecordCommand, @PathVariable("student") student: Member) = {
		val formats = MeetingFormat.members
		
		Mav("tutor/meeting/edit",
			"command" -> createCommand,
			"student" -> student,
			"tutorName" -> createCommand.relationship.agentName,
			"creator" -> createCommand.creator,
			"formats" -> formats)
	}
	
	// cancel sync
	@RequestMapping(method = Array(POST), params = Array("!submit", "!modal"))
	def cancel(@PathVariable("student") student: Member) = {
		Redirect(Routes.profile.view(student))
	}
		
	// submit sync
	@RequestMapping(method = Array(POST), params = Array("submit"))
	def saveMeetingRecord(@Valid @ModelAttribute("createMeetingRecordCommand") createCommand: CreateMeetingRecordCommand, errors: Errors, @PathVariable("student") student: Member) = {
		transactional() {
			if (errors.hasErrors) {
				showForm(createCommand, student)
			} else {
				val meeting = createCommand.apply()
				Redirect(Routes.profile.view(student, meeting))
			}
		}
	}
}