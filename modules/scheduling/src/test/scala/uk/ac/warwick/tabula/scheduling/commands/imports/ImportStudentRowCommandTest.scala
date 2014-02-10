package uk.ac.warwick.tabula.scheduling.commands.imports

import java.sql.{Date, ResultSet, ResultSetMetaData}

import org.joda.time.{DateTime, DateTimeConstants, LocalDate}
import org.springframework.beans.BeanWrapperImpl
import org.springframework.transaction.annotation.Transactional

import uk.ac.warwick.tabula.{Mockito, TestBase}
import uk.ac.warwick.tabula.data.{FileDao, MemberDao, ModeOfAttendanceDao, SitsStatusDao, StudentCourseDetailsDao, StudentCourseYearDetailsDao}
import uk.ac.warwick.tabula.data.model._
import uk.ac.warwick.tabula.data.model.Gender.Male
import uk.ac.warwick.tabula.data.model.MemberUserType.Student
import uk.ac.warwick.tabula.events.EventHandling
import uk.ac.warwick.tabula.helpers.Logging
import uk.ac.warwick.tabula.scheduling.helpers.ImportRowTracker
import uk.ac.warwick.tabula.scheduling.services._
import uk.ac.warwick.tabula.services.{CourseAndRouteService, MaintenanceModeService, ModuleAndDepartmentService, ProfileService, ProfileServiceComponent, RelationshipService}
import uk.ac.warwick.userlookup.AnonymousUser
import uk.ac.warwick.tabula.scheduling.services.MembershipMember
import uk.ac.warwick.tabula.scheduling.services.MembershipInformation

// scalastyle:off magic.number
class ImportStudentRowCommandTest extends TestBase with Mockito with Logging {
	EventHandling.enabled = false

	trait ComponentMixins extends ProfileServiceComponent with Tier4RequirementImporterComponent	with ModeOfAttendanceImporterComponent {
		var profileService = smartMock[ProfileService]
		var tier4RequirementImporter = smartMock[Tier4RequirementImporter]
		var modeOfAttendanceImporter = smartMock[ModeOfAttendanceImporter]
	}

	trait Environment extends ComponentMixins {

		val memberDao = smartMock[MemberDao]
		val moaDao = smartMock[ModeOfAttendanceDao]
		val sitsStatusDao = smartMock[SitsStatusDao]
		val fileDao = smartMock[FileDao]
		val studentCourseYearDetailsDao = smartMock[StudentCourseYearDetailsDao]
		val studentCourseDetailsDao = smartMock[StudentCourseDetailsDao]

		val relationshipService = smartMock[RelationshipService]
		relationshipService.getStudentRelationshipTypeByUrlPart("tutor") returns (None)

		val disabilityQ = new Disability("Q", "Test disability")

		val department = new Department
		department.code = "ph"
		department.name = "Philosophy"

		val modAndDeptService = smartMock[ModuleAndDepartmentService]
		modAndDeptService.getDepartmentByCode("ph") returns (Some(department))

		var maintenanceModeService = smartMock[MaintenanceModeService]
		maintenanceModeService.enabled returns false

		val courseAndRouteService = smartMock[CourseAndRouteService]
		val route = smartMock[Route]
		courseAndRouteService.getRouteByCode("c100") returns (Some(route))

		modeOfAttendanceImporter.modeOfAttendanceMap returns Map(
					"F" -> new ModeOfAttendance("F", "FT", "Full Time"),
					"P" -> new ModeOfAttendance("P", "PT", "Part Time")
			)

		modeOfAttendanceImporter.getModeOfAttendanceForCode("P") returns Some(new ModeOfAttendance("P", "PT", "Part Time"))

		val sitsStatusImporter = smartMock[SitsStatusImporter]
		sitsStatusImporter.getSitsStatusForCode("F") returns  Some(new SitsStatus("F", "F", "Fully Enrolled"))
		sitsStatusImporter.getSitsStatusForCode("P") returns  Some(new SitsStatus("P", "P", "Permanently Withdrawn"))


		val courseImporter = smartMock[CourseImporter]
		courseImporter.getCourseForCode("UESA-H612") returns new Course("UESA-H612", "Computer Systems Engineering MEng")

		//department.personalTutorSource = Department.Settings.PersonalTutorSourceValues.Sits

		val rs = smartMock[ResultSet]
		val rsMetaData = smartMock[ResultSetMetaData]
		rs.getMetaData() returns(rsMetaData)

		rsMetaData.getColumnCount() returns(4)
		rsMetaData.getColumnName(1) returns("gender")
		rsMetaData.getColumnName(2) returns("year_of_study")
		rsMetaData.getColumnName(3) returns("spr_code")
		rsMetaData.getColumnName(4) returns("route_code")

		rs.getString("gender") returns("M")
		rs.getInt("year_of_study") returns(3)
		rs.getString("spr_code") returns("0672089/2")
		rs.getString("route_code") returns("C100")
		rs.getString("spr_tutor1") returns ("0070790")
		rs.getString("homeDepartmentCode") returns ("PH")
		rs.getString("department_code") returns ("PH")
		rs.getString("scj_code") returns ("0672089/2")
		rs.getDate("begin_date") returns Date.valueOf("2011-05-12")
		rs.getDate("end_date") returns Date.valueOf("2014-05-12")
		rs.getDate("expected_end_date") returns Date.valueOf("2015-05-12")
		rs.getInt("sce_sequence_number") returns (1)
		rs.getString("enrolment_status_code") returns ("F")
		rs.getString("mode_of_attendance_code") returns ("P")
		rs.getString("sce_academic_year") returns ("10/11")
		rs.getString("most_signif_indicator") returns ("Y")
		rs.getString("mod_reg_status") returns ("CON")
		rs.getString("course_code") returns ("UESA-H612")
		rs.getString("disability") returns ("Q")

		val mm = MembershipMember(
			universityId 			= "0672089",
			departmentCode			= "ph",
			email					= "M.Mannion@warwick.ac.uk",
			targetGroup				= null,
			title					= "Mr",
			preferredForenames		= "Mathew",
			preferredSurname		= "Mannion",
			position				= null,
			dateOfBirth				= new LocalDate(1984, DateTimeConstants.AUGUST, 19),
			usercode				= "cuscav",
			startDate				= null,
			endDate					= null,
			modified				= null,
			phoneNumber				= null,
			gender					= null,
			alternativeEmailAddress	= null,
			userType				= Student)

		val blobBytes = Array[Byte](1,2,3,4,5)
		val mac = MembershipInformation(mm, () => Some(blobBytes))
		val importRowTracker = new ImportRowTracker

		val yearCommand = new ImportStudentCourseYearCommand(rs, importRowTracker)
		yearCommand.modeOfAttendanceImporter = modeOfAttendanceImporter
		yearCommand.profileService = profileService
		yearCommand.sitsStatusImporter = sitsStatusImporter
		yearCommand.maintenanceMode = maintenanceModeService
		yearCommand.studentCourseYearDetailsDao = studentCourseYearDetailsDao

		val supervisorCommand = new ImportSupervisorsForStudentCommand
		supervisorCommand.maintenanceMode = maintenanceModeService

		val courseCommand = new ImportStudentCourseCommand(rs, importRowTracker, yearCommand, supervisorCommand)
		courseCommand.studentCourseDetailsDao = studentCourseDetailsDao
		courseCommand.sitsStatusImporter = sitsStatusImporter
		courseCommand.courseAndRouteService = courseAndRouteService
		courseCommand.maintenanceMode = maintenanceModeService
		courseCommand.moduleAndDepartmentService = modAndDeptService
		courseCommand.memberDao = memberDao
		courseCommand.relationshipService = relationshipService
		courseCommand.courseImporter = courseImporter
		courseCommand.stuMem = smartMock[StudentMember]

		val rowCommand = new ImportStudentRowCommandInternal(mac, new AnonymousUser(), rs, new ImportRowTracker, courseCommand)
			with ComponentMixins
		rowCommand.memberDao = memberDao
		rowCommand.fileDao = fileDao
		rowCommand.moduleAndDepartmentService = modAndDeptService

		// only return a known disability for code Q
		profileService.getDisability(any[String]) returns (None)
		profileService.getDisability("Q") returns (Some(disabilityQ))
		rowCommand.profileService = profileService

		tier4RequirementImporter.hasTier4Requirement("0672089") returns (false)
		rowCommand.tier4RequirementImporter = tier4RequirementImporter


	}

	@Test def testImportStudentCourseYearCommand {
		new Environment {
			val studentCourseDetails = new StudentCourseDetails
			studentCourseDetails.scjCode = "0672089/2"
			studentCourseDetails.sprCode = "0672089/2"
			yearCommand.studentCourseDetails = studentCourseDetails

			// now the set up is done, run the apply command and test it:
			val studentCourseYearDetails = yearCommand.applyInternal()

			// and check stuff:
			studentCourseYearDetails.academicYear.toString should be ("10/11")
			studentCourseYearDetails.sceSequenceNumber should be (1)
			studentCourseYearDetails.enrolmentStatus.code should be ("F")
			studentCourseYearDetails.lastUpdatedDate should not be null
			studentCourseYearDetails.modeOfAttendance.code should be ("P")
			studentCourseYearDetails.yearOfStudy should be (3)

			there was one(studentCourseYearDetailsDao).saveOrUpdate(any[StudentCourseYearDetails]);
		}
	}

	@Test def testImportStudentCourseCommand {
		new Environment {
			// first set up the studentCourseYearDetails as above
			var studentCourseDetails = new StudentCourseDetails
			studentCourseDetails.scjCode = "0672089/2"
			studentCourseDetails.sprCode = "0672089/2"
			yearCommand.studentCourseDetails = studentCourseDetails

			relationshipService.getStudentRelationshipTypeByUrlPart("tutor") returns (None)

			val studentCourseYearDetails = yearCommand.applyInternal()

			// now the set up is done, run the apply command and test it:
			studentCourseDetails = courseCommand.applyInternal()

			// now test some stuff
			studentCourseDetails.scjCode should be ("0672089/2")
			studentCourseDetails.beginDate.toString should be ("2011-05-12")
			studentCourseDetails.endDate.toString should be ("2014-05-12")
			studentCourseDetails.expectedEndDate.toString should be ("2015-05-12")

			studentCourseDetails.freshStudentCourseYearDetails.size should be (1)

			there was one(studentCourseDetailsDao).saveOrUpdate(any[StudentCourseDetails]);
		}
	}

	@Test def testMarkAsSeenInSits {
		new Environment {

			// first set up the studentCourseYearDetails as above
			var studentCourseDetails = new StudentCourseDetails
			studentCourseDetails.scjCode = "0672089/2"
			studentCourseDetails.sprCode = "0672089/2"

			val studentCourseDetailsBean = new BeanWrapperImpl(studentCourseDetails)

			studentCourseDetails.missingFromImportSince should be (null)

			courseCommand.markAsSeenInSits(studentCourseDetailsBean) should be (false)

			studentCourseDetails.missingFromImportSince should be (null)

			studentCourseDetails.missingFromImportSince = DateTime.now

			studentCourseDetails.missingFromImportSince should not be (null)

			courseCommand.markAsSeenInSits(studentCourseDetailsBean) should be (true)

			studentCourseDetails.missingFromImportSince should be (null)

		}
	}


	@Test
	def testImportStudentRowCommandWorksWithNew {
		new Environment {
			relationshipService.getStudentRelationshipTypeByUrlPart("tutor") returns (None)

			// now the set-up is done, run the apply command for member, which should cascade and run the other apply commands:
			val member = rowCommand.applyInternal()

			// test that member contains the expected data:
			member.title should be ("Mr")
			member.universityId should be ("0672089")
			member.userId should be ("cuscav")
			member.email should be ("M.Mannion@warwick.ac.uk")
			member.gender should be (Male)
			member.firstName should be ("Mathew")
			member.lastName should be ("Mannion")
			member.photo should not be (null)
			member.dateOfBirth should be (new LocalDate(1984, DateTimeConstants.AUGUST, 19))

			member match {
				case stu: StudentMember => {
					stu.freshStudentCourseDetails.size should be (1)
					stu.freshStudentCourseDetails.head.freshStudentCourseYearDetails.size should be (1)
				}
				case _ => false should be (true)
			}

			there was one(fileDao).savePermanent(any[FileAttachment])
			there was no(fileDao).saveTemporary(any[FileAttachment])
			there was one(memberDao).saveOrUpdate(any[Member])
		}
	}

	@Test
	def worksWithExistingMember {
		new Environment {
			val existing = new StudentMember("0672089")
			memberDao.getByUniversityId("0672089") returns(Some(existing))

			relationshipService.getStudentRelationshipTypeByUrlPart("tutor") returns (None)

			// now the set-up is done, run the apply command for member, which should cascade and run the other apply commands:
			val member = rowCommand.applyInternal()
			member match {
				case stu: StudentMember => {
					stu.freshStudentCourseDetails.size should be (1)
					stu.freshStudentCourseDetails.head.freshStudentCourseYearDetails.size should be (1)
				}
				case _ => false should be (true)
			}

			there was one(fileDao).savePermanent(any[FileAttachment])
			there was no(fileDao).saveTemporary(any[FileAttachment])
			there was one(memberDao).saveOrUpdate(any[Member])
		}
	}

	@Transactional
	@Test def testCaptureTutorIfSourceIsLocal {

		new Environment {
			val existing = new StudentMember("0672089")
			val existingStaffMember = new StaffMember("0070790")

			memberDao.getByUniversityId("0070790") returns(Some(existingStaffMember))
			memberDao.getByUniversityId("0672089") returns(Some(existing))

			val tutorRelationshipType = StudentRelationshipType("tutor", "tutor", "personal tutor", "personal tutee")

			relationshipService.getStudentRelationshipTypeByUrlPart("tutor") returns (Some(tutorRelationshipType))

			// if personalTutorSource is "local", there should be no update
			department.setStudentRelationshipSource(tutorRelationshipType, StudentRelationshipSource.Local)

			val member = rowCommand.applyInternal() match {
				case stu: StudentMember => Some(stu)
				case _ => None
			}

			val studentMember = member.get

			studentMember.freshStudentCourseDetails.size should not be (0)

			there was no(relationshipService).replaceStudentRelationships(tutorRelationshipType, studentMember.mostSignificantCourseDetails.get, Seq(existingStaffMember))
		}
	}

	@Transactional
	@Test def testCaptureTutorIfSourceIsSits {

		new Environment {
			val existing = new StudentMember("0672089")
			val existingStaffMember = new StaffMember("0070790")

			val tutorRelationshipType = StudentRelationshipType("tutor", "tutor", "personal tutor", "personal tutee")

			relationshipService.getStudentRelationshipTypeByUrlPart("tutor") returns (Some(tutorRelationshipType))
			courseCommand.relationshipService = relationshipService
			rowCommand.importStudentCourseCommand = courseCommand

			// if personalTutorSource is "SITS", there *should* an update
			department.setStudentRelationshipSource(tutorRelationshipType, StudentRelationshipSource.SITS)

			memberDao.getByUniversityId("0070790") returns(Some(existingStaffMember))
			memberDao.getByUniversityId("0672089") returns(Some(existing))
			
			relationshipService.findCurrentRelationships(tutorRelationshipType, existing) returns (Nil)

			val member = rowCommand.applyInternal() match {
				case stu: StudentMember => Some(stu)
				case _ => None
			}
			
			val studentMember = member.get

			studentMember.mostSignificantCourseDetails should not be (null)

			there was one(relationshipService).replaceStudentRelationships(tutorRelationshipType, studentMember.mostSignificantCourseDetails.get, Seq(existingStaffMember))
		}
	}

	@Test def testDisabilityHandling {
		new Environment {
			var student = rowCommand.applyInternal().asInstanceOf[StudentMember]
			student.disability should be (disabilityQ)

			// override to test for attempted import of unknown disability
			rs.getString("disability") returns ("Mystery")
			student = rowCommand.applyInternal().asInstanceOf[StudentMember]
			student.disability should be (null)
		}
	}
}

