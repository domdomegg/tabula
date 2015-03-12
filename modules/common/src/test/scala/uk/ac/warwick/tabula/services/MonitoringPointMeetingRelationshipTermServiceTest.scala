package uk.ac.warwick.tabula.services

import uk.ac.warwick.tabula.data.{MeetingRecordDao, MeetingRecordDaoComponent, MonitoringPointDaoComponent, MonitoringPointDao}
import uk.ac.warwick.tabula.{AcademicYear, TestBase, Fixtures, Mockito}
import uk.ac.warwick.tabula.data.model.attendance.{AttendanceState, MonitoringPointType, MonitoringPoint, MonitoringPointSet, MonitoringCheckpoint}
import uk.ac.warwick.tabula.JavaImports.JArrayList
import uk.ac.warwick.tabula.data.model._
import org.joda.time.DateTime
import org.mockito.Matchers
import uk.ac.warwick.util.termdates.TermImpl
import uk.ac.warwick.util.termdates.Term.TermType

class MonitoringPointMeetingRelationshipTermServiceTest extends TestBase with Mockito {
	trait ServiceTestSupport extends MonitoringPointDaoComponent with MeetingRecordDaoComponent
	with RelationshipServiceComponent with TermServiceComponent with MonitoringPointServiceComponent {

		val monitoringPointDao = mock[MonitoringPointDao]
		val meetingRecordDao = mock[MeetingRecordDao]
		var relationshipService = mock[RelationshipService]
		val termService = mock[TermService]
		val monitoringPointService = mock[MonitoringPointService]
		// return 2013 for any date
		termService.getTermFromDateIncludingVacations(any[DateTime]) returns new TermImpl(null, dateTime(2013, 1, 1), dateTime(2013, 12, 1), TermType.autumn)
	}

	trait StudentFixture {
		val service = new AbstractMonitoringPointMeetingRelationshipTermService with ServiceTestSupport

		val academicYear2012 = AcademicYear(2012)
		val academicYear2013 = AcademicYear(2013)

		val student = Fixtures.student("1234")
		val studentRoute = Fixtures.route("a100")
		val studentCourseDetails = student.mostSignificantCourseDetails.get
		studentCourseDetails.route = studentRoute

		val agent = "agent"
		val agentMember = Fixtures.staff(agent, agent)

		val tutorRelationshipType = StudentRelationshipType("personalTutor", "tutor", "personal tutor", "personal tutee")
		val supervisorRelationshipType = StudentRelationshipType("supervisor", "supervisor", "supervisor", "supervisee")

		val meetingRelationship = ExternalStudentRelationship(agent, tutorRelationshipType, student)

		val meeting = new MeetingRecord
		meeting.relationship = meetingRelationship
		meeting.format = MeetingFormat.FaceToFace
		meeting.creator = Fixtures.student("student", "student")
		meeting.meetingDate = dateTime(2013, 1, 7)
	}

	trait StudentYear2Fixture extends StudentFixture {
		val studentCourseYear1 = studentCourseDetails.latestStudentCourseYearDetails
		studentCourseYear1.yearOfStudy = 1
		studentCourseYear1.academicYear = academicYear2012

		val studentCourseYear2 = Fixtures.studentCourseYearDetails(academicYear2013)
		studentCourseYear2.yearOfStudy = 2
		studentCourseDetails.addStudentCourseYearDetails(studentCourseYear1)
		studentCourseDetails.addStudentCourseYearDetails(studentCourseYear2)
	}

	trait Year2PointSetFixture extends StudentYear2Fixture {
		val year2PointSet = new MonitoringPointSet
		year2PointSet.academicYear = studentCourseYear2.academicYear
		year2PointSet.route = studentRoute
		year2PointSet.year = 2

		service.monitoringPointService.getPointSetForStudent(student, academicYear2013) returns Option(year2PointSet)
	}

	trait ValidYear2PointFixture extends Year2PointSetFixture {
		val meetingThisYearPoint = new MonitoringPoint
		meetingThisYearPoint.pointSet = year2PointSet
		year2PointSet.points = JArrayList(meetingThisYearPoint)
		meetingThisYearPoint.validFromWeek = 1
		meetingThisYearPoint.requiredFromWeek = 1
		meetingThisYearPoint.pointType = MonitoringPointType.Meeting
		meetingThisYearPoint.meetingRelationships = Seq(meeting.relationship.relationshipType)
		meetingThisYearPoint.relationshipService = mock[RelationshipService]
		meetingThisYearPoint.relationshipService
			.getStudentRelationshipTypeById(meeting.relationship.relationshipType.id) returns Option(meeting.relationship.relationshipType)
		meetingThisYearPoint.meetingFormats = Seq(meeting.format)
	}

	@Test
	def updateMeetingNotApproved() { new StudentFixture {
		meeting.approvals = JArrayList(Fixtures.meetingRecordApproval(MeetingApprovalState.Pending))
		service.updateCheckpointsForMeeting(meeting)
		verify(service.monitoringPointDao, times(0)).saveOrUpdate(any[MonitoringCheckpoint])
	}}

	trait NoSuchStudent extends StudentFixture {
		val thisMeeting = new MeetingRecord
		thisMeeting.approvals = JArrayList(Fixtures.meetingRecordApproval(MeetingApprovalState.Approved))
		val thisMeetingRelationship = new ExternalStudentRelationship
		thisMeetingRelationship.agent = agent
		thisMeetingRelationship.relationshipType = supervisorRelationshipType
		thisMeeting.relationship = thisMeetingRelationship
	}

	@Test
	def willBeCreatedNoSuchStudent() { new NoSuchStudent {
		service.willCheckpointBeCreated(thisMeeting) should be (right = false)
	}}

	@Test
	def updateNoSuchStudent() { new NoSuchStudent {
		service.updateCheckpointsForMeeting(thisMeeting)
		verify(service.monitoringPointDao, times(0)).saveOrUpdate(any[MonitoringCheckpoint])
	}}

	trait NonMeetingPoint extends Year2PointSetFixture {
		val normalThisYearPoint = new MonitoringPoint
		normalThisYearPoint.pointSet = year2PointSet
		year2PointSet.points = JArrayList(normalThisYearPoint)

		meeting.approvals = JArrayList(Fixtures.meetingRecordApproval(MeetingApprovalState.Approved))
	}

	@Test
	def willBeCreatedNonMeetingPoint() { new NonMeetingPoint {
		service.willCheckpointBeCreated(meeting) should be (right = false)
	}}

	@Test
	def updateNonMeetingPoint() { new NonMeetingPoint {
		service.updateCheckpointsForMeeting(meeting)
		verify(service.monitoringPointDao, times(0)).getCheckpoint(any[MonitoringPoint], any[StudentMember])
		verify(service.termService, times(0)).getAcademicWeekForAcademicYear(any[DateTime], any[AcademicYear])
		verify(service.monitoringPointDao, times(0)).saveOrUpdate(any[MonitoringCheckpoint])
	}}

	trait MeetingPointWrongRelationship extends Year2PointSetFixture {
		val meetingThisYearPoint = new MonitoringPoint
		meetingThisYearPoint.pointSet = year2PointSet
		year2PointSet.points = JArrayList(meetingThisYearPoint)
		meetingThisYearPoint.pointType = MonitoringPointType.Meeting
		meetingThisYearPoint.meetingRelationships = Seq(supervisorRelationshipType)
		meetingThisYearPoint.relationshipService = mock[RelationshipService]
		meetingThisYearPoint.relationshipService.getStudentRelationshipTypeById(supervisorRelationshipType.id) returns Option(supervisorRelationshipType)

		meeting.approvals = JArrayList(Fixtures.meetingRecordApproval(MeetingApprovalState.Approved))
	}

	@Test
	def willBeCreatedMeetingPointWrongRelationship() { new MeetingPointWrongRelationship {
		service.willCheckpointBeCreated(meeting) should be (right = false)
	}}

	@Test
	def updateMeetingPointWrongRelationship() { new MeetingPointWrongRelationship {
		service.updateCheckpointsForMeeting(meeting)
		verify(service.monitoringPointDao, times(0)).getCheckpoint(any[MonitoringPoint], any[StudentMember])
		verify(service.termService, times(0)).getAcademicWeekForAcademicYear(any[DateTime], any[AcademicYear])
		verify(service.monitoringPointDao, times(0)).saveOrUpdate(any[MonitoringCheckpoint])
	}}

	trait MeetingPointWrongFormat extends Year2PointSetFixture {
		val meetingThisYearPoint = new MonitoringPoint
		meetingThisYearPoint.pointSet = year2PointSet
		year2PointSet.points = JArrayList(meetingThisYearPoint)
		meetingThisYearPoint.pointType = MonitoringPointType.Meeting
		meetingThisYearPoint.meetingRelationships = Seq(meeting.relationship.relationshipType)
		meetingThisYearPoint.relationshipService = mock[RelationshipService]
		meetingThisYearPoint.relationshipService
			.getStudentRelationshipTypeById(meeting.relationship.relationshipType.id) returns Option(meeting.relationship.relationshipType)
		meetingThisYearPoint.meetingFormats = Seq(MeetingFormat.Email)

		meeting.approvals = JArrayList(Fixtures.meetingRecordApproval(MeetingApprovalState.Approved))
	}

	@Test
	def willBeCreatedMeetingPointWrongFormat() { new MeetingPointWrongFormat {
		service.willCheckpointBeCreated(meeting) should be (right = false)
	}}

	@Test
	def updateMeetingPointWrongFormat() { new MeetingPointWrongFormat {
		service.updateCheckpointsForMeeting(meeting)
		verify(service.monitoringPointDao, times(0)).getCheckpoint(any[MonitoringPoint], any[StudentMember])
		verify(service.termService, times(0)).getAcademicWeekForAcademicYear(any[DateTime], any[AcademicYear])
		verify(service.monitoringPointDao, times(0)).saveOrUpdate(any[MonitoringCheckpoint])
	}}

	trait MeetingExistingCheckpoint extends Year2PointSetFixture {
		val meetingThisYearPoint = new MonitoringPoint
		meetingThisYearPoint.pointSet = year2PointSet
		year2PointSet.points = JArrayList(meetingThisYearPoint)
		meetingThisYearPoint.pointType = MonitoringPointType.Meeting
		meetingThisYearPoint.meetingRelationships = Seq(meeting.relationship.relationshipType)
		meetingThisYearPoint.relationshipService = mock[RelationshipService]
		meetingThisYearPoint.relationshipService
			.getStudentRelationshipTypeById(meeting.relationship.relationshipType.id) returns Option(meeting.relationship.relationshipType)
		meetingThisYearPoint.meetingFormats = Seq(meeting.format)
		service.monitoringPointDao.getCheckpoint(meetingThisYearPoint, student) returns Option(new MonitoringCheckpoint)

		meeting.approvals = JArrayList(Fixtures.meetingRecordApproval(MeetingApprovalState.Approved))
	}

	@Test
	def willBeCreatedMeetingExistingCheckpoint() { new MeetingExistingCheckpoint {
		service.willCheckpointBeCreated(meeting) should be (right = false)
	}}

	@Test
	def updateMeetingExistingCheckpoint() { new MeetingExistingCheckpoint {
		service.updateCheckpointsForMeeting(meeting)
		verify(service.monitoringPointDao, times(1)).getCheckpoint(meetingThisYearPoint, student)
		verify(service.termService, times(0)).getAcademicWeekForAcademicYear(any[DateTime], any[AcademicYear])
		verify(service.monitoringPointDao, times(0)).saveOrUpdate(any[MonitoringCheckpoint])
	}}

	trait MeetingBeforePoint extends Year2PointSetFixture {
		val meetingThisYearPoint = new MonitoringPoint
		meetingThisYearPoint.pointSet = year2PointSet
		year2PointSet.points = JArrayList(meetingThisYearPoint)
		meetingThisYearPoint.validFromWeek = 4
		meetingThisYearPoint.requiredFromWeek = 5
		meetingThisYearPoint.pointType = MonitoringPointType.Meeting
		meetingThisYearPoint.meetingRelationships = Seq(meeting.relationship.relationshipType)
		meetingThisYearPoint.relationshipService = mock[RelationshipService]
		meetingThisYearPoint.relationshipService
			.getStudentRelationshipTypeById(meeting.relationship.relationshipType.id) returns Option(meeting.relationship.relationshipType)
		meetingThisYearPoint.meetingFormats = Seq(meeting.format)
		service.monitoringPointDao.getCheckpoint(meetingThisYearPoint, student) returns None

		val meetingDate = dateTime(2013, 1, 7)
		service.termService.getAcademicWeekForAcademicYear(meetingDate, year2PointSet.academicYear) returns 2

		meeting.approvals = JArrayList(Fixtures.meetingRecordApproval(MeetingApprovalState.Approved))
		meeting.meetingDate = meetingDate
	}

	@Test
	def willBeCreatedMeetingBeforePoint() { new MeetingBeforePoint {
		service.willCheckpointBeCreated(meeting) should be (right = false)
	}}

	@Test
	def updateMeetingBeforePoint() { new MeetingBeforePoint {
		service.updateCheckpointsForMeeting(meeting)
		verify(service.monitoringPointDao, times(1)).getCheckpoint(meetingThisYearPoint, student)
		verify(service.termService, times(1)).getAcademicWeekForAcademicYear(any[DateTime], Matchers.eq(year2PointSet.academicYear))
		verify(service.monitoringPointDao, times(0)).saveOrUpdate(any[MonitoringCheckpoint])
	}}

	trait MeetingAfterPoint extends Year2PointSetFixture {
		val meetingThisYearPoint = new MonitoringPoint
		meetingThisYearPoint.pointSet = year2PointSet
		year2PointSet.points = JArrayList(meetingThisYearPoint)
		meetingThisYearPoint.validFromWeek = 4
		meetingThisYearPoint.requiredFromWeek = 5
		meetingThisYearPoint.pointType = MonitoringPointType.Meeting
		meetingThisYearPoint.meetingRelationships = Seq(meeting.relationship.relationshipType)
		meetingThisYearPoint.relationshipService = mock[RelationshipService]
		meetingThisYearPoint.relationshipService
			.getStudentRelationshipTypeById(meeting.relationship.relationshipType.id) returns Option(meeting.relationship.relationshipType)
		meetingThisYearPoint.meetingFormats = Seq(meeting.format)
		service.monitoringPointDao.getCheckpoint(meetingThisYearPoint, student) returns None

		val meetingDate = dateTime(2013, 1, 7)
		service.termService.getAcademicWeekForAcademicYear(meetingDate, year2PointSet.academicYear) returns 6

		meeting.approvals = JArrayList(Fixtures.meetingRecordApproval(MeetingApprovalState.Approved))
		meeting.meetingDate = meetingDate
	}

	@Test
	def willBeCreatedMeetingAfterPoint() { new MeetingAfterPoint {
		service.willCheckpointBeCreated(meeting) should be (right = false)
	}}

	@Test
	def updateMeetingAfterPoint() { new MeetingAfterPoint {
		service.updateCheckpointsForMeeting(meeting)
		verify(service.monitoringPointDao, times(1)).getCheckpoint(meetingThisYearPoint, student)
		verify(service.termService, times(1)).getAcademicWeekForAcademicYear(any[DateTime], Matchers.eq(year2PointSet.academicYear))
		verify(service.monitoringPointDao, times(0)).saveOrUpdate(any[MonitoringCheckpoint])
	}}

	trait ValidPointNotEnoughMeetingsApproved extends ValidYear2PointFixture {
		meetingThisYearPoint.meetingQuantity = 2

		service.monitoringPointDao.getCheckpoint(meetingThisYearPoint, student) returns None
		service.termService.getAcademicWeekForAcademicYear(any[DateTime], Matchers.eq(year2PointSet.academicYear)) returns 2
		service.relationshipService.getRelationships(meetingThisYearPoint.meetingRelationships.head, student) returns Seq(meetingRelationship)

		val otherMeeting = new MeetingRecord
		otherMeeting.approvals = JArrayList(Fixtures.meetingRecordApproval(MeetingApprovalState.Pending))

		service.meetingRecordDao.list(meetingRelationship) returns Seq(meeting, otherMeeting)

		meeting.approvals = JArrayList(Fixtures.meetingRecordApproval(MeetingApprovalState.Approved))
		meeting.meetingDate = DateTime.now
	}

	@Test
	def willBeCreatedValidPointNotEnoughMeetingsApproved() { new ValidPointNotEnoughMeetingsApproved {
		service.willCheckpointBeCreated(meeting) should be (right = false)
	}}

	@Test
	def updateValidPointNotEnoughMeetingsApproved() { new ValidPointNotEnoughMeetingsApproved {
		service.updateCheckpointsForMeeting(meeting)
		verify(service.monitoringPointDao, times(1)).getCheckpoint(meetingThisYearPoint, student)
		verify(service.termService, times(1)).getAcademicWeekForAcademicYear(any[DateTime], Matchers.eq(year2PointSet.academicYear))
		verify(service.monitoringPointDao, times(0)).saveOrUpdate(any[MonitoringCheckpoint])
	}}

	trait ValidPointNotEnoughMeetingsCorrectFormat extends ValidYear2PointFixture {
		meetingThisYearPoint.meetingQuantity = 2

		service.monitoringPointDao.getCheckpoint(meetingThisYearPoint, student) returns None
		service.termService.getAcademicWeekForAcademicYear(any[DateTime], Matchers.eq(year2PointSet.academicYear)) returns 2
		service.relationshipService.getRelationships(meetingThisYearPoint.meetingRelationships.head, student) returns Seq(meetingRelationship)

		val otherMeeting = new MeetingRecord
		otherMeeting.approvals = JArrayList(Fixtures.meetingRecordApproval(MeetingApprovalState.Approved))
		otherMeeting.format = MeetingFormat.Email

		service.meetingRecordDao.list(meetingRelationship) returns Seq(meeting, otherMeeting)

		meeting.approvals = JArrayList(Fixtures.meetingRecordApproval(MeetingApprovalState.Approved))
		meeting.meetingDate = DateTime.now
	}

	@Test
	def willBeCreatedValidPointNotEnoughMeetingsCorrectFormat() { new ValidPointNotEnoughMeetingsCorrectFormat {
		service.willCheckpointBeCreated(meeting) should be (right = false)
	}}

	@Test
	def updateValidPointNotEnoughMeetingsCorrectFormat() { new ValidPointNotEnoughMeetingsCorrectFormat {
		service.updateCheckpointsForMeeting(meeting)
		verify(service.monitoringPointDao, times(1)).getCheckpoint(meetingThisYearPoint, student)
		verify(service.termService, times(1)).getAcademicWeekForAcademicYear(any[DateTime], Matchers.eq(year2PointSet.academicYear))
		verify(service.monitoringPointDao, times(0)).saveOrUpdate(any[MonitoringCheckpoint])
	}}

	trait ValidPointNotEnoughMeetingsValidWeek extends ValidYear2PointFixture {
		// needs 2 meetings
		meetingThisYearPoint.meetingQuantity = 2
		val beforeDate = dateTime(2013, 1, 7).minusDays(7)
		val meetingDate = dateTime(2013, 1, 7)

		service.monitoringPointDao.getCheckpoint(meetingThisYearPoint, student) returns None
		service.termService.getAcademicWeekForAcademicYear(beforeDate, year2PointSet.academicYear) returns meetingThisYearPoint.validFromWeek - 1
		service.termService.getAcademicWeekForAcademicYear(meetingDate, year2PointSet.academicYear) returns meetingThisYearPoint.validFromWeek
		service.relationshipService.getRelationships(meetingThisYearPoint.meetingRelationships.head, student) returns Seq(meetingRelationship)

		val otherMeeting = new MeetingRecord
		otherMeeting.approvals = JArrayList(Fixtures.meetingRecordApproval(MeetingApprovalState.Approved))
		otherMeeting.format = MeetingFormat.FaceToFace

		// only 1 is valid
		otherMeeting.meetingDate = beforeDate
		meeting.meetingDate = meetingDate

		service.meetingRecordDao.list(meetingRelationship) returns Seq(meeting, otherMeeting)

		meeting.approvals = JArrayList(Fixtures.meetingRecordApproval(MeetingApprovalState.Approved))
	}

	@Test
	def willBeCreatedValidPointNotEnoughMeetingsValidWeek() { new ValidPointNotEnoughMeetingsValidWeek {
		service.willCheckpointBeCreated(meeting) should be (right = false)
	}}

	@Test
	def updateValidPointNotEnoughMeetingsValidWeek() { new ValidPointNotEnoughMeetingsValidWeek {
		service.updateCheckpointsForMeeting(meeting)
		verify(service.monitoringPointDao, times(1)).getCheckpoint(meetingThisYearPoint, student)
		verify(service.termService, times(2)).getAcademicWeekForAcademicYear(meetingDate, year2PointSet.academicYear)
		verify(service.termService, times(1)).getAcademicWeekForAcademicYear(beforeDate, year2PointSet.academicYear)
		verify(service.monitoringPointDao, times(0)).saveOrUpdate(any[MonitoringCheckpoint])
	}}

	trait ValidPointValidMeetings extends ValidYear2PointFixture {
		// needs 2 meetings
		meetingThisYearPoint.meetingQuantity = 2
		val beforeDate = dateTime(2013, 1, 7).minusDays(7)
		val meetingDate = dateTime(2013, 1, 7).minusDays(7)

		service.monitoringPointDao.getCheckpoint(meetingThisYearPoint, student) returns None
		service.termService.getAcademicWeekForAcademicYear(beforeDate, year2PointSet.academicYear) returns meetingThisYearPoint.validFromWeek - 1
		service.termService.getAcademicWeekForAcademicYear(meetingDate, year2PointSet.academicYear) returns meetingThisYearPoint.validFromWeek
		service.relationshipService.getRelationships(meetingThisYearPoint.meetingRelationships.head, student) returns Seq(meetingRelationship)

		val otherMeeting = new MeetingRecord
		otherMeeting.approvals = JArrayList(Fixtures.meetingRecordApproval(MeetingApprovalState.Approved))
		otherMeeting.format = MeetingFormat.FaceToFace

		// 2 are valid
		otherMeeting.meetingDate = meetingDate
		meeting.meetingDate = meetingDate

		service.meetingRecordDao.list(meetingRelationship) returns Seq(meeting, otherMeeting)
	}

	@Test
	def willBeCreatedValidPointValidMeetings() { new ValidPointValidMeetings {
		// the current meeting is pending, but if it were approved it should create the checkpoint
		meeting.approvals = JArrayList(Fixtures.meetingRecordApproval(MeetingApprovalState.Pending))
		service.willCheckpointBeCreated(meeting) should be (right = true)
	}}

	@Test
	def updateValidPointValidMeetings() { new ValidPointValidMeetings {
		meeting.approvals = JArrayList(Fixtures.meetingRecordApproval(MeetingApprovalState.Approved))
		val createdCheckpoints = service.updateCheckpointsForMeeting(meeting)
		verify(service.monitoringPointDao, times(1)).getCheckpoint(meetingThisYearPoint, student)
		verify(service.termService, times(3)).getAcademicWeekForAcademicYear(meetingDate, year2PointSet.academicYear)
		verify(service.monitoringPointDao, times(1)).saveOrUpdate(any[MonitoringCheckpoint])
		createdCheckpoints.size should be (1)
		createdCheckpoints.head.state should be (AttendanceState.Attended)
		createdCheckpoints.head.student should be (student)
		createdCheckpoints.head.point should be (meetingThisYearPoint)
		createdCheckpoints.head.updatedBy should be (agentMember.userId)
	}}

	@Test
	def willBeCreatedValidPointValidMeetingsAlreadyReported() { new ValidPointValidMeetings {
		service.monitoringPointService.studentAlreadyReportedThisTerm(student, meetingThisYearPoint) returns (true)

		// the current meeting is pending, but if it were approved it should create the checkpoint
		meeting.approvals = JArrayList(Fixtures.meetingRecordApproval(MeetingApprovalState.Pending))
		service.willCheckpointBeCreated(meeting) should be (right = false)
	}}

	@Test
	def updateValidPointValidMeetingsAlreadyReported() { new ValidPointValidMeetings {
		service.monitoringPointService.studentAlreadyReportedThisTerm(student, meetingThisYearPoint) returns (true)

		meeting.approvals = JArrayList(Fixtures.meetingRecordApproval(MeetingApprovalState.Approved))
		service.updateCheckpointsForMeeting(meeting)
		verify(service.monitoringPointDao, times(1)).getCheckpoint(meetingThisYearPoint, student)
		verify(service.termService, times(1)).getAcademicWeekForAcademicYear(meetingDate, year2PointSet.academicYear)
		verify(service.monitoringPointDao, times(0)).saveOrUpdate(any[MonitoringCheckpoint])
	}}

	trait ValidPointMeetingNotApprovedButCreatedByAgent extends ValidYear2PointFixture {
		// needs 2 meetings
		meetingThisYearPoint.meetingQuantity = 2
		val beforeDate = dateTime(2013, 1, 7).minusDays(7)
		val meetingDate = dateTime(2013, 1, 7)

		service.monitoringPointDao.getCheckpoint(meetingThisYearPoint, student) returns None
		service.termService.getAcademicWeekForAcademicYear(beforeDate, year2PointSet.academicYear) returns meetingThisYearPoint.validFromWeek - 1
		service.termService.getAcademicWeekForAcademicYear(meetingDate, year2PointSet.academicYear) returns meetingThisYearPoint.validFromWeek
		service.relationshipService.getRelationships(meetingThisYearPoint.meetingRelationships.head, student) returns Seq(meetingRelationship)

		val otherMeeting = new MeetingRecord
		otherMeeting.approvals = JArrayList(Fixtures.meetingRecordApproval(MeetingApprovalState.Pending))
		otherMeeting.format = MeetingFormat.FaceToFace
		otherMeeting.relationship = meetingRelationship
		otherMeeting.creator = agentMember

		// 2 are valid
		otherMeeting.meetingDate = meetingDate
		meeting.meetingDate = meetingDate

		service.meetingRecordDao.list(meetingRelationship) returns Seq(meeting, otherMeeting)
		meeting.approvals = JArrayList(Fixtures.meetingRecordApproval(MeetingApprovalState.Pending))
		meeting.creator = agentMember
	}

	@Test
	def willBeCreatedValidPointMeetingNotApprovedButCreatedByAgent() { new ValidPointMeetingNotApprovedButCreatedByAgent {
		service.willCheckpointBeCreated(meeting) should be (right = true)
	}}

	@Test
	def updateValidPointMeetingNotApprovedButCreatedByAgent() { new ValidPointMeetingNotApprovedButCreatedByAgent {
		val createdCheckpoints = service.updateCheckpointsForMeeting(meeting)
		verify(service.monitoringPointDao, times(1)).getCheckpoint(meetingThisYearPoint, student)
		verify(service.termService, times(3)).getAcademicWeekForAcademicYear(meetingDate, year2PointSet.academicYear)
		verify(service.monitoringPointDao, times(1)).saveOrUpdate(any[MonitoringCheckpoint])
		createdCheckpoints.size should be (1)
		createdCheckpoints.head.state should be (AttendanceState.Attended)
		createdCheckpoints.head.student should be (student)
		createdCheckpoints.head.point should be (meetingThisYearPoint)
		createdCheckpoints.head.updatedBy should be (agentMember.userId)
	}}

}
