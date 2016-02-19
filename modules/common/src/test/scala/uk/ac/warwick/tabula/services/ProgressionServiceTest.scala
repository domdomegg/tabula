package uk.ac.warwick.tabula.services

import org.mockito.Matchers
import uk.ac.warwick.tabula.commands.exams.grids.GenerateExamGridEntity
import uk.ac.warwick.tabula.data.model.{CourseYearWeighting, CoreRequiredModule, Module, ModuleRegistration}
import uk.ac.warwick.tabula.data.{CourseDao, CourseDaoComponent}
import uk.ac.warwick.tabula.{AcademicYear, Fixtures, Mockito, TestBase}

class ProgressionServiceTest extends TestBase with Mockito {

	val module1 = Fixtures.module("its01")
	val module2 = Fixtures.module("its02")
	val academicYear = AcademicYear(2014)
	val course = Fixtures.course("its1")

	trait Fixture {
		val student = Fixtures.student("1234")
		student.mostSignificantCourse.course = course
		val scyd3 = student.mostSignificantCourse.latestStudentCourseYearDetails
		scyd3.academicYear = academicYear
		student.mostSignificantCourse.courseYearLength = "3"
		val service = new AbstractProgressionService with ModuleRegistrationServiceComponent with CourseDaoComponent {
			override val moduleRegistrationService: ModuleRegistrationService = smartMock[ModuleRegistrationService]
			override val courseDao: CourseDao = smartMock[CourseDao]
		}
	}

	@Test
	def suggestedResultNullModuleMark(): Unit = {
		new Fixture {
			student.mostSignificantCourse.addModuleRegistration(Fixtures.moduleRegistration(
				student.mostSignificantCourse,
				module1,
				BigDecimal(30).underlying,
				academicYear,
				agreedMark = null
			))
			val result = service.suggestedResult(scyd3)
			result.description should be (ProgressionResult.Unknown("").description)
			result.asInstanceOf[ProgressionResult.Unknown].details.contains(module1.code.toUpperCase) should be {true}
		}
	}

	@Test
	def suggestedResultNoModuleRegistrations(): Unit = {
		new Fixture {
			val result = service.suggestedResult(scyd3)
			result.description should be (ProgressionResult.Unknown("").description)
			result.asInstanceOf[ProgressionResult.Unknown].details.contains("No module registrations found") should be {true}
		}
	}

	@Test
	def suggestedResultFirstYear(): Unit = {
		// Not satisfied overall mark
		new Fixture {
			scyd3.yearOfStudy = 1
			service.moduleRegistrationService.findCoreRequiredModules(null, academicYear, 1) returns Seq()
			service.moduleRegistrationService.weightedMeanYearMark(any[Seq[ModuleRegistration]], any[Map[Module, BigDecimal]]) returns Option(BigDecimal(30))

			student.mostSignificantCourse.addModuleRegistration(Fixtures.moduleRegistration(
				student.mostSignificantCourse,
				module1,
				BigDecimal(30).underlying,
				academicYear,
				agreedMark = BigDecimal(100)
			))

			val result = service.suggestedResult(scyd3)
			result.description should be (ProgressionResult.Resit.description)
		}

		// Not passed core required
		new Fixture {
			scyd3.yearOfStudy = 1
			service.moduleRegistrationService.findCoreRequiredModules(null, academicYear, 1) returns Seq(new CoreRequiredModule(null, null, 1, module1))
			service.moduleRegistrationService.weightedMeanYearMark(any[Seq[ModuleRegistration]], any[Map[Module, BigDecimal]]) returns Option(BigDecimal(90))

			student.mostSignificantCourse.addModuleRegistration(Fixtures.moduleRegistration(
				student.mostSignificantCourse,
				module1,
				BigDecimal(30).underlying,
				academicYear,
				agreedMark = BigDecimal(30)
			))

			val result = service.suggestedResult(scyd3)
			result.description should be (ProgressionResult.Resit.description)
		}

		// Not passed enough credits
		new Fixture {
			scyd3.yearOfStudy = 1
			service.moduleRegistrationService.findCoreRequiredModules(null, academicYear, 1) returns Seq(new CoreRequiredModule(null, null, 1, module1))
			service.moduleRegistrationService.weightedMeanYearMark(any[Seq[ModuleRegistration]], any[Map[Module, BigDecimal]]) returns Option(BigDecimal(90))

			student.mostSignificantCourse.addModuleRegistration(Fixtures.moduleRegistration(
				student.mostSignificantCourse,
				module1,
				BigDecimal(30).underlying,
				academicYear,
				agreedMark = BigDecimal(90)
			))

			val result = service.suggestedResult(scyd3)
			result.description should be (ProgressionResult.PossiblyProceed.description)
		}

		// All good
		new Fixture {
			scyd3.yearOfStudy = 1
			service.moduleRegistrationService.findCoreRequiredModules(null, academicYear, 1) returns Seq(new CoreRequiredModule(null, null, 1, module1))
			service.moduleRegistrationService.weightedMeanYearMark(any[Seq[ModuleRegistration]], any[Map[Module, BigDecimal]]) returns Option(BigDecimal(90))

			student.mostSignificantCourse.addModuleRegistration(Fixtures.moduleRegistration(
				student.mostSignificantCourse,
				module1,
				BigDecimal(60).underlying,
				academicYear,
				agreedMark = BigDecimal(90)
			))
			student.mostSignificantCourse.addModuleRegistration(Fixtures.moduleRegistration(
				student.mostSignificantCourse,
				module1,
				BigDecimal(30).underlying,
				academicYear,
				agreedMark = BigDecimal(40)
			))

			val result = service.suggestedResult(scyd3)
			result.description should be (ProgressionResult.Proceed.description)
		}
	}

	@Test
	def suggestedResultIntermediateYear(): Unit = {
		// Not satisfied overall mark
		new Fixture {
			scyd3.yearOfStudy = 2
			service.moduleRegistrationService.findCoreRequiredModules(null, academicYear, 2) returns Seq()
			service.moduleRegistrationService.weightedMeanYearMark(any[Seq[ModuleRegistration]], any[Map[Module, BigDecimal]]) returns Option(BigDecimal(30))

			student.mostSignificantCourse.addModuleRegistration(Fixtures.moduleRegistration(
				student.mostSignificantCourse,
				module1,
				BigDecimal(30).underlying,
				academicYear,
				agreedMark = BigDecimal(100)
			))

			val result = service.suggestedResult(scyd3)
			result.description should be (ProgressionResult.Resit.description)
		}

		// Not passed enough credits
		new Fixture {
			scyd3.yearOfStudy = 2
			service.moduleRegistrationService.findCoreRequiredModules(null, academicYear, 2) returns Seq(new CoreRequiredModule(null, null, 2, module1))
			service.moduleRegistrationService.weightedMeanYearMark(any[Seq[ModuleRegistration]], any[Map[Module, BigDecimal]]) returns Option(BigDecimal(90))

			student.mostSignificantCourse.addModuleRegistration(Fixtures.moduleRegistration(
				student.mostSignificantCourse,
				module1,
				BigDecimal(30).underlying,
				academicYear,
				agreedMark = BigDecimal(90)
			))

			val result = service.suggestedResult(scyd3)
			result.description should be (ProgressionResult.Resit.description)
		}

		// All good
		new Fixture {
			scyd3.yearOfStudy = 2
			service.moduleRegistrationService.findCoreRequiredModules(null, academicYear, 2) returns Seq(new CoreRequiredModule(null, null, 2, module1))
			service.moduleRegistrationService.weightedMeanYearMark(any[Seq[ModuleRegistration]], any[Map[Module, BigDecimal]]) returns Option(BigDecimal(90))

			student.mostSignificantCourse.addModuleRegistration(Fixtures.moduleRegistration(
				student.mostSignificantCourse,
				module1,
				BigDecimal(60).underlying,
				academicYear,
				agreedMark = BigDecimal(90)
			))
			student.mostSignificantCourse.addModuleRegistration(Fixtures.moduleRegistration(
				student.mostSignificantCourse,
				module1,
				BigDecimal(30).underlying,
				academicYear,
				agreedMark = BigDecimal(40)
			))

			val result = service.suggestedResult(scyd3)
			result.description should be (ProgressionResult.Proceed.description)
		}
	}

	@Test
	def suggestedFinalYearGradeNotFinalYear(): Unit = {
		new Fixture {
			scyd3.yearOfStudy = 2
			val result = service.suggestedFinalYearGrade(scyd3)
			result.description should be (FinalYearGrade.Ignore.description)
		}
	}

	trait ThreeYearStudentFixture extends Fixture {
		scyd3.yearOfStudy = 3
		val scyd1 = Fixtures.studentCourseYearDetails()
		scyd1.studentCourseDetails = student.mostSignificantCourse
		scyd1.yearOfStudy = 1
		scyd1.academicYear = academicYear - 2
		student.mostSignificantCourse.addStudentCourseYearDetails(scyd1)
		val scyd2 = Fixtures.studentCourseYearDetails()
		scyd2.studentCourseDetails = student.mostSignificantCourse
		scyd2.yearOfStudy = 2
		scyd2.academicYear = academicYear - 1
		student.mostSignificantCourse.addStudentCourseYearDetails(scyd2)
	}

	@Test
	def suggestedFinalYearGradeMissingYearMark(): Unit = {
		// No marks for any year
		new ThreeYearStudentFixture {
			service.moduleRegistrationService.weightedMeanYearMark(any[Seq[ModuleRegistration]], any[Map[Module, BigDecimal]]) returns None
			val result = service.suggestedFinalYearGrade(scyd3)
			result.description should be (FinalYearGrade.Unknown("").description)
			result.asInstanceOf[FinalYearGrade.Unknown].details.contains("years: 1, 2, 3") should be {true}
		}

		// No mark for 2nd year
		new ThreeYearStudentFixture {
			service.moduleRegistrationService.weightedMeanYearMark(any[Seq[ModuleRegistration]], any[Map[Module, BigDecimal]]) returns Option(BigDecimal(90.0))
			student.mostSignificantCourse.freshStudentCourseYearDetails.head.agreedMark = BigDecimal(90.0).underlying
			val result = service.suggestedFinalYearGrade(scyd3)
			result.description should be (FinalYearGrade.Unknown("").description)
			result.asInstanceOf[FinalYearGrade.Unknown].details.contains("years: 2") should be {true}
		}

		// No mark for this year
		new ThreeYearStudentFixture {
			service.moduleRegistrationService.weightedMeanYearMark(any[Seq[ModuleRegistration]], any[Map[Module, BigDecimal]]) returns None
			student.mostSignificantCourse.freshStudentCourseYearDetails.head.agreedMark = BigDecimal(90.0).underlying
			student.mostSignificantCourse.freshStudentCourseYearDetails.tail.head.agreedMark = BigDecimal(90.0).underlying
			val result = service.suggestedFinalYearGrade(scyd3)
			result.description should be (FinalYearGrade.Unknown("").description)
			result.asInstanceOf[FinalYearGrade.Unknown].details.contains("years: 3") should be {true}
		}
	}

	@Test
	def suggestedFinalYearGradeOvercat(): Unit = {
		trait TestFixture extends ThreeYearStudentFixture {
			service.moduleRegistrationService.weightedMeanYearMark(any[Seq[ModuleRegistration]], any[Map[Module, BigDecimal]]) returns Option(BigDecimal(90.0))
			student.mostSignificantCourse.freshStudentCourseYearDetails.head.agreedMark = BigDecimal(90.0).underlying
			student.mostSignificantCourse.freshStudentCourseYearDetails.tail.head.agreedMark = BigDecimal(90.0).underlying
			service.courseDao.getCourseYearWeighting(Matchers.eq(course.code), Matchers.eq(academicYear), any[Int]) returns None

			val mr1 = Fixtures.moduleRegistration(
				student.mostSignificantCourse,
				module1,
				BigDecimal(60).underlying,
				academicYear,
				agreedMark = BigDecimal(90)
			)
			val mr2 = Fixtures.moduleRegistration(
				student.mostSignificantCourse,
				module2,
				BigDecimal(75).underlying,
				academicYear,
				agreedMark = BigDecimal(40)
			)
			student.mostSignificantCourse.addModuleRegistration(mr1)
			student.mostSignificantCourse.addModuleRegistration(mr2)
			scyd3.moduleAndDepartmentService = smartMock[ModuleAndDepartmentService]
			scyd3.moduleAndDepartmentService.getModuleByCode(module1.code) returns Option(module1)
			scyd3.moduleAndDepartmentService.getModuleByCode(module2.code) returns Option(module2)
		}

		// Not overcat
		new TestFixture {
			scyd3.normalCATLoad = 180
			val result = service.suggestedFinalYearGrade(scyd3)
			result.description should be (FinalYearGrade.Unknown("").description)
			result.asInstanceOf[FinalYearGrade.Unknown].details.contains("Could not find year weightings") should be {true}
		}

		// Overcat, single subset
		new TestFixture {
			service.moduleRegistrationService.overcattedModuleSubsets(any[GenerateExamGridEntity], any[Map[Module, BigDecimal]]) returns Seq(
				(BigDecimal(90.0), Seq(mr1, mr2))
			) // One subset

			val result = service.suggestedFinalYearGrade(scyd3)
			// Should re-use the initally calculated mark as only 1 subset
			verify(service.moduleRegistrationService, times(1)).weightedMeanYearMark(any[Seq[ModuleRegistration]], any[Map[Module, BigDecimal]])
			result.description should be (FinalYearGrade.Unknown("").description)
			result.asInstanceOf[FinalYearGrade.Unknown].details.contains("Could not find year weightings") should be {true}
		}

		// Overcat, 2 subsets, none chosen
		new TestFixture {
			val mr3 = Fixtures.moduleRegistration(
				student.mostSignificantCourse,
				module2,
				BigDecimal(60).underlying,
				academicYear,
				agreedMark = BigDecimal(50)
			)
			student.mostSignificantCourse.addModuleRegistration(mr3)
			service.moduleRegistrationService.overcattedModuleSubsets(any[GenerateExamGridEntity], any[Map[Module, BigDecimal]]) returns Seq(
				(BigDecimal(90.0), Seq(mr1, mr2)),
				(BigDecimal(90.0), Seq(mr1, mr3))
			) // Two subsets

			val result = service.suggestedFinalYearGrade(scyd3)
			result.description should be (FinalYearGrade.Unknown("").description)
			result.asInstanceOf[FinalYearGrade.Unknown].details.contains("years: 3") should be {true}
		}

		// Overcat, 2 subsets, 1 chosen
		new TestFixture {
			val mr3 = Fixtures.moduleRegistration(
				student.mostSignificantCourse,
				module2,
				BigDecimal(60).underlying,
				academicYear,
				agreedMark = BigDecimal(50)
			)
			student.mostSignificantCourse.addModuleRegistration(mr3)
			service.moduleRegistrationService.overcattedModuleSubsets(any[GenerateExamGridEntity], any[Map[Module, BigDecimal]]) returns Seq(
				(BigDecimal(90.0), Seq(mr1, mr2)),
				(BigDecimal(90.0), Seq(mr1, mr3))
			) // Two subsets
			scyd3.overcattingModules = Seq(module1, module2) // Subset chosen

			val result = service.suggestedFinalYearGrade(scyd3)
			result.description should be (FinalYearGrade.Unknown("").description)
			result.asInstanceOf[FinalYearGrade.Unknown].details.contains("Could not find year weightings") should be {true}
		}
	}

	@Test
	def suggestedFinalYearGradeMissingYearWeightings(): Unit = {
		// No weightings for any year
		new ThreeYearStudentFixture {
			service.moduleRegistrationService.weightedMeanYearMark(any[Seq[ModuleRegistration]], any[Map[Module, BigDecimal]]) returns Option(BigDecimal(90.0))
			student.mostSignificantCourse.freshStudentCourseYearDetails.head.agreedMark = BigDecimal(90.0).underlying
			student.mostSignificantCourse.freshStudentCourseYearDetails.tail.head.agreedMark = BigDecimal(90.0).underlying

			service.courseDao.getCourseYearWeighting(course.code, academicYear, 1) returns None
			service.courseDao.getCourseYearWeighting(course.code, academicYear, 2) returns None
			service.courseDao.getCourseYearWeighting(course.code, academicYear, 3) returns None
			val result = service.suggestedFinalYearGrade(scyd3)
			verify(service.courseDao, times(3)).getCourseYearWeighting(Matchers.eq(course.code), Matchers.eq(academicYear), any[Int])
			result.description should be (FinalYearGrade.Unknown("").description)
			result.asInstanceOf[FinalYearGrade.Unknown].details.contains("Could not find year weightings") should be {true}
		}

		// No weighting for 2nd year
		new ThreeYearStudentFixture {
			service.moduleRegistrationService.weightedMeanYearMark(any[Seq[ModuleRegistration]], any[Map[Module, BigDecimal]]) returns Option(BigDecimal(90.0))
			student.mostSignificantCourse.freshStudentCourseYearDetails.head.agreedMark = BigDecimal(90.0).underlying
			student.mostSignificantCourse.freshStudentCourseYearDetails.tail.head.agreedMark = BigDecimal(90.0).underlying

			service.courseDao.getCourseYearWeighting(course.code, academicYear, 1) returns Some(null)
			service.courseDao.getCourseYearWeighting(course.code, academicYear, 2) returns None
			service.courseDao.getCourseYearWeighting(course.code, academicYear, 3) returns Some(null)
			val result = service.suggestedFinalYearGrade(scyd3)
			verify(service.courseDao, times(3)).getCourseYearWeighting(Matchers.eq(course.code), Matchers.eq(academicYear), any[Int])
			result.description should be (FinalYearGrade.Unknown("").description)
			result.asInstanceOf[FinalYearGrade.Unknown].details.contains(s"Could not find year weightings for: ${course.code.toUpperCase} ${academicYear.toString} Year 2") should be {true}
		}
	}
	
	trait ThreeYearStudentWithMarksAndYearWeightingsFixture extends ThreeYearStudentFixture {
		student.mostSignificantCourse.freshStudentCourseYearDetails.head.agreedMark = BigDecimal(65.0).underlying
		student.mostSignificantCourse.freshStudentCourseYearDetails.tail.head.agreedMark = BigDecimal(60.0).underlying
		service.moduleRegistrationService.weightedMeanYearMark(any[Seq[ModuleRegistration]], any[Map[Module, BigDecimal]]) returns Option(BigDecimal(80.0))

		val year1Weighting = new CourseYearWeighting
		year1Weighting.weighting = BigDecimal(0.2).underlying
		val year2Weighting = new CourseYearWeighting
		year2Weighting.weighting = BigDecimal(0.4).underlying
		val year3Weighting = new CourseYearWeighting
		year3Weighting.weighting = BigDecimal(0.4).underlying
		service.courseDao.getCourseYearWeighting(course.code, academicYear, 1) returns Option(year1Weighting)
		service.courseDao.getCourseYearWeighting(course.code, academicYear, 2) returns Option(year2Weighting)
		service.courseDao.getCourseYearWeighting(course.code, academicYear, 3) returns Option(year3Weighting)
	}

	@Test
	def suggestedFinalYearGrade(): Unit = {
		// Missing module mark in 2nd year
		new ThreeYearStudentWithMarksAndYearWeightingsFixture {
			val mr1 = Fixtures.moduleRegistration(
				student.mostSignificantCourse,
				module1,
				BigDecimal(60).underlying,
				scyd2.academicYear,
				agreedMark = null
			)
			val mr2 = Fixtures.moduleRegistration(
				student.mostSignificantCourse,
				module2,
				BigDecimal(60).underlying,
				scyd2.academicYear,
				agreedMark = BigDecimal(90)
			)
			student.mostSignificantCourse.addModuleRegistration(mr1)
			student.mostSignificantCourse.addModuleRegistration(mr2)
			val result = service.suggestedFinalYearGrade(scyd3)
			result.description should be (FinalYearGrade.Unknown("").description)
			result.asInstanceOf[FinalYearGrade.Unknown].details.contains(module1.code.toUpperCase) should be {true}
		}

		// Not enough credits passed in 2nd year
		new ThreeYearStudentWithMarksAndYearWeightingsFixture {
			val mr1 = Fixtures.moduleRegistration(
				student.mostSignificantCourse,
				module1,
				BigDecimal(40).underlying,
				scyd2.academicYear,
				agreedMark = BigDecimal(90)
			)
			val mr2 = Fixtures.moduleRegistration(
				student.mostSignificantCourse,
				module2,
				BigDecimal(40).underlying,
				scyd2.academicYear,
				agreedMark = BigDecimal(35) // Failed in 2nd year
			)
			val mr3 = Fixtures.moduleRegistration(
				student.mostSignificantCourse,
				module1,
				BigDecimal(40).underlying,
				scyd3.academicYear,
				agreedMark = BigDecimal(90)
			)
			val mr4 = Fixtures.moduleRegistration(
				student.mostSignificantCourse,
				module2,
				BigDecimal(60).underlying,
				scyd3.academicYear,
				agreedMark = BigDecimal(90)
			)
			student.mostSignificantCourse.addModuleRegistration(mr1)
			student.mostSignificantCourse.addModuleRegistration(mr2)
			student.mostSignificantCourse.addModuleRegistration(mr3)
			student.mostSignificantCourse.addModuleRegistration(mr4)
			val result = service.suggestedFinalYearGrade(scyd3)
			result.description should be (FinalYearGrade.Fail.description)
		}

		// Not enough credits passed in final year
		new ThreeYearStudentWithMarksAndYearWeightingsFixture {
			val mr1 = Fixtures.moduleRegistration(
				student.mostSignificantCourse,
				module1,
				BigDecimal(40).underlying,
				scyd2.academicYear,
				agreedMark = BigDecimal(90)
			)
			val mr2 = Fixtures.moduleRegistration(
				student.mostSignificantCourse,
				module2,
				BigDecimal(40).underlying,
				scyd2.academicYear,
				agreedMark = BigDecimal(90)
			)
			val mr3 = Fixtures.moduleRegistration(
				student.mostSignificantCourse,
				module1,
				BigDecimal(40).underlying,
				scyd3.academicYear,
				agreedMark = BigDecimal(90)
			)
			val mr4 = Fixtures.moduleRegistration(
				student.mostSignificantCourse,
				module2,
				BigDecimal(60).underlying,
				scyd3.academicYear,
				agreedMark = BigDecimal(35) // Failed in final year
			)
			student.mostSignificantCourse.addModuleRegistration(mr1)
			student.mostSignificantCourse.addModuleRegistration(mr2)
			student.mostSignificantCourse.addModuleRegistration(mr3)
			student.mostSignificantCourse.addModuleRegistration(mr4)
			val result = service.suggestedFinalYearGrade(scyd3)
			result.description should be (FinalYearGrade.Fail.description)
		}

		// All good
		new ThreeYearStudentWithMarksAndYearWeightingsFixture {
			val mr1 = Fixtures.moduleRegistration(
				student.mostSignificantCourse,
				module1,
				BigDecimal(40).underlying,
				scyd2.academicYear,
				agreedMark = BigDecimal(90)
			)
			val mr2 = Fixtures.moduleRegistration(
				student.mostSignificantCourse,
				module2,
				BigDecimal(40).underlying,
				scyd2.academicYear,
				agreedMark = BigDecimal(90)
			)
			val mr3 = Fixtures.moduleRegistration(
				student.mostSignificantCourse,
				module1,
				BigDecimal(40).underlying,
				scyd3.academicYear,
				agreedMark = BigDecimal(90)
			)
			val mr4 = Fixtures.moduleRegistration(
				student.mostSignificantCourse,
				module2,
				BigDecimal(60).underlying,
				scyd3.academicYear,
				agreedMark = BigDecimal(90)
			)
			student.mostSignificantCourse.addModuleRegistration(mr1)
			student.mostSignificantCourse.addModuleRegistration(mr2)
			student.mostSignificantCourse.addModuleRegistration(mr3)
			student.mostSignificantCourse.addModuleRegistration(mr4)
			val result = service.suggestedFinalYearGrade(scyd3)
			// 1st year: 65 @ 20%, 2nd year: 60 @ 40%, 3rd year 80 @ 40%
			// Final grade 69 = 2.1
			result.description should be (FinalYearGrade.UpperSecond.description)
		}
	}

}
