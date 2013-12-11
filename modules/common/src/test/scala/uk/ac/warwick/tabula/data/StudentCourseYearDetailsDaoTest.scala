package uk.ac.warwick.tabula.data

import org.hibernate.annotations.AccessType
import org.junit.Before
import org.junit.runner.RunWith
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ContextConfiguration
import javax.persistence.Entity
import uk.ac.warwick.tabula.AcademicYear
import uk.ac.warwick.tabula.PersistenceTestBase
import uk.ac.warwick.tabula.data.model.Module
import uk.ac.warwick.tabula.data.model.ModuleRegistration
import uk.ac.warwick.tabula.data.model.ModuleSelectionStatus
import uk.ac.warwick.tabula.data.model.StudentCourseDetails
import uk.ac.warwick.tabula.data.model.StudentMember
import uk.ac.warwick.tabula.data.model.StudentCourseYearDetails
import uk.ac.warwick.tabula.Fixtures
import scala.collection.JavaConverters._
import org.joda.time.DateTime
import scala.collection.mutable.HashSet
import uk.ac.warwick.tabula.data.model.StudentCourseYearKey

class StudentCourseYearDetailsDaoTest extends PersistenceTestBase {

	val scydDao = new StudentCourseYearDetailsDaoImpl
	val scdDao = new StudentCourseDetailsDaoImpl
	val memDao = new MemberDaoImpl

	@Before
	def setup() {
		scydDao.sessionFactory = sessionFactory
		scdDao.sessionFactory = sessionFactory
		memDao.sessionFactory = sessionFactory
	}

	@Test def testGetBySceKey {
		transactional { tx =>
			val stuMem = new StudentMember("0123456")
			stuMem.userId = "abcde"
			memDao.saveOrUpdate(stuMem)
			val scd = new StudentCourseDetails(stuMem, "0123456/1")
			scd.sprCode = "0123456/2"
			scdDao.saveOrUpdate(scd)

			val nonexistantYearDetails = scydDao.getBySceKey(scd, 1)
			nonexistantYearDetails should be (None)

			val scyd = new StudentCourseYearDetails(scd, 1,AcademicYear(2013))

			scd.addStudentCourseYearDetails(scyd)

			scydDao.saveOrUpdate(scyd)
			scdDao.saveOrUpdate(scd)

			scd.freshStudentCourseYearDetails.size should be (1)

			val retrievedScyd = scydDao.getBySceKey(scd, 1).get
			retrievedScyd.isInstanceOf[StudentCourseYearDetails] should be (true)
			retrievedScyd.studentCourseDetails.scjCode should be ("0123456/1")
			retrievedScyd.studentCourseDetails.sprCode should be ("0123456/2")
			retrievedScyd.academicYear should be (AcademicYear(2013))
		}
	}

	@Test def testGetFreshKeys {
		transactional { tx =>
			val dept1 = Fixtures.department("hm", "History of Music")
			val dept2 = Fixtures.department("ar", "Architecture")

			session.saveOrUpdate(dept1)
			session.saveOrUpdate(dept2)

			val stu1 = Fixtures.student(universityId = "1000001", userId="student", department=dept1, courseDepartment=dept1)
			val stu2 = Fixtures.student(universityId = "1000002", userId="student", department=dept2, courseDepartment=dept2)
			val stu3 = Fixtures.student(universityId = "1000003", userId="student", department=dept2, courseDepartment=dept2)
			val stu4 = Fixtures.student(universityId = "1000004", userId="student", department=dept2, courseDepartment=dept2)

			session.saveOrUpdate(stu1)
			session.saveOrUpdate(stu2)
			session.saveOrUpdate(stu3)
			session.saveOrUpdate(stu4)

			scydDao.getFreshKeys.size should be (4)

			scydDao.getFreshKeys.head.scjCode.length() should be (9)
			val scjCodes = scydDao.getFreshKeys map { key => key.getScjCode}
			scjCodes.contains("1000001/1") should be (true)
			scjCodes.contains("1000002/1") should be (true)
			scjCodes.contains("1000003/1") should be (true)
			scjCodes.contains("1000004/1") should be (true)

			scydDao.getFreshKeys.head.sceSequenceNumber should be (1)

			val scyd = stu2.mostSignificantCourse.freshStudentCourseYearDetails.head

			scyd.missingFromImportSince = DateTime.now
			session.saveOrUpdate(scyd)

			scydDao.getFreshKeys.size should be (3)
		}
	}

	@Test
	def testStampMissingFromImport = transactional { tx =>
		val dept1 = Fixtures.department("hm", "History of Music")
		val dept2 = Fixtures.department("ar", "Architecture")

		session.saveOrUpdate(dept1)
		session.saveOrUpdate(dept2)

		val stu1 = Fixtures.student(universityId = "1000001", userId="student", department=dept1, courseDepartment=dept1)
		val stu2 = Fixtures.student(universityId = "1000002", userId="student", department=dept2, courseDepartment=dept2)
		val stu3 = Fixtures.student(universityId = "1000003", userId="student", department=dept2, courseDepartment=dept2)
		val stu4 = Fixtures.student(universityId = "1000004", userId="student", department=dept2, courseDepartment=dept2)

		memDao.saveOrUpdate(stu1)
		memDao.saveOrUpdate(stu2)
		memDao.saveOrUpdate(stu3)
		memDao.saveOrUpdate(stu4)
		session.flush
		session.clear

		val key1 = new StudentCourseYearKey(stu1.mostSignificantCourse.scjCode, 1)
		val key2 = new StudentCourseYearKey(stu2.mostSignificantCourse.scjCode, 1)
		val key3 = new StudentCourseYearKey(stu3.mostSignificantCourse.scjCode, 1)
		val key4 = new StudentCourseYearKey(stu4.mostSignificantCourse.scjCode, 1)

		val scyd1 = scydDao.getBySceKey(stu1.mostSignificantCourse, 1).get
		val scyd2 = scydDao.getBySceKey(stu2.mostSignificantCourse, 1).get
		val scyd3 = scydDao.getBySceKey(stu3.mostSignificantCourse, 1).get
		val scyd4 = scydDao.getBySceKey(stu4.mostSignificantCourse, 1).get

		scydDao.getBySceKey(stu1.mostSignificantCourse, 1) should be (Some(scyd1))
		scydDao.getBySceKey(stu2.mostSignificantCourse, 1) should be (Some(scyd2))
		scydDao.getBySceKey(stu3.mostSignificantCourse, 1) should be (Some(scyd3))
		scydDao.getBySceKey(stu4.mostSignificantCourse, 1) should be (Some(scyd4))

		scyd1.missingFromImportSince should be (null)
		scyd2.missingFromImportSince should be (null)
		scyd3.missingFromImportSince should be (null)
		scyd4.missingFromImportSince should be (null)

		val seenSceKeys = new HashSet[StudentCourseYearKey]
		seenSceKeys.add(key1)
		seenSceKeys.add(key3)

		val seenIds = scydDao.convertKeysToIds(seenSceKeys)

		// these 3 lines are in fact ImportRowTracker.newStaleScydIdeas but that's in scheduling so can't be accessed:
		val scydIdsSeen = scydDao.convertKeysToIds(seenSceKeys)
		val allFreshIds = scydDao.getFreshIds.toSet
		val newStaleScydIds = (allFreshIds -- scydIdsSeen).toSeq

		val importStart = DateTime.now
		scydDao.stampMissingFromImport(newStaleScydIds, importStart)

		for (id <- seenIds) logger.warn("seen ID: " + id)

		session.flush
		session.clear

		scydDao.getBySceKey(stu1.mostSignificantCourse, 1) should be (Some(scyd1))
		scydDao.getBySceKey(stu2.mostSignificantCourse, 1) should be (None)
		scydDao.getBySceKey(stu3.mostSignificantCourse, 1) should be (Some(scyd3))
		scydDao.getBySceKey(stu4.mostSignificantCourse, 1) should be (None)

	}

}
