package uk.ac.warwick.tabula.services.jobs

import org.joda.time.DateTime
import uk.ac.warwick.tabula.CurrentUser
import uk.ac.warwick.tabula.JavaImports._

import scala.collection.JavaConverters._

/**
 * Interface for a Job to update its status in the database.
 *
 * The main implementation of this is JobInstanceImpl which is
 * stores in the database.
 */
trait JobInstance {
	type JsonMap = Map[String, Any]
	
	protected var propsMap: Map[String, Any]

	def jobType: String

	var id: String

	def getString(name: String) = propsMap(name).toString
	def setString(name: String, value: String) = propsMap = propsMap + (name -> value)

	def getStrings(name: String) = propsMap(name).asInstanceOf[JList[String]].asScala.toSeq
	def setStrings(name: String, value: Seq[String]) = propsMap = propsMap + (name -> value.asJava)

	var createdDate: DateTime
	var updatedDate: DateTime

	var status: String
	var progress: Int

	var started: Boolean
	var finished: Boolean
	var succeeded: Boolean
	
	def json: JsonMap

	def user: CurrentUser
}