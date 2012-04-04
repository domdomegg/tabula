package uk.ac.warwick.courses.data.model
import scala.reflect.BeanProperty
import org.hibernate.annotations.AccessType
import org.hibernate.annotations.GenericGenerator
import javax.persistence.Entity
import javax.persistence.GeneratedValue
import javax.persistence.Id
import javax.validation.constraints._
import javax.persistence.ManyToOne
import javax.persistence.CascadeType
import javax.persistence.JoinColumn
import javax.persistence.NamedQuery
import javax.persistence.NamedQueries
import javax.persistence.OneToMany
import collection.JavaConversions._
import org.hibernate.annotations.FetchMode
import javax.persistence.FetchType
import javax.persistence.OneToOne
import uk.ac.warwick.courses.actions._

@Entity
@NamedQueries(Array(
	new NamedQuery(name="module.code", query="select m from Module m where code = :code"),
	new NamedQuery(name="module.department", query="select m from Module m where department = :department")
))
class Module extends GeneratedId with Viewable with Manageable with Participatable {
	
	def this(code:String=null) {
		this()
		this.code = code
	}
	
	@BeanProperty var code:String = _
	@BeanProperty var name:String = _
	
	// The members are studying the module.
	@OneToOne(cascade=Array(CascadeType.ALL))
	@JoinColumn(name="membersgroup_id")
	@BeanProperty var members:UserGroup = new UserGroup
	
	// The participants are markers/moderators who upload feedback. 
	// They can also publish feedback.
	@OneToOne(cascade=Array(CascadeType.ALL))
	@JoinColumn(name="participantsgroup_id")
	@BeanProperty var participants:UserGroup = new UserGroup
	
	// return participants, creating an empty one if missing.
	def ensuredParticipants = {
		ensureParticipantsGroup
		participants
	}
	
	def ensuredMembers = {
		ensureMembersGroup
		members
	}
	
	/** Create an empty participants group if it's null. */
	def ensureParticipantsGroup {
		if (participants == null) participants = new UserGroup
	}
	
	def ensureMembersGroup {
		if (members == null) members = new UserGroup
	}
	
	@ManyToOne
	@JoinColumn(name="department_id")
	@BeanProperty var department:Department = _
	
	@OneToMany(mappedBy="module", fetch=FetchType.LAZY, cascade=Array(CascadeType.ALL))
	@BeanProperty var assignments:java.util.List[Assignment] = List()
	
	@BeanProperty var active:Boolean = _
	
	override def toString = "Module["+code+"]"
}

object Module {
	def nameFromWebgroupName(groupName:String) : String = groupName.indexOf("-") match {
		case -1 => groupName
		case i:Int => groupName.substring(i+1)
	}
}