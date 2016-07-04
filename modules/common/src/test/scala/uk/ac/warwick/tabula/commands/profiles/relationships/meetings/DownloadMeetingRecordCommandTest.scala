package uk.ac.warwick.tabula.commands.profiles.relationships.meetings

import org.joda.time.DateTimeConstants
import org.springframework.web.multipart.MultipartFile
import uk.ac.warwick.tabula._
import uk.ac.warwick.tabula.commands.UploadedFile
import uk.ac.warwick.tabula.data.model.MeetingFormat._
import uk.ac.warwick.tabula.data.model.{ExternalStudentRelationship, FileAttachment, StudentRelationshipType}
import uk.ac.warwick.tabula.services._
import uk.ac.warwick.tabula.services.attendancemonitoring.{AttendanceMonitoringMeetingRecordService, AttendanceMonitoringMeetingRecordServiceComponent}
import uk.ac.warwick.tabula.data.{FileDao, MeetingRecordDao}
import uk.ac.warwick.tabula.services.attendancemonitoring.AttendanceMonitoringMeetingRecordService


class DownloadMeetingRecordCommandTest extends TestBase with Mockito {

	val aprilFool = dateTime(2013, DateTimeConstants.APRIL)

	@Test
	def validMeeting() = withUser("cusdx") { withFakeTime(aprilFool) {
		val meetingRecordDao = smartMock[MeetingRecordDao]
		val fileDao = smartMock[FileDao]
		val attendanceMonitoringMeetingRecordService = smartMock[AttendanceMonitoringMeetingRecordService]

		val creator = Fixtures.staff("9876543", "staffmember")
		val student = Fixtures.student(universityId="1170836", userId="studentmember")

		val relationship = ExternalStudentRelationship(
			"Professor A Tutor",
			StudentRelationshipType("tutor", "tutor", "personal tutor", "personal tutee"),
			student
		)

		val uploadedFile =  new UploadedFile
		val mpFile = smartMock[MultipartFile]
		uploadedFile.upload.add(mpFile)

		val fileAttach = new FileAttachment
		fileAttach.name = "Beltane.txt"
		uploadedFile.attached.add(fileAttach)

		val createMeetingRecordCommand = new CreateMeetingRecordCommandInternal(creator, relationship)
			with ModifyMeetingRecordCommandRequest
			with MeetingRecordServiceComponent
			with FeaturesComponent
			with AttendanceMonitoringMeetingRecordServiceComponent
			with FileAttachmentServiceComponent {
			override val meetingRecordService: MeetingRecordService = smartMock[MeetingRecordService]
			override val features: Features = Features.empty
			override val attendanceMonitoringMeetingRecordService: AttendanceMonitoringMeetingRecordService = smartMock[AttendanceMonitoringMeetingRecordService]
			override val fileAttachmentService: FileAttachmentService = smartMock[FileAttachmentService]
		}

		createMeetingRecordCommand.title = "Title"
		createMeetingRecordCommand.format = FaceToFace
		createMeetingRecordCommand.meetingDateTime  = dateTime(3903, DateTimeConstants.MARCH) // it's the future
		createMeetingRecordCommand.description = "Lovely words"
		createMeetingRecordCommand.file = uploadedFile

		val meeting = createMeetingRecordCommand.applyInternal()

		// test to see if DownloadMeetingRecordFilesCommand.apply() can be used to get the file
		val downloadCommand = new DownloadMeetingRecordFilesCommand(meeting)

		// normally for single files the filename is set in the command as it is a path variable (I think!)
		downloadCommand.filename = "Beltane.txt"
		val retSingle = downloadCommand.applyInternal()
		val rendFile = retSingle.get
		rendFile.filename should be ("Beltane.txt")
	}}
}
