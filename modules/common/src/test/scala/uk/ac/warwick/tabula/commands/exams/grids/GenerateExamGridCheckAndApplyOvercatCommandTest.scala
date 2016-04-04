package uk.ac.warwick.tabula.commands.exams.grids

import uk.ac.warwick.tabula.commands.Appliable
import uk.ac.warwick.tabula.data.{StudentCourseYearDetailsDao, StudentCourseYearDetailsDaoComponent}
import uk.ac.warwick.tabula.data.model.Department
import uk.ac.warwick.tabula.services._
import uk.ac.warwick.tabula._

class GenerateExamGridCheckAndApplyOvercatCommandTest extends TestBase with Mockito {

	val thisDepartment = Fixtures.department("its")
	val thisAcademicYear = AcademicYear(2014)
	val thisRoute = Fixtures.route("a100")
	val thisYearOfStudy = 3
	val module1 = Fixtures.module("its01")
	val module2 = Fixtures.module("its02")
	val mads = smartMock[ModuleAndDepartmentService]
	mads.getModuleByCode(module1.code) returns Some(module1)
	mads.getModuleByCode(module2.code) returns Some(module2)
	val scd = Fixtures.student("1234").mostSignificantCourse
	val scyd = scd.latestStudentCourseYearDetails
	scyd.moduleAndDepartmentService = mads
	val mr1 = Fixtures.moduleRegistration(scd, module1, null, null)
	val mr2 = Fixtures.moduleRegistration(scd, module2, null, null)

	trait StateFixture {
		val state = new GenerateExamGridCheckAndApplyOvercatCommandState with UpstreamRouteRuleServiceComponent
			with ModuleRegistrationServiceComponent {
			override val department = thisDepartment
			override val academicYear = thisAcademicYear
			override val upstreamRouteRuleService = smartMock[UpstreamRouteRuleService]
			override val moduleRegistrationService = smartMock[ModuleRegistrationService]
		}
		// Just use the default normal load
		state.upstreamRouteRuleService.findNormalLoad(thisRoute, thisAcademicYear, thisYearOfStudy) returns None
		state.upstreamRouteRuleService.list(thisRoute, thisAcademicYear, thisYearOfStudy) returns Seq()
	}

	@Test
	def stateFilterNotOvercat(): Unit = { new StateFixture {
		val entity = GenerateExamGridEntity(null, null, null, null, ModuleRegistrationService.DefaultNormalLoad, null, null, null)
		val selectCourseCommand = new Appliable[Seq[GenerateExamGridEntity]] with GenerateExamGridSelectCourseCommandRequest {
			override def apply(): Seq[GenerateExamGridEntity] = Seq(entity)
		}
		state.selectCourseCommand = selectCourseCommand
		state.filteredEntities.isEmpty should be {true}
	}}

	@Test
	def stateFilterNoValidSubset(): Unit = { new StateFixture {
		val entity = GenerateExamGridEntity(null, null, null, null, ModuleRegistrationService.DefaultNormalLoad + 15, null, None, null)
		val selectCourseCommand = new Appliable[Seq[GenerateExamGridEntity]] with GenerateExamGridSelectCourseCommandRequest {
			override def apply(): Seq[GenerateExamGridEntity] = Seq(entity)
			route = thisRoute
			yearOfStudy = thisYearOfStudy
		}
		state.selectCourseCommand = selectCourseCommand
		state.moduleRegistrationService.overcattedModuleSubsets(entity, Map(), ModuleRegistrationService.DefaultNormalLoad, Seq()) returns Seq()
		state.filteredEntities.isEmpty should be {true}
		verify(state.moduleRegistrationService, times(1)).overcattedModuleSubsets(entity, Map(), ModuleRegistrationService.DefaultNormalLoad, Seq())
	}}

	@Test
	def stateFilterNoOvercatSelection(): Unit = { new StateFixture {
		val entity = GenerateExamGridEntity(null, null, null, null, ModuleRegistrationService.DefaultNormalLoad + 15, None, None, null)
		val selectCourseCommand = new Appliable[Seq[GenerateExamGridEntity]] with GenerateExamGridSelectCourseCommandRequest {
			override def apply(): Seq[GenerateExamGridEntity] = Seq(entity)
			route = thisRoute
			yearOfStudy = thisYearOfStudy
		}
		state.selectCourseCommand = selectCourseCommand
		state.moduleRegistrationService.overcattedModuleSubsets(entity, Map(), ModuleRegistrationService.DefaultNormalLoad, Seq()) returns Seq(null)
		state.filteredEntities.isEmpty should be {false}
		state.filteredEntities.head._1 should be (entity)
		verify(state.moduleRegistrationService, times(1)).overcattedModuleSubsets(entity, Map(), ModuleRegistrationService.DefaultNormalLoad, Seq())
	}}

	@Test
	def stateFilterSameModule(): Unit = { new StateFixture {
		val entity = GenerateExamGridEntity(null, null, null, null, ModuleRegistrationService.DefaultNormalLoad + 15, Some(Seq(module1)), None, null)

		val selectCourseCommand = new Appliable[Seq[GenerateExamGridEntity]] with GenerateExamGridSelectCourseCommandRequest {
			override def apply(): Seq[GenerateExamGridEntity] = Seq(entity)
			route = thisRoute
			yearOfStudy = thisYearOfStudy
		}
		state.selectCourseCommand = selectCourseCommand

		state.moduleRegistrationService.overcattedModuleSubsets(entity, Map(), ModuleRegistrationService.DefaultNormalLoad, Seq()) returns Seq((BigDecimal(0), Seq(mr1)))

		state.filteredEntities.isEmpty should be {true}
		verify(state.moduleRegistrationService, times(1)).overcattedModuleSubsets(entity, Map(), ModuleRegistrationService.DefaultNormalLoad, Seq())
	}}

	@Test
	def stateFilterDifferentModule(): Unit = { new StateFixture {
		val entity = GenerateExamGridEntity(null, null, null, null, ModuleRegistrationService.DefaultNormalLoad + 15, Some(Seq(module2)), None, null)

		val selectCourseCommand = new Appliable[Seq[GenerateExamGridEntity]] with GenerateExamGridSelectCourseCommandRequest {
			override def apply(): Seq[GenerateExamGridEntity] = Seq(entity)
			route = thisRoute
			yearOfStudy = thisYearOfStudy
		}
		state.selectCourseCommand = selectCourseCommand

		state.moduleRegistrationService.overcattedModuleSubsets(entity, Map(), ModuleRegistrationService.DefaultNormalLoad, Seq()) returns Seq((BigDecimal(0), Seq(mr1)))

		state.filteredEntities.isEmpty should be {false}
		state.filteredEntities.head._1 should be (entity)
		state.filteredEntities.head._2 should be (Seq((BigDecimal(0), Seq(mr1))))
		verify(state.moduleRegistrationService, times(1)).overcattedModuleSubsets(entity, Map(), ModuleRegistrationService.DefaultNormalLoad, Seq())
	}}

	@Test
	def stateFilterExtraModule(): Unit = { new StateFixture {
		val entity = GenerateExamGridEntity(null, null, null, null, ModuleRegistrationService.DefaultNormalLoad + 15, Some(Seq(module1)), None, null)

		val selectCourseCommand = new Appliable[Seq[GenerateExamGridEntity]] with GenerateExamGridSelectCourseCommandRequest {
			override def apply(): Seq[GenerateExamGridEntity] = Seq(entity)
			route = thisRoute
			yearOfStudy = thisYearOfStudy
		}
		state.selectCourseCommand = selectCourseCommand

		state.moduleRegistrationService.overcattedModuleSubsets(entity, Map(), ModuleRegistrationService.DefaultNormalLoad, Seq()) returns Seq((BigDecimal(0), Seq(mr1, mr2)))

		state.filteredEntities.isEmpty should be {false}
		state.filteredEntities.head._1 should be (entity)
		state.filteredEntities.head._2 should be (Seq((BigDecimal(0), Seq(mr1, mr2))))
		verify(state.moduleRegistrationService, times(1)).overcattedModuleSubsets(entity, Map(), ModuleRegistrationService.DefaultNormalLoad, Seq())
	}}

	@Test
	def stateFilterRemovedModule(): Unit = { new StateFixture {
		val entity = GenerateExamGridEntity(null, null, null, null, ModuleRegistrationService.DefaultNormalLoad + 15, Some(Seq(module1, module2)), None, null)

		val selectCourseCommand = new Appliable[Seq[GenerateExamGridEntity]] with GenerateExamGridSelectCourseCommandRequest {
			override def apply(): Seq[GenerateExamGridEntity] = Seq(entity)
			route = thisRoute
			yearOfStudy = thisYearOfStudy
		}
		state.selectCourseCommand = selectCourseCommand

		state.moduleRegistrationService.overcattedModuleSubsets(entity, Map(), ModuleRegistrationService.DefaultNormalLoad, Seq()) returns Seq((BigDecimal(0), Seq(mr2)))

		state.filteredEntities.isEmpty should be {false}
		state.filteredEntities.head._1 should be (entity)
		state.filteredEntities.head._2 should be (Seq((BigDecimal(0), Seq(mr2))))
		verify(state.moduleRegistrationService, times(1)).overcattedModuleSubsets(entity, Map(), ModuleRegistrationService.DefaultNormalLoad, Seq())
	}}

	@Test
	def apply(): Unit = {
		val entity = GenerateExamGridEntity(null, null, null, null, ModuleRegistrationService.DefaultNormalLoad + 15, None, None, Option(scyd))

		var fetchCount = 0

		val selectCourseCommand = new Appliable[Seq[GenerateExamGridEntity]] with GenerateExamGridSelectCourseCommandRequest {
			override def apply(): Seq[GenerateExamGridEntity] = {
				fetchCount = fetchCount + 1
				Seq(entity)
			}
			route = thisRoute
			yearOfStudy = thisYearOfStudy
		}

		val cmd = new GenerateExamGridCheckAndApplyOvercatCommandInternal(null, thisAcademicYear, NoCurrentUser())
			with ModuleRegistrationServiceComponent with UpstreamRouteRuleServiceComponent
			with GenerateExamGridCheckAndApplyOvercatCommandState	with StudentCourseYearDetailsDaoComponent {
			override val moduleRegistrationService = smartMock[ModuleRegistrationService]
			override val studentCourseYearDetailsDao = smartMock[StudentCourseYearDetailsDao]
			override val upstreamRouteRuleService = smartMock[UpstreamRouteRuleService]
		}
		// Just use the default normal load
		cmd.upstreamRouteRuleService.findNormalLoad(thisRoute, thisAcademicYear, thisYearOfStudy) returns None
		cmd.upstreamRouteRuleService.list(thisRoute, thisAcademicYear, thisYearOfStudy) returns Seq()
		cmd.selectCourseCommand = selectCourseCommand
		cmd.moduleRegistrationService.overcattedModuleSubsets(entity, Map(), ModuleRegistrationService.DefaultNormalLoad, Seq()) returns Seq((BigDecimal(0), Seq(mr1, mr2)))
		cmd.filteredEntities.isEmpty should be {false}
		cmd.filteredEntities.head._1 should be (entity)
		cmd.filteredEntities.head._2 should be (Seq((BigDecimal(0), Seq(mr1, mr2))))
		verify(cmd.moduleRegistrationService, times(1)).overcattedModuleSubsets(entity, Map(), ModuleRegistrationService.DefaultNormalLoad, Seq())

		val result = cmd.applyInternal()
		result.entities.size should be (1)
		result.entities.head should be (entity)
		result.updatedEntities.keys.head should be (entity)
		entity.studentCourseYearDetails.get.overcattingModules.nonEmpty should be {true}
		entity.studentCourseYearDetails.get.overcattingModules.get should be (Seq(module1, module2))
		assert(fetchCount == 2)
	}

}
