package uk.ac.warwick.tabula.data

import org.hibernate.SessionFactory
import scala.reflect.Manifest

import scala.annotation.target.field
import javax.sql.DataSource
import org.hibernate.Session
import uk.ac.warwick.tabula.data.model.CanBeDeleted
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.transaction.TransactionStatus
import org.springframework.transaction.support.TransactionCallback
import org.springframework.transaction.PlatformTransactionManager
import uk.ac.warwick.spring.Wire
import language.implicitConversions
import scala.reflect._

/**
 * A trait for DAO classes to mix in to get useful things
 * like the current session.
 *
 * It's only really for Hibernate access to the default
 * session factory. If you want to do JDBC stuff or use a
 * different data source you'll need to look elsewhere.
 */
trait Daoisms {
	import uk.ac.warwick.tabula.data.Transactions._

	import org.hibernate.criterion.Restrictions._
	def is = org.hibernate.criterion.Restrictions.eq _

	// FIXME This is a shitty hack to get around the fact that Wire[] doesn't resolve circular dependencies, 
	// and we want to inject this as if it's a var at runtime rather than a lazy val
	private var _testDataSource: DataSource = _
	private var _testSessionFactory: SessionFactory = _
	lazy private val _dataSource = Wire[DataSource]("dataSource")
	lazy private val _sessionFactory = Wire[SessionFactory]
	
	def dataSource = Option(_testDataSource) getOrElse (_dataSource)
	def dataSource_=(ds: DataSource) { _testDataSource = ds }
	
	def sessionFactory = Option(_testSessionFactory) getOrElse (_sessionFactory)
	def sessionFactory_=(sf: SessionFactory) { _testSessionFactory = sf }
	// End FIXME

	protected def session = sessionFactory.getCurrentSession

	def isFilterEnabled(name: String) = session.getEnabledFilter(name) != null

	/**
	 * Do some work in a new session. Only needed outside of a request,
	 * since we already have sessions there. When you know there's already
	 * a session, you can access it through the `session` getter (within
	 * the callback of this method, it should work too).
	 */
	protected def inSession(fn: (Session) => Unit) {
		val sess = sessionFactory.openSession()
		try fn(sess) finally sess.close()
	}

	/**
	 * Adds a method to Session which returns a wrapped Criteria or Query that works
	 * better with Scala's generics support.
	 */
	implicit class NiceQueryCreator(session: Session) {
		def newCriteria[A: ClassTag] = new ScalaCriteria[A](session.createCriteria(classTag[A].runtimeClass))
		def newQuery[A](hql: String) = new ScalaQuery[A](session.createQuery(hql))
	}

	/**
	 * type-safe session.get. returns an Option object, which will match None if
	 * null is returned.
	 *
	 * For CanBeDeleted entities, it also checks if the entity is deleted and
	 * the notDeleted filter is enabled, in which case it also returns None.
	 */
	protected def getById[A:ClassTag](id: String): Option[A] = {
		val runtimeClass = classTag[A].runtimeClass
		session.get(runtimeClass.getName(), id) match {
			case entity: CanBeDeleted if entity.deleted && isFilterEnabled("notDeleted") => None
			case entity: Any if runtimeClass.isInstance(entity) => Some(entity.asInstanceOf[A])
			case _ => None
		}
	}

}
