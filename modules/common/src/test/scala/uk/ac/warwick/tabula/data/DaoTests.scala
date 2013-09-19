package uk.ac.warwick.tabula.data

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Transactional

import uk.ac.warwick.tabula.PersistenceTestBase
import uk.ac.warwick.tabula.JavaImports._
import uk.ac.warwick.tabula.data.model.Module
import uk.ac.warwick.tabula.data.model.UpstreamAssessmentGroup
import uk.ac.warwick.tabula.data.model.UserGroup
import uk.ac.warwick.tabula.helpers.Logging
import org.junit.Before

// TODO should merge into MemberDaoTest?
class DaoTests extends PersistenceTestBase with Logging {

	val memberDao = new MemberDaoImpl

	@Before
	def setup() {
		memberDao.sessionFactory = sessionFactory
	}

	@Transactional
	@Test def findRegisteredModules {
		val group = UserGroup.ofUniversityIds
		group.staticIncludeUsers.add("1112939")
		val module = new Module()
		module.code="en107"

		val upAssessGroup = new UpstreamAssessmentGroup()
		upAssessGroup.moduleCode = "EN107-15"
		upAssessGroup.members = group

		session.saveOrUpdate(group)
		session.saveOrUpdate(module)
		session.saveOrUpdate(upAssessGroup)

		memberDao.getByUniversityId("1112939")

		val modules = memberDao.getByUniversityId("1112939").toSeq.flatMap(_.registeredModulesAnyYear)
	  modules.size should be (1)
		modules.head.code should be ("en107")
	}
}
