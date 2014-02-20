package uk.ac.warwick.tabula.services

import uk.ac.warwick.tabula.{PersistenceTestBase, TestBase, AppContextTestBase}
import org.junit.Test
import org.springframework.beans.factory.annotation.Autowired
import uk.ac.warwick.tabula.events.Event
import org.springframework.transaction.annotation.Transactional
import org.joda.time.DateTime
import org.hibernate.dialect.HSQLDialect
import uk.ac.warwick.tabula.data.SessionComponent

// scalastyle:off magic.number
class AuditEventServiceTest extends PersistenceTestBase {
	
	val service =new AuditEventServiceImpl with SessionComponent {
		def session = sessionFactory.getCurrentSession
	}
	service.dialect = new HSQLDialect

	val now = new DateTime()

	@Transactional
	@Test def getByIds {
		for (i <- Range(0, 1020)) {
			val event = new Event(s"id$i", "DownloadFeedback", "cusebr", "cusebr", Map(), now.plusSeconds(i))
			service.save(event, "before")
			service.save(event, "after")
		}
		val recent = service.listRecent(0, 1020)
		recent.length should be (1020)

		val result = service.getByIds(recent.map(_.id))
		result.length should be (1020)
	}

	@Transactional
	@Test def listEvents {
		for (i <- Range(1, 30)) {
			val event = new Event("1138-9962-1813-4938", "Bite" + i, "cusebr", "cusebr", Map(), now.plusSeconds(i))
			service.save(event, "pre")
		}

		val recent = service.listRecent(5, 10).toList
		recent.size should be(10)
		recent(0).eventType should be("Bite24")
		recent(2).eventType should be("Bite22")
	}
}