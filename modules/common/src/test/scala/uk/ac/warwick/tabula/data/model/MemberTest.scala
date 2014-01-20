package uk.ac.warwick.tabula.data.model

import uk.ac.warwick.tabula.{AcademicYear, Fixtures, Mockito, PersistenceTestBase}
import uk.ac.warwick.tabula.services.{ProfileService, RelationshipService, RelationshipServiceImpl}
import uk.ac.warwick.tabula.data.MemberDaoImpl
import org.junit.Before
import uk.ac.warwick.tabula.data.StudentCourseDetailsDao
import uk.ac.warwick.tabula.data.StudentCourseDetailsDaoImpl

class MemberTest extends PersistenceTestBase with Mockito {

	val profileService = mock[ProfileService]
	val relationshipService = mock[RelationshipService]
	val memberDao = new MemberDaoImpl
	val studentCourseDetailsDao = new StudentCourseDetailsDaoImpl

	@Before def setup() {
		memberDao.sessionFactory = sessionFactory
		studentCourseDetailsDao.sessionFactory = sessionFactory
	}

	@Test def testAffiliatedDepartments {
		val member = new StudentMember
		member.universityId = "01234567"

		// create their home department
		val homeDept = new Department
		homeDept.code = "zx"

		// create another department where they're taking a module
		val extDept = new Department
		extDept.code = "pi"

		// set home department and test
		member.homeDepartment = homeDept
		member.affiliatedDepartments should be (Stream(homeDept))

		// set course department and test
		// create their course department
		val courseDept = new Department
		courseDept.code = "mn"

		val studentCourseDetails = new StudentCourseDetails(member, "2222222/2")
		studentCourseDetails.department = courseDept
		studentCourseDetails.mostSignificant = true
		studentCourseDetails.relationshipService = relationshipService

		member.attachStudentCourseDetails(studentCourseDetails)
		member.mostSignificantCourse = studentCourseDetails

		// add module registrations
		val mod1 = new Module
		val mod2 = new Module
		mod1.department = extDept
		mod2.department = homeDept
		val modReg1 = new ModuleRegistration(studentCourseDetails, mod1, new java.math.BigDecimal("12.0"), AcademicYear(2012), "A")
		val modReg2 = new ModuleRegistration(studentCourseDetails, mod2, new java.math.BigDecimal("12.0"), AcademicYear(2013), "A")
		studentCourseDetails.moduleRegistrations.add(modReg1)
		studentCourseDetails.moduleRegistrations.add(modReg2)

		member.mostSignificantCourseDetails.get.department = courseDept

		// now test that the member is attached to the right departments
		member.affiliatedDepartments should be (Stream(homeDept, courseDept))
		member.touchedDepartments should be (Stream(homeDept, courseDept, extDept))

		// also set route department and test
		val routeDept = new Department
		routeDept.code = "ch"
		val route = new Route
		route.department = routeDept
		member.mostSignificantCourseDetails.get.route = route
		member.freshStudentCourseDetails += studentCourseDetails

		member.affiliatedDepartments should be (Stream(homeDept, courseDept, routeDept))
		member.touchedDepartments should be (Stream(homeDept, courseDept, routeDept, extDept))

		// reset route to home, and check it appears only once
		route.department = homeDept
		member.mostSignificantCourseDetails.get.route = route

		member.affiliatedDepartments should be (Stream(homeDept, courseDept))
		member.touchedDepartments should be (Stream(homeDept, courseDept, extDept))
	}

	@Test def testModuleRegistrations {
		val member = new StudentMember
		member.universityId = "01234567"

		// create a student course details with module registrations
		val scd1 = new StudentCourseDetails(member, "2222222/2")
		member.attachStudentCourseDetails(scd1)

		val mod1 = new Module
		val mod2 = new Module
		val modReg1 = new ModuleRegistration(scd1, mod1, new java.math.BigDecimal("12.0"), AcademicYear(2012), "A")
		val modReg2 = new ModuleRegistration(scd1, mod2, new java.math.BigDecimal("12.0"), AcademicYear(2013), "A")
		scd1.moduleRegistrations.add(modReg1)
		scd1.moduleRegistrations.add(modReg2)

		member.registeredModulesByYear(Some(AcademicYear(2013))) should be (Set(mod2))
		member.registeredModulesByYear(None) should be (Set(mod1, mod2))

		// create another student course details with module registrations
		val scd2 = new StudentCourseDetails(member, "2222222/3")
		member.attachStudentCourseDetails(scd2)

		val mod3 = new Module
		val mod4 = new Module
		val modReg3 = new ModuleRegistration(scd2, mod3, new java.math.BigDecimal("12.0"), AcademicYear(2012), "A")
		val modReg4 = new ModuleRegistration(scd2, mod4, new java.math.BigDecimal("12.0"), AcademicYear(2013), "A")
		scd2.moduleRegistrations.add(modReg3)
		scd2.moduleRegistrations.add(modReg4)

		member.registeredModulesByYear(Some(AcademicYear(2013))) should be (Set(mod2, mod4))
		member.registeredModulesByYear(None) should be (Set(mod1, mod2, mod3, mod4))

		member.moduleRegistrationsByYear(None) should be (Set(modReg1, modReg2, modReg3, modReg4))
		member.moduleRegistrationsByYear(Some(AcademicYear(2012))) should be (Set(modReg1, modReg3))
	}

	@Test def nullUsers {
		val member = new StaffMember
		member.fullName should be (None)

		member.lastName = "Bono"
		member.fullName should be (Some("Bono"))

		member.firstName = "Sonny"
		member.fullName should be (Some("Sonny Bono"))

		member.lastName = null
		member.fullName should be (Some("Sonny"))
	}

	@Test def fromCurrentUser = withUser("cuscav", "0672089") {
		currentUser.realUser.setFirstName("Mat")
		currentUser.realUser.setLastName("Mannion")
		currentUser.realUser.setEmail("M.Mannion@warwick.ac.uk")
		currentUser.realUser.setStaff(true)

		val member = new RuntimeMember(currentUser)
		member.userType should be (MemberUserType.Staff)
		member.userId should be ("cuscav")
		member.universityId should be ("0672089")
		member.firstName should be ("Mat")
		member.lastName should be ("Mannion")
		member.fullName should be (Some("Mat Mannion"))
		member.email should be ("M.Mannion@warwick.ac.uk")
		member.description should be ("")
		member.isStaff should be (true)
		member.isStudent should be (false)

		val user = member.asSsoUser
		user.getUserId should be ("cuscav")
		user.getWarwickId should be ("0672089")
		user.getFirstName should be ("Mat")
		user.getLastName should be ("Mannion")
		user.getFullName should be ("Mat Mannion")
		user.getEmail should be ("M.Mannion@warwick.ac.uk")
		user.getDepartment should be (null)
		user.getDepartmentCode should be (null)
		user.isFoundUser should be (true)
		user.isVerified should be (true)
		user.getUserType should be ("Staff")
		user.isStaff should be (true)
		user.isStudent should be (false)
		user.getExtraProperty("urn:websignon:usertype") should be ("Staff")
		Option(user.getExtraProperty("urn:websignon:timestamp")) should be ('defined)
		user.getExtraProperty("urn:websignon:usersource") should be ("Tabula")
	}

	@Test def description = {
		val dept = Fixtures.department("in", "IT Services")

		val staff = new StaffMember
		staff.jobTitle = "Web Developer"
		staff.homeDepartment = dept

		staff.description should be ("Web Developer, IT Services")

		val route = Fixtures.route("G503", "MEng Computer Science")

		val student = new StudentMember
		student.groupName = "Undergraduate student"

		val studentCourseDetails = new StudentCourseDetails(student, "1111111/1")
		studentCourseDetails.route = route
		studentCourseDetails.mostSignificant = true

		student.attachStudentCourseDetails(studentCourseDetails)
		student.homeDepartment = dept
		student.mostSignificantCourse = studentCourseDetails

		val desc = student.description
		student.description should be ("Undergraduate student, MEng Computer Science, IT Services")
	}

	@Test def isRelationshipAgent {
		val staff = new StaffMember
		staff.profileService = profileService
		staff.relationshipService = relationshipService

		val relationshipType = StudentRelationshipType("tutor", "tutor", "personal tutor", "personal tutee")

		relationshipService.listStudentRelationshipsWithMember(relationshipType, staff) returns (Seq())
		staff.isRelationshipAgent(relationshipType) should be (false)

		relationshipService.listStudentRelationshipsWithMember(relationshipType, staff) returns (Seq(StudentRelationship("0672089", relationshipType, "0205225/1")))
		staff.isRelationshipAgent(relationshipType) should be (true)
	}

	@Test def deleteFileAttachmentOnDelete = transactional { tx =>
		// TAB-667
		val orphanAttachment =flushing(session){
			val attachment = new FileAttachment

			session.save(attachment)
			attachment
		}

		val (member, memberAttachment) = flushing(session){
			val member = new StudentMember
			member.universityId = "01234567"
			member.userId = "steve"

			val attachment = new FileAttachment
			member.photo = attachment

			session.save(member)
			(member, attachment)
		}

		// Ensure everything's been persisted
		orphanAttachment.id should not be (null)
		member.id should not be (null)
		memberAttachment.id should not be (null)

		// Can fetch everything from db
		flushing(session){
			session.get(classOf[FileAttachment], orphanAttachment.id) should be (orphanAttachment)
			// Can't do an exact equality check because of Hibernate polymorphism
			session.get(classOf[Member], member.id).asInstanceOf[Member].universityId should be (member.universityId)
			session.get(classOf[FileAttachment], memberAttachment.id).asInstanceOf[FileAttachment].id should be (memberAttachment.id)
		}

		flushing(session){session.delete(member) }

		// Ensure we can't fetch the FeedbackTemplate or attachment, but all the other objects are returned
		flushing(session){
			session.get(classOf[FileAttachment], orphanAttachment.id) should be (orphanAttachment)
			session.get(classOf[Member], member.id) should be (null)
			session.get(classOf[FileAttachment], memberAttachment.id) should be (null)
		}
	}

	@Test def testPermanentlyWithdrawn = transactional { tx =>

		val dept1 = Fixtures.department("ms", "Motorsport")
		session.save(dept1)
		val dept2 = Fixtures.department("vr", "Vehicle Repair")
		session.save(dept2)

		val status1 = Fixtures.sitsStatus("P", "PWD", "Permanently Withdrawn")
		session.save(status1)
		val status2 = Fixtures.sitsStatus("P1", "Perm Wd", "Another slightly more esoteric kind of permanently withdrawn")
		session.save(status2)
		val status3 = Fixtures.sitsStatus("F", "Fully Enrolled", "Definitely not permanently withdrawn at all")
		session.save(status3)

		session.flush

		val stu1 = Fixtures.student(universityId = "1000001", userId="student", department=dept1, courseDepartment=dept1, status1)
		memberDao.saveOrUpdate(stu1)
		session.flush

		stu1.permanentlyWithdrawn should be (true)

		val stu2 = Fixtures.student(universityId = "2000001", userId="student", department=dept1, courseDepartment=dept1, status2)
		memberDao.saveOrUpdate(stu2)
		session.flush

		stu2.permanentlyWithdrawn should be (true)

		val stu3 = Fixtures.student(universityId = "3000001", userId="student", department=dept1, courseDepartment=dept1, status3)
		memberDao.saveOrUpdate(stu3)
		session.flush

		stu3.permanentlyWithdrawn should be (false)

		// the student fixture comes with one free studentCourseDetails - add another to stu1:
		val stu1_scd2 = Fixtures.studentCourseDetails(stu1, dept1, null, "1000001/2")
		stu1_scd2.statusOnRoute = status2
		memberDao.saveOrUpdate(stu1)
		studentCourseDetailsDao.saveOrUpdate(stu1_scd2)
		session.flush

		stu1.permanentlyWithdrawn should be (true)

		// add an SCD with a null status - shouldn't come back as permanently withdrawn:
		val stu1_scd3 = Fixtures.studentCourseDetails(stu1, dept1, null, "1000001/3")
		memberDao.saveOrUpdate(stu1)
		studentCourseDetailsDao.saveOrUpdate(stu1_scd3)
		session.flush

		stu1.permanentlyWithdrawn should be (false)

		// and now set the null status to fully enrolled:
		stu1_scd3.statusOnRoute = status3
		studentCourseDetailsDao.saveOrUpdate(stu1_scd3)
		session.flush

		stu1.permanentlyWithdrawn should be (false)
	}

	@Test def freshOrStaleSCYDTest =  {

		val studentMember = Fixtures.student()
		val year = studentMember.mostSignificantCourseDetails.get.latestStudentCourseYearDetails.academicYear
		val studentCourseYearDetails = studentMember.freshOrStaleStudentCourseYearDetails(year).head
		val latestCourseYearDetails =  studentMember.mostSignificantCourseDetails.get.latestStudentCourseYearDetails

		studentCourseYearDetails should be (latestCourseYearDetails)

	}
	
	@Test def casUsedTest =  {

		val studentMember = Fixtures.student()
		studentMember.casUsed.booleanValue() should be (true)

	}

}
