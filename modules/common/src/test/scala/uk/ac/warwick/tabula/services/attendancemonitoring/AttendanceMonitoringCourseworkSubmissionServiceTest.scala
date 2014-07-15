package uk.ac.warwick.tabula.services.attendancemonitoring

import org.joda.time.DateTime
import uk.ac.warwick.tabula.data.model._
import uk.ac.warwick.tabula.data.model.attendance._
import uk.ac.warwick.tabula.services._
import uk.ac.warwick.tabula.{AcademicYear, Fixtures, Mockito, TestBase}

class AttendanceMonitoringCourseworkSubmissionServiceTest extends TestBase with Mockito {

	val mockProfileService = smartMock[ProfileService]
	val mockAttendanceMonitoringService = smartMock[AttendanceMonitoringService]
	val mockModuleAndDepartmentService = smartMock[ModuleAndDepartmentService]
	val mockAssignmentService = smartMock[AssignmentService]

	trait ServiceTestSupport extends AttendanceMonitoringServiceComponent
		with ProfileServiceComponent with AssignmentServiceComponent {

		val attendanceMonitoringService = mockAttendanceMonitoringService
		val assignmentService = mockAssignmentService
		val profileService = mockProfileService
	}

	trait Fixture {
		val service = new AbstractAttendanceMonitoringCourseworkSubmissionService with ServiceTestSupport

		val student = Fixtures.student("1234")

		val assignment = new Assignment
		assignment.openDate = new DateTime().minusDays(1)
		assignment.closeDate = new DateTime().plusMonths(1)
		assignment.openEnded = false
		assignment.academicYear = AcademicYear(2014)

		val submission = new Submission
		submission.userId = student.userId
		submission.universityId = student.universityId
		submission.submittedDate = new DateTime()
		submission.assignment = assignment

		assignment.addSubmission(submission)

		mockProfileService.getMemberByUniversityId(student.universityId) returns Option(student)

		val module1 = Fixtures.module("aa101")
		module1.id = "aa101"
		val module2 = Fixtures.module("aa202")
		module2.id = "aa202"
		mockModuleAndDepartmentService.getModuleById(module1.id) returns Option(module1)
		mockModuleAndDepartmentService.getModuleById(module2.id) returns Option(module2)

		mockAssignmentService.getAssignmentById(assignment.id) returns Option(assignment)

		val assignmentPoint = new AttendanceMonitoringPoint
		assignmentPoint.startDate = assignment.closeDate.minusDays(2).toLocalDate
		assignmentPoint.endDate = assignment.closeDate.plusDays(1).toLocalDate
		assignmentPoint.pointType = AttendanceMonitoringPointType.AssignmentSubmission
		assignmentPoint.assignmentSubmissionQuantity = 1
		assignmentPoint.assignmentSubmissionIsDisjunction = true
		assignmentPoint.assignmentSubmissionAssignments = Seq(new Assignment, assignment)
		assignmentPoint.assignmentSubmissionModules = Seq()

		assignmentPoint.assignmentService = mockAssignmentService
		assignmentPoint.moduleAndDepartmentService = mockModuleAndDepartmentService

		mockAttendanceMonitoringService.listStudentsPoints(student, None, assignment.academicYear) returns Seq(assignmentPoint)
		mockAttendanceMonitoringService.getCheckpoints(Seq(assignmentPoint), Seq(student)) returns Map()
		mockAttendanceMonitoringService.studentAlreadyReportedThisTerm(student, assignmentPoint) returns false
		mockAttendanceMonitoringService.setAttendance(student, Map(assignmentPoint -> AttendanceState.Attended), student.userId, autocreated = true) returns
			Seq(Fixtures.attendanceMonitoringCheckpoint(assignmentPoint, student, AttendanceState.Attended))
	}

	@Test
	def updatesCheckpointAnyAssignment() { new Fixture {
		service.getCheckpoints(submission).size should be (1)

		service.updateCheckpoints(submission)
		there was one (service.attendanceMonitoringService).setAttendance(student, Map(assignmentPoint -> AttendanceState.Attended), student.userId, autocreated = true)
	}}

	@Test
	def updatesCheckpointSpecificModule() { new Fixture {
		assignmentPoint.assignmentSubmissionIsSpecificAssignments = false
		assignmentPoint.assignmentSubmissionModules = Seq(module1)
		assignment.module = module1

		service.getCheckpoints(submission).size should be (1)

		service.updateCheckpoints(submission)
		there was one (service.attendanceMonitoringService).setAttendance(student, Map(assignmentPoint -> AttendanceState.Attended), student.userId, autocreated = true)
	}}

	@Test
	def lateSubmission() { new Fixture {
		submission.submittedDate = assignment.closeDate.plusDays(1)
		service.getCheckpoints(submission).size should be (0)
	}}

	@Test
	def noPoints() { new Fixture {
		service.attendanceMonitoringService.listStudentsPoints(student, None, assignment.academicYear) returns Seq()
		service.getCheckpoints(submission).size should be (0)
	}}

	@Test
	def pointDateInvalid() { new Fixture {
		assignmentPoint.endDate = assignment.closeDate.toLocalDate.minusDays(1)
		service.getCheckpoints(submission).size should be (0)
	}}

	@Test
	def wrongPointType() { new Fixture {
		assignmentPoint.pointType = AttendanceMonitoringPointType.Meeting
		service.getCheckpoints(submission).size should be (0)
	}}

	@Test
	def wrongModule() { new Fixture {
		assignmentPoint.assignmentSubmissionModules = Seq(module2)
		assignmentPoint.assignmentSubmissionIsSpecificAssignments = false
		service.getCheckpoints(submission).size should be (0)
	}}

	@Test
	def checkpointAlreadyExists() { new Fixture {
		mockAttendanceMonitoringService.getCheckpoints(Seq(assignmentPoint), Seq(student)) returns
			Map(
				student -> Map(
					assignmentPoint -> Fixtures.attendanceMonitoringCheckpoint(assignmentPoint, student, AttendanceState.Attended)
				)
			)
		service.getCheckpoints(submission).size should be (0)
	}}

	@Test
	def reportedToSITS() { new Fixture {
		mockAttendanceMonitoringService.studentAlreadyReportedThisTerm(student, assignmentPoint) returns true
		service.getCheckpoints(submission).size should be (0)
	}}

	@Test
	def notEnough() { new Fixture {
		assignmentPoint.assignmentSubmissionQuantity = 2
		assignmentPoint.assignmentSubmissionIsSpecificAssignments = false
		service.getCheckpoints(submission).size should be (0)
	}}

}
