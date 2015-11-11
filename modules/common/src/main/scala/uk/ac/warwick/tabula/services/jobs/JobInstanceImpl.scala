package uk.ac.warwick.tabula.services.jobs

import javax.persistence.{Column, Entity}

import com.fasterxml.jackson.databind.ObjectMapper
import org.joda.time.DateTime
import uk.ac.warwick.spring.Wire
import uk.ac.warwick.tabula.{CurrentUser, ToString}
import uk.ac.warwick.tabula.data.PostLoadBehaviour
import uk.ac.warwick.tabula.data.model.GeneratedId
import uk.ac.warwick.tabula.helpers.Logging
import uk.ac.warwick.tabula.jobs.JobPrototype
import uk.ac.warwick.tabula.system.CurrentUserInterceptor
import uk.ac.warwick.userlookup.{AnonymousUser, UserLookupInterface}

object JobInstanceImpl {
	def fromPrototype(prototype: JobPrototype) = {
		val instance = new JobInstanceImpl
		instance.jobType = prototype.identifier
		instance.json = prototype.map
		instance
	}
}

/**
 * JobDefinition is the database entity that stores
 * data about the job request, its status and progress.
 * There can be many Job subclasses but JobInstance
 * does not need subclassing.
 */
@Entity(name = "Job")
class JobInstanceImpl() extends JobInstance with GeneratedId with PostLoadBehaviour with Logging with ToString {

	@transient var jsonMapper = Wire.auto[ObjectMapper]
	@transient var userLookup = Wire.auto[UserLookupInterface]
	@transient var currentUserFinder = Wire.auto[CurrentUserInterceptor]

	/** Human-readable status of the job */
	var status: String = _

	var jobType: String = _

	var started = false
	var finished = false
	var succeeded = false

	var realUser: String = _
	var apparentUser: String = _

	def userId = apparentUser

	@transient var user: CurrentUser = _

	var createdDate: DateTime = new DateTime

	var updatedDate: DateTime = new DateTime

	@Column(name = "progress") var _progress: Int = 0
	def progress = _progress
	def progress_=(p: Int) = {
		_progress = p
	}

	// CLOB
	var data: String = "{}"

	@transient private var _json: JsonMap = Map()
	def json = _json
	def json_=(map: JsonMap) {
		_json = map
		if (jsonMapper != null) {
			data = jsonMapper.writeValueAsString(json)
		} else {
			logger.warn("JSON mapper not set on JobInstanceImpl")
		}
	}

	def propsMap = json
	def propsMap_=(map: JsonMap) { json = map }

	override def postLoad {
		val map = jsonMapper.readValue(data, classOf[Map[String, Any]])
		json = map

		updatedDate = new DateTime

		def u(id: String) = id match {
			case id: String => userLookup.getUserByUserId(id)
			case _ => new AnonymousUser
		}

		val realUser = u(this.realUser)
		val apparentUser = u(this.apparentUser)

		user = currentUserFinder.resolveCurrentUser(realUser, { (u, s) => apparentUser }, false)
	}

	def toStringProps = Seq(
		"id" -> id,
		"status" -> status,
		"jobType" -> jobType)

}