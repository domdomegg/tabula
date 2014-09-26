package uk.ac.warwick.tabula.groups.commands.admin

import org.joda.time.LocalTime
import uk.ac.warwick.tabula.commands.DescriptionImpl
import uk.ac.warwick.tabula.data.model.{NamedLocation, Location, Module}
import uk.ac.warwick.tabula.data.model.groups._
import uk.ac.warwick.tabula.groups.commands.admin.ImportSmallGroupSetsFromExternalSystemCommand.TimetabledSmallGroupEvent
import uk.ac.warwick.tabula.permissions.Permissions
import uk.ac.warwick.tabula.services._
import uk.ac.warwick.tabula.services.timetables.{ModuleTimetableFetchingService, ModuleTimetableFetchingServiceComponent}
import uk.ac.warwick.tabula._
import uk.ac.warwick.tabula.system.permissions.{PermissionsChecking, RequiresPermissionsChecking}
import uk.ac.warwick.tabula.timetables.{TimetableEventType, TimetableEvent}
import uk.ac.warwick.userlookup.User

class ImportSmallGroupSetsFromExternalSystemCommandTest extends TestBase with Mockito {

	private trait CommandTestSupport extends ModuleTimetableFetchingServiceComponent
		with ImportSmallGroupSetsFromExternalSystemPermissionsRestrictedState
		with SmallGroupServiceComponent
		with SmallGroupSetGenerator
		with SmallGroupEventGenerator
		with UserLookupComponent
		with ModuleAndDepartmentServiceComponent
		with SecurityServiceComponent {
		self: ImportSmallGroupSetsFromExternalSystemCommandState =>

		val timetableFetchingService = mock[ModuleTimetableFetchingService]
		val smallGroupService = mock[SmallGroupService]
		val userLookup = new MockUserLookup
		val moduleAndDepartmentService = mock[ModuleAndDepartmentService]
		val securityService = mock[SecurityService]

		def createSet(module: Module, format: SmallGroupFormat, name: String) = {
			val set = new SmallGroupSet(module)
			set.format = format
			set.name = name

			module.groupSets.add(set)
			set
		}

		def createEvent(module: Module, set: SmallGroupSet, group: SmallGroup, weeks: Seq[WeekRange], day: DayOfWeek, startTime: LocalTime, endTime: LocalTime, location: Option[Location], tutorUsercodes: Seq[String]) = {
			val event = new SmallGroupEvent(group)
			event.weekRanges = weeks
			event.day = day
			event.startTime = startTime
			event.endTime = endTime
			location.foreach { location => event.location = location }
			event.tutors.knownType.includedUserIds = tutorUsercodes

			group.addEvent(event)
			event
		}
	}

	private trait Fixture {
		val department = Fixtures.department("in")

		val module1 = Fixtures.module("in101")
		val module2 = Fixtures.module("in102")

		department.modules.add(module1)
		department.modules.add(module2)

		val user = new User("cuscav")
		val currentUser = new CurrentUser(user, user)
	}

	private trait CommandFixture extends Fixture {
		val command = new ImportSmallGroupSetsFromExternalSystemCommandInternal(department, currentUser) with CommandTestSupport
	}

	private trait FixtureWithSingleSeminarForYear {
		self: CommandFixture =>

		command.academicYear = AcademicYear(2012)

		command.securityService.can(currentUser, Permissions.SmallGroups.ImportFromExternalSystem, department) returns (true)

		val tEventModule1Seminar1 = TimetableEvent(
			uid="uuid1",
			name="IN101S",
			title="",
			description="",
			startTime=new LocalTime(12, 0),
			endTime=new LocalTime(13, 0),
			weekRanges=Seq(WeekRange(6, 10)),
			day=DayOfWeek.Friday,
			eventType=TimetableEventType.Seminar,
			location=Some(NamedLocation("CS1.04")),
			context=Some("IN101"),
			comments=None,
			staffUniversityIds=Seq("1170047"),
			studentUniversityIds=Seq("0000001", "0000002", "0000003"),
			year = AcademicYear(2012)
		)
		val tEventModule1Seminar2 = TimetableEvent(
			uid="uuid2",
			name="IN101S",
			title="",
			description="",
			startTime=new LocalTime(12, 0),
			endTime=new LocalTime(13, 0),
			weekRanges=Seq(WeekRange(6, 10)),
			day=DayOfWeek.Thursday,
			eventType=TimetableEventType.Seminar,
			location=Some(NamedLocation("CS1.04")),
			context=Some("IN101"),
			comments=None,
			staffUniversityIds=Seq("1170047"),
			studentUniversityIds=Seq("0000004", "0000005", "0000006"),
			year = AcademicYear(2012)
		)

		command.timetableFetchingService.getTimetableForModule("IN101") returns (Seq(
			tEventModule1Seminar1, tEventModule1Seminar2,
			TimetableEvent(
				uid="uuid3",
				name="IN101L",
				title="",
				description="",
				startTime=new LocalTime(12, 0),
				endTime=new LocalTime(13, 0),
				weekRanges=Seq(WeekRange(6, 10)),
				day=DayOfWeek.Friday,
				eventType=TimetableEventType.Lecture,
				location=Some(NamedLocation("L5")),
				context=Some("IN101"),
				comments=None,
				staffUniversityIds=Seq("1170047"),
				studentUniversityIds=Nil,
				year = AcademicYear(2012)
			)
		))
		command.timetableFetchingService.getTimetableForModule("IN102") returns (Seq(
			TimetableEvent(
				uid="uuid4",
				name="IN102S",
				title="",
				description="",
				startTime=new LocalTime(12, 0),
				endTime=new LocalTime(13, 0),
				weekRanges=Seq(WeekRange(6, 10)),
				day=DayOfWeek.Thursday,
				eventType=TimetableEventType.Seminar,
				location=Some(NamedLocation("CS1.04")),
				context=Some("IN102"),
				comments=None,
				staffUniversityIds=Seq("1170047"),
				studentUniversityIds=Seq("0000004", "0000005", "0000006"),
				year = AcademicYear(2013)
			)
		))

		val tutor = new User("abcdef")
		tutor.setFoundUser(true)
		tutor.setWarwickId("1170047")
		command.userLookup.registerUserObjects(tutor)
	}

	@Test def init { new CommandFixture with FixtureWithSingleSeminarForYear {
		command.canManageDepartment should be (true)
		command.modules should be (Seq(module1, module2))
		command.timetabledEvents should be (Seq(
			new TimetabledSmallGroupEvent(module1, TimetableEventType.Seminar, Seq(tEventModule1Seminar2, tEventModule1Seminar1))
		))
	}}

	@Test def apply { new CommandFixture with FixtureWithSingleSeminarForYear {
		val sets = command.applyInternal()

		there was one (command.smallGroupService).saveOrUpdate(any[SmallGroupSet])
		there were two (command.smallGroupService).saveOrUpdate(any[SmallGroup])

		sets.size should be (1)

		val set = sets.head
		set.format should be (SmallGroupFormat.Seminar)
		set.name should be ("IN101 Seminars")
		set.groups.size should be (2)

		val group1 = set.groups.get(1) // Order intentionally reversed; the events are re-ordered because Thursday is before Friday
		group1.name = "Group 1"
		group1.students.knownType.includedUserIds should be (Seq("0000001", "0000002", "0000003"))
		group1.events.size should be (1)

		val group1event = group1.events.head
		group1event.weekRanges should be (Seq(WeekRange(6, 10)))
		group1event.day should be (DayOfWeek.Friday)
		group1event.startTime should be (new LocalTime(12, 0))
		group1event.endTime should be (new LocalTime(13, 0))
		group1event.location should be (NamedLocation("CS1.04"))
		group1event.tutors.knownType.includedUserIds should be (Seq("abcdef"))

		val group2 = set.groups.get(0) // Order intentionally reversed; the events are re-ordered because Thursday is before Friday
		group2.name = "Group 2"
		group2.students.knownType.includedUserIds should be (Seq("0000004", "0000005", "0000006"))

		val group2event = group2.events.head
		group2event.weekRanges should be (Seq(WeekRange(6, 10)))
		group2event.day should be (DayOfWeek.Thursday)
		group2event.startTime should be (new LocalTime(12, 0))
		group2event.endTime should be (new LocalTime(13, 0))
		group2event.location should be (NamedLocation("CS1.04"))
		group2event.tutors.knownType.includedUserIds should be (Seq("abcdef"))
	}}

	private trait PermissionsTestSupport extends ImportSmallGroupSetsFromExternalSystemPermissionsRestrictedState
		with SecurityServiceComponent with ModuleAndDepartmentServiceComponent {
		self: ImportSmallGroupSetsFromExternalSystemCommandState with RequiresPermissionsChecking =>

		val securityService = mock[SecurityService]
		val moduleAndDepartmentService = mock[ModuleAndDepartmentService]
	}

	private trait PermissionsFixture extends Fixture {
		val command = new ImportSmallGroupSetsFromExternalSystemPermissions with ImportSmallGroupSetsFromExternalSystemCommandState with PermissionsTestSupport {
			val department = PermissionsFixture.this.department
			val user = PermissionsFixture.this.currentUser
		}
	}

	private trait DepartmentalAdministratorPermissions {
		self: PermissionsFixture =>

		command.securityService.can(currentUser, Permissions.SmallGroups.ImportFromExternalSystem, department) returns (true)
	}

	private trait ModuleManagerPermissions {
		self: PermissionsFixture =>

		command.securityService.can(currentUser, Permissions.SmallGroups.ImportFromExternalSystem, department) returns (false)
		command.moduleAndDepartmentService.modulesWithPermission(currentUser, Permissions.SmallGroups.ImportFromExternalSystem, department) returns (Set(module1, module2))
	}

	private trait NoPermissions {
		self: PermissionsFixture =>

		command.securityService.can(currentUser, Permissions.SmallGroups.ImportFromExternalSystem, department) returns (false)
		command.moduleAndDepartmentService.modulesWithPermission(currentUser, Permissions.SmallGroups.ImportFromExternalSystem, department) returns (Set.empty)
	}

	@Test def deptAdminPermissions { new PermissionsFixture with DepartmentalAdministratorPermissions {
		val checking = mock[PermissionsChecking]
		command.permissionsCheck(checking)

		there was one(checking).PermissionCheck(Permissions.SmallGroups.ImportFromExternalSystem, department)
	}}

	@Test def noPermissions { new PermissionsFixture with DepartmentalAdministratorPermissions {
		val checking = mock[PermissionsChecking]
		command.permissionsCheck(checking)

		there was one(checking).PermissionCheck(Permissions.SmallGroups.ImportFromExternalSystem, department)
	}}

	@Test def moduleManagerPermissions { new PermissionsFixture with ModuleManagerPermissions {
		val checking = mock[PermissionsChecking]
		command.permissionsCheck(checking)

		there was one(checking).PermissionCheckAll(Permissions.SmallGroups.ImportFromExternalSystem, Seq(module1, module2))
	}}

	@Test def description {
		val command = new ImportSmallGroupSetsFromExternalSystemDescription with ImportSmallGroupSetsFromExternalSystemCommandState {
			override val eventName: String = "test"
			val department = Fixtures.department("in")
			val user = mock[CurrentUser]
		}

		val d = new DescriptionImpl
		command.describe(d)

		d.allProperties should be (Map(
			"department" -> "in"
		))
	}

}
