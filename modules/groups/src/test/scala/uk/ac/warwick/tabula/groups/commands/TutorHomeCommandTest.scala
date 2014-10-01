package uk.ac.warwick.tabula.groups.commands

import uk.ac.warwick.tabula.{Mockito, TestBase}
import uk.ac.warwick.tabula.data.model.{Department, Module}
import uk.ac.warwick.tabula.services.{SecurityService, SmallGroupService}
import uk.ac.warwick.tabula.data.model.groups.{SmallGroupSet, SmallGroup}

class TutorHomeCommandTest extends TestBase with Mockito {
	@Test def commandWorks() {
		withUser("cusebr") {
			val department = new Department
			val groups = Seq(new SmallGroup, new SmallGroup)
			val set = new SmallGroupSet
			set.releasedToTutors = true

			val module = new Module
			module.department = department
			set.module = module
			for (group <- groups) group.groupSet = set

			val command = new TutorHomeCommandImpl(currentUser)

			command.smallGroupService = mock[SmallGroupService]
			command.securityService = mock[SecurityService]

			command.smallGroupService.findSmallGroupsByTutor(currentUser.apparentUser) returns (groups)

			val result = command.applyInternal()
			result should be (Map(module -> Map(set->groups)))
		}
	}
}
