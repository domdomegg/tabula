package uk.ac.warwick.tabula.data.model

import scala.collection.JavaConversions._
import scala.reflect.BeanProperty
import scala.util.matching.Regex
import org.hibernate.annotations.AccessType
import javax.persistence._
import javax.validation.constraints._
import uk.ac.warwick.tabula.permissions.PermissionsTarget

@Entity
@NamedQueries(Array(
	new NamedQuery(name = "module.code", query = "select m from Module m where code = :code"),
	new NamedQuery(name = "module.department", query = "select m from Module m where department = :department")))
class Module extends GeneratedId with PermissionsTarget {

	def this(code: String = null, department: Department = null) {
		this()
		this.code = code
		this.department = department
	}

	@BeanProperty var code: String = _
	@BeanProperty var name: String = _

	// The participants are markers/moderators who upload feedback. 
	// They can also publish feedback.
	@OneToOne(cascade = Array(CascadeType.ALL))
	@JoinColumn(name = "participantsgroup_id")
	@BeanProperty var participants: UserGroup = new UserGroup

	// return participants, creating an empty one if missing.
	def ensuredParticipants = {
		ensureParticipantsGroup
		participants
	}

	/** Create an empty participants group if it's null. */
	def ensureParticipantsGroup {
		if (participants == null) participants = new UserGroup
	}

	@ManyToOne
	@JoinColumn(name = "department_id")
	@BeanProperty var department: Department = _
	
	def permissionsParents = Seq(Option(department)).flatten
	
	@OneToMany(mappedBy = "module", fetch = FetchType.LAZY, cascade = Array(CascadeType.ALL))
	@BeanProperty var assignments: java.util.List[Assignment] = List()

	@BeanProperty var active: Boolean = _

	override def toString = "Module[" + code + "]"
}

object Module {

	// <modulecode> "-" <cats>
	// where cats can be a decimal number.
	private val ModuleCatsPattern = new Regex("""(.+?)-(\d+(?:\.\d+)?)""")

	def nameFromWebgroupName(groupName: String): String = groupName.indexOf("-") match {
		case -1 => groupName
		case i: Int => groupName.substring(i + 1)
	}

	def stripCats(fullModuleName: String): String = fullModuleName match {
		case ModuleCatsPattern(module, cats) => module
		case _ => throw new IllegalArgumentException(fullModuleName + " didn't match pattern")
	}

	def extractCats(fullModuleName: String): Option[String] = fullModuleName match {
		case ModuleCatsPattern(module, cats) => Some(cats)
		case _ => None
	}
}