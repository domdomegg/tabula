package uk.ac.warwick.tabula.data

import org.junit.{After, Before}
import org.springframework.test.context.transaction.{TransactionConfiguration, BeforeTransaction}
import org.springframework.transaction.annotation.Transactional
import org.springframework.context.annotation.{ClassPathScanningCandidateComponentProvider, ClassPathBeanDefinitionScanner}
import org.springframework.core.`type`.filter.AssignableTypeFilter
import uk.ac.warwick.tabula.{PackageScanner, Mockito, Fixtures, PersistenceTestBase}
import uk.ac.warwick.tabula.data.model._
import uk.ac.warwick.tabula.services.UserLookupService
import uk.ac.warwick.userlookup.User
import scala.reflect.runtime.universe._
import javax.persistence.DiscriminatorValue
import org.joda.time.{DateTimeUtils, DateTime}
import uk.ac.warwick.tabula.data.model.groups.SmallGroup
import uk.ac.warwick.tabula.data.model.notifications.{ScheduledMeetingRecordInviteeNotification, ScheduledMeetingRecordNotification, SubmissionReceivedNotification}
import org.hibernate.ObjectNotFoundException

@Transactional
class NotificationDaoTest extends PersistenceTestBase with Mockito {

	val notificationDao = new NotificationDaoImpl

	val agentMember = Fixtures.member(userType=MemberUserType.Staff)
	val agent = agentMember.asSsoUser

	val victim = Fixtures.user("heronVictim")
	val heron = new Heron(victim)

	@Before
	def setup() {
		notificationDao.sessionFactory = sessionFactory
		SSOUserType.userLookup = smartMock[UserLookupService]
		// hbm2ddl generates a swathe of conflicting foreign key constraints for entity_id, so ignore for this test
		session.createSQLQuery("SET DATABASE REFERENTIAL INTEGRITY FALSE").executeUpdate()
	}

	@After
	def teardown() {
		SSOUserType.userLookup = null
		DateTimeUtils.setCurrentMillisSystem()
	}

	def newHeronNotification(agent: User, heron: Heron) = {
		Notification.init(new HeronWarningNotification, agent, heron, heron)
	}

	@Test def saveAndFetch() {

			val notification = newHeronNotification(agent, heron)

			session.save(heron)

			notificationDao.getById("heronWarningNotification") should be (None)
			notificationDao.save(notification)
			notificationDao.getById(notification.id) should be (Option(notification))

			session.flush()
			session.clear()

			val retrievedNotification = notificationDao.getById(notification.id).get.asInstanceOf[HeronWarningNotification]
			retrievedNotification.title should be ("Blissfully unaware group - You all need to know. Herons would love to kill you in your sleep")
			retrievedNotification.url should be ("/beware/herons")
			retrievedNotification.item.entity should be(heron)
			retrievedNotification.target should not be(null)
			retrievedNotification.target.entity should be(heron)
			retrievedNotification.recipient should be (victim)
			retrievedNotification.content.template should be ("/WEB-INF/freemarker/notifications/i_really_hate_herons.ftl")

			session.clear()
			session.delete(heron)
			session.flush()

			// If an attached entityreference points to a now non-existent thing, we shouldn't explode
			// when loading the Notification but only when accessing the lazy entityreference.

			val notificationWithNoItem = try {
				 notificationDao.getById(notification.id).get.asInstanceOf[HeronWarningNotification]
			} catch {
				case e: ObjectNotFoundException =>
					fail("Shouldn't throw ObjectNotFoundException until entity reference is accessed")
			}

			// asserts that this type of exception is thrown
			intercept[ObjectNotFoundException] {
				notificationWithNoItem.item
			}
	}

	@Test
	def submissionReceived() {
		val submission = Fixtures.submission()
		val assignment = Fixtures.assignment("Fun")
		assignment.addSubmission(submission)
		val notification = Notification.init(new SubmissionReceivedNotification, agent, submission, assignment)

		notificationDao.save(notification)
		notification.target.id should not be (null)
	}

	@Test
	def scheduledMeetings() {
		val meeting = new ScheduledMeetingRecord
		val relationship = new MemberStudentRelationship

		session.save(meeting)
		session.save(relationship)

		val r: StudentRelationship = relationship
		relationship.agentMember = agentMember
		val notification = Notification.init(new ScheduledMeetingRecordInviteeNotification, agent, Seq(meeting), r)
		notificationDao.save(notification)

		session.flush()
		session.clear()

		val retrieved = notificationDao.getById(notification.id).get.asInstanceOf[ScheduledMeetingRecordNotification]
		retrieved.meeting
	}

	@Test def recent() {
		val agent = Fixtures.user()
		val group = Fixtures.smallGroup("Blissfully unaware group")
		session.save(group)
		var ii = 0
		val now = DateTime.now
		DateTimeUtils.setCurrentMillisFixed(now.getMillis)
		val notifications = for (i <- 1 to 1000) {
			val notification = newHeronNotification(agent, heron)
			notification.created = now.minusMinutes(i)
			notificationDao.save(notification)
			ii += 1
		}
		session.flush()

		val everything = notificationDao.recent(now.minusMonths(10)).takeWhile(n => true).toSeq
		everything.size should be (1000)

		val oneHundred = notificationDao.recent(now.minusMonths(10)).take(100).toSeq
		oneHundred.size should be (100)

		def noNewer(mins: Int)(n: Notification[_,_]) = n.created.isBefore(now.minusMinutes(mins))

		val recent = notificationDao.recent(now.minusMonths(10)).takeWhile(noNewer(25)).toSeq
		recent.size should be (975)

	}

	/**
	 * Ensure there's nothing obviously wrong with the Notification subclass mappings. This will detect e.g.
	 * if an @Entity or @DiscriminatorValue are missing.
	 */
	@Test def nooneDied() {
		val notificationClasses = PackageScanner.subclassesOf[Notification[_,_]]("uk.ac.warwick.tabula.data.model")
		withClue("Package scanner should find a sensible number of classes") {
			notificationClasses.size should be > 5
		}

		val user = new User
		user.setUserId("testid")

		for (clazz <- notificationClasses) {
			try {
				val notification = clazz.getConstructor().newInstance().asInstanceOf[Notification[ToEntityReference,_]]
				notification.agent = user

				session.save(notification)

				if (clazz.getAnnotation(classOf[DiscriminatorValue]) == null) {
					fail(s"Notification ${clazz} has no @DiscriminatorValue annotation")
				}

				// FIXME we do want to flush because it would test things we care about, but many of the subclasses
				// expect specific properties to be set in order to save successfully. Need to do magic reflection to
				// work out what this is???
				//session.flush()
			} catch {
				case e: Exception => {
					fail(s"Exception saving ${clazz}", e)
				}
			}
		}
	}
}
