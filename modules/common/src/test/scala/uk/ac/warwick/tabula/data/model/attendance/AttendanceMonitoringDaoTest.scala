package uk.ac.warwick.tabula.data.model.attendance

import org.joda.time.{DateTime, LocalDate}
import org.junit.Before
import uk.ac.warwick.tabula.commands.MemberOrUser
import uk.ac.warwick.tabula.data.AttendanceMonitoringDaoImpl
import uk.ac.warwick.tabula.data.model.UserGroup
import uk.ac.warwick.tabula._

class AttendanceMonitoringDaoTest extends PersistenceTestBase with Mockito {

	val academicYear = AcademicYear(2014)
	val department = Fixtures.department("its")
	val route = Fixtures.route("it100")

	val student1 = Fixtures.student("1234","1234")
	student1.mostSignificantCourse.beginDate = DateTime.now.minusYears(2).toLocalDate
	student1.mostSignificantCourse.route = route
	student1.mostSignificantCourse.latestStudentCourseYearDetails.academicYear = academicYear

	val student2 = Fixtures.student("2345","2345")
	student2.mostSignificantCourse.beginDate = DateTime.now.minusYears(2).toLocalDate
	student2.mostSignificantCourse.route = route
	student2.mostSignificantCourse.latestStudentCourseYearDetails.academicYear = academicYear

	val userLookup = new MockUserLookup
	userLookup.registerUserObjects(
		MemberOrUser(student1).asUser,
		MemberOrUser(student2).asUser
	)

	val attendanceMonitoringDao = new AttendanceMonitoringDaoImpl

	val scheme1 = new AttendanceMonitoringScheme
	scheme1.academicYear = academicYear
	scheme1.department = department
	scheme1.pointStyle = AttendanceMonitoringPointStyle.Date
	scheme1.members = UserGroup.ofUniversityIds
	scheme1.members.addUserId(student1.universityId)
	scheme1.members.addUserId(student2.universityId)
	scheme1.createdDate = DateTime.now
	scheme1.updatedDate = DateTime.now

	val point1 = Fixtures.attendanceMonitoringPoint(scheme1, name = "point1")
	point1.startDate = new LocalDate(2014, 10, 1)
	point1.endDate = new LocalDate(2014, 10, 1)
	point1.pointType = AttendanceMonitoringPointType.Standard
	point1.createdDate = DateTime.now
	point1.updatedDate = DateTime.now

	val point2 = Fixtures.attendanceMonitoringPoint(scheme1, name = "point2")
	point2.startDate = new LocalDate(2014, 10, 1)
	point2.endDate = new LocalDate(2014, 10, 4)
	point2.pointType = AttendanceMonitoringPointType.Standard
	point2.createdDate = DateTime.now
	point2.updatedDate = DateTime.now

	val point3 = Fixtures.attendanceMonitoringPoint(scheme1, name = "point3")
	point3.startDate = new LocalDate(2014, 10, 4)
	point3.endDate = new LocalDate(2014, 10, 4)
	point3.pointType = AttendanceMonitoringPointType.Standard
	point3.createdDate = DateTime.now
	point3.updatedDate = DateTime.now

	val point4 = Fixtures.attendanceMonitoringPoint(scheme1, name = "point4")
	point4.startDate = new LocalDate(2014, 10, 5)
	point4.endDate = new LocalDate(2014, 10, 5)
	point4.pointType = AttendanceMonitoringPointType.Standard
	point4.createdDate = DateTime.now
	point4.updatedDate = DateTime.now

	@Before
	def setup() {
		attendanceMonitoringDao.sessionFactory = sessionFactory
	}

	@Test def findUnrecordedPoints() { transactional { tx =>
		session.save(department)
		session.save(route)
		session.save(student1)
		session.save(student2)
		session.save(scheme1)
		session.save(point1)
		session.save(point2)
		session.save(point3)
		session.save(point4)

		val point1student1checkpoint = Fixtures.attendanceMonitoringCheckpoint(point1, student1, AttendanceState.Attended)
		point1student1checkpoint.updatedBy = ""
		point1student1checkpoint.updatedDate = DateTime.now
		session.save(point1student1checkpoint)

		val point2student1checkpoint = Fixtures.attendanceMonitoringCheckpoint(point2, student1, AttendanceState.Attended)
		point2student1checkpoint.updatedBy = ""
		point2student1checkpoint.updatedDate = DateTime.now
		session.save(point2student1checkpoint)

		val point2student2checkpoint = Fixtures.attendanceMonitoringCheckpoint(point2, student2, AttendanceState.Attended)
		point2student2checkpoint.updatedBy = ""
		point2student2checkpoint.updatedDate = DateTime.now
		session.save(point2student2checkpoint)

		val points = attendanceMonitoringDao.findUnrecordedPoints(department, academicYear, new LocalDate(2014, 10, 4))
		points.size should be (2)
		// 2 students, 1 recorded
		points.contains(point1) should be {true}
		// 2 students, 2 recorded
		points.contains(point2) should be {false}
		// 2 students, 0 recorded
		points.contains(point3) should be {true}
		// Point too late
		points.contains(point4) should be {false}

	}}

	@Test def findUnrecordedStudents() { transactional { tx =>
		session.save(department)
		session.save(route)
		session.save(student1)
		session.save(student2)
		session.save(scheme1)
		session.save(point1)
		session.save(point2)
		session.save(point3)
		session.save(point4)

		scheme1.members.asInstanceOf[UserGroup].userLookup = userLookup

		val point1student1checkpoint = Fixtures.attendanceMonitoringCheckpoint(point1, student1, AttendanceState.Attended)
		point1student1checkpoint.updatedBy = ""
		point1student1checkpoint.updatedDate = DateTime.now
		session.save(point1student1checkpoint)

		val point2student1checkpoint = Fixtures.attendanceMonitoringCheckpoint(point2, student1, AttendanceState.Attended)
		point2student1checkpoint.updatedBy = ""
		point2student1checkpoint.updatedDate = DateTime.now
		session.save(point2student1checkpoint)

		val point2student2checkpoint = Fixtures.attendanceMonitoringCheckpoint(point2, student2, AttendanceState.Attended)
		point2student2checkpoint.updatedBy = ""
		point2student2checkpoint.updatedDate = DateTime.now
		session.save(point2student2checkpoint)

		val users = attendanceMonitoringDao.findUnrecordedStudents(department, academicYear, new LocalDate(2014, 10, 4))
		users.size should be (2)
	}}

	@Test def resetTotalsForStudentsNotInAScheme() { transactional { tx =>
		session.save(department)
		session.save(route)
		session.save(student1)
		session.save(student2)
		session.save(scheme1)
		session.save(point1)
		session.save(point2)
		session.save(point3)
		session.save(point4)

		val studentNotOnAScheme = Fixtures.student("3456","3456")
		studentNotOnAScheme.mostSignificantCourse.beginDate = DateTime.now.minusYears(2).toLocalDate
		studentNotOnAScheme.mostSignificantCourse.route = route
		session.save(studentNotOnAScheme)

		val student1Totals = Fixtures.attendanceMonitoringCheckpointTotal(student1, department, academicYear, 1, 1, 1, 1)
		session.save(student1Totals)
		val student2Totals = Fixtures.attendanceMonitoringCheckpointTotal(student2, department, academicYear, 1, 1, 1, 1)
		session.save(student2Totals)
		val studentNotOnASchemeTotals = Fixtures.attendanceMonitoringCheckpointTotal(studentNotOnAScheme, department, academicYear, 1, 1, 1, 1)
		session.save(studentNotOnASchemeTotals)

		attendanceMonitoringDao.resetTotalsForStudentsNotInAScheme(department, academicYear)
		student1Totals.attended should be (1)
		student1Totals.authorised should be (1)
		student1Totals.unauthorised should be (1)
		student1Totals.unrecorded should be (1)
		student2Totals.attended should be (1)
		student2Totals.authorised should be (1)
		student2Totals.unauthorised should be (1)
		student2Totals.unrecorded should be (1)
		studentNotOnASchemeTotals.attended should be (0)
		studentNotOnASchemeTotals.authorised should be (0)
		studentNotOnASchemeTotals.unauthorised should be (0)
		studentNotOnASchemeTotals.unrecorded should be (0)
	}}

	@Test def correctEndDate(): Unit = { transactional { tx =>
		// 2 SCDs, both null end date
		val newSCD = Fixtures.studentCourseDetails(student1, department, scjCode = "1234/2")
		newSCD.beginDate = DateTime.now.minusYears(2).toLocalDate
		newSCD.route = route
		newSCD.latestStudentCourseYearDetails.academicYear = academicYear
		session.save(department)
		session.save(route)
		session.save(student1)
		val result1 = attendanceMonitoringDao.getAttendanceMonitoringDataForStudents(Seq(student1.universityId), academicYear)
		result1.size should be (1)
		result1.head.scdEndDate.isEmpty should be {true}

		// 2 SCDs, 1 null end date
		newSCD.endDate = newSCD.beginDate.plusYears(3)
		session.update(newSCD)
		val result2 = attendanceMonitoringDao.getAttendanceMonitoringDataForStudents(Seq(student1.universityId), academicYear)
		result2.size should be (1)
		result2.head.scdEndDate.isEmpty should be {true}

		// 2 SCDs, no null end date
		student1.freshStudentCourseDetails.zipWithIndex.foreach{case(scd, i) =>
			scd.endDate = scd.beginDate.plusYears(i)
				session.update(scd)
		}
		val result3 = attendanceMonitoringDao.getAttendanceMonitoringDataForStudents(Seq(student1.universityId), academicYear)
		result3.size should be (1)
		result3.head.scdEndDate.nonEmpty should be {true}
		import uk.ac.warwick.tabula.helpers.DateTimeOrdering._
		result3.head.scdEndDate.get should be (student1.freshStudentCourseDetails.map(_.endDate).max)
	}}

}
