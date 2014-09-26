package uk.ac.warwick.tabula.data.model.groups

import javax.persistence.CascadeType._
import javax.persistence._

import org.hibernate.annotations.{AccessType, BatchSize, Filter, FilterDef}
import uk.ac.warwick.spring.Wire
import uk.ac.warwick.tabula.JavaImports._
import uk.ac.warwick.tabula.ToString
import uk.ac.warwick.tabula.data.PostLoadBehaviour
import uk.ac.warwick.tabula.data.model._
import uk.ac.warwick.tabula.helpers.StringUtils
import uk.ac.warwick.tabula.permissions.PermissionsTarget
import uk.ac.warwick.tabula.services.permissions.PermissionsService
import uk.ac.warwick.tabula.services.{SmallGroupMembershipHelpers, SmallGroupService, UserGroupCacheManager}

import scala.collection.JavaConverters._

object SmallGroup {
	final val NotDeletedFilter = "notDeleted"
	final val DefaultGroupSize = 15
	object Settings {
		val MaxGroupSize = "MaxGroupSize"
	}

	// For sorting a collection by group name. Either pass to the sort function,
	// or expose as an implicit val.
	val NameOrdering = new Ordering[SmallGroup] {
		def compare(a: SmallGroup, b: SmallGroup) = {
			val nameCompare = StringUtils.AlphaNumericStringOrdering.compare(a.name, b.name)
			if (nameCompare != 0) nameCompare else a.id compare b.id
		}
	}

	// Companion object is one of the places searched for an implicit Ordering, so
	// this will be the default when ordering a list of small groups.
	implicit val defaultOrdering = NameOrdering
}

/**
 * Represents a single small teaching group within a group set.
 */
@FilterDef(name = SmallGroup.NotDeletedFilter, defaultCondition = "deleted = 0")
@Filter(name = SmallGroup.NotDeletedFilter)
@Entity
@AccessType("field")
class SmallGroup
		extends GeneratedId
		with CanBeDeleted
		with ToString
		with PermissionsTarget
		with HasSettings
		with Serializable
		with PostLoadBehaviour
		with ToEntityReference {
	type Entity = SmallGroup
	import uk.ac.warwick.tabula.data.model.groups.SmallGroup._
	
	@transient var permissionsService = Wire[PermissionsService]

	// FIXME this isn't really optional, but testing is a pain unless it's made so
	@transient var smallGroupService = Wire.option[SmallGroupService with SmallGroupMembershipHelpers]

	def this(_set: SmallGroupSet) {
		this()
		this.groupSet = _set
	}

	@Column(name="name")
	private var _name: String = _
	def name = Option(linkedDepartmentSmallGroup).map { _.name }.getOrElse(_name)
	def name_=(name: String) { _name = name }

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "set_id", insertable = false, updatable = false)
	var groupSet: SmallGroupSet = _
	
	@OneToMany(fetch = FetchType.LAZY, cascade = Array(CascadeType.ALL), orphanRemoval=true)
	@JoinColumn(name = "group_id")
	@BatchSize(size=200)
	private var _events: JList[SmallGroupEvent] = JArrayList()
	def events = _events.asScala.sorted
	private def events_=(e: Seq[SmallGroupEvent]) { _events = e.asJava }
	def addEvent(event: SmallGroupEvent) = _events.add(event)
	def removeEvent(event: SmallGroupEvent) = _events.remove(event)

	// A linked departmental small group; if this is linked, allocations aren't kept here.
	@ManyToOne(fetch = FetchType.LAZY, optional = true)
	@JoinColumn(name = "linked_dept_group_id")
	var linkedDepartmentSmallGroup: DepartmentSmallGroup = _
	
	def permissionsParents = Option(groupSet).toStream
	override def humanReadableId = name

	/**
	 * Direct access to the underlying UserGroup. Most of the time you don't want to us this; unless you're setting
	 * it to a new UserGroup, you should probably access "students" instead and work with Users rather than guessing what
	 * the right sort of UserId to use is.
	 */
	@OneToOne(cascade = Array(ALL), fetch = FetchType.LAZY)
	@JoinColumn(name = "studentsgroup_id")
	private var _studentsGroup: UserGroup = UserGroup.ofUniversityIds
  def students: UnspecifiedTypeUserGroup = {
		linkedDepartmentSmallGroup match {
			case ldsg: DepartmentSmallGroup => ldsg.students
			case _ =>
				smallGroupService match {
					case Some(service) =>
						new UserGroupCacheManager(_studentsGroup, service.studentGroupHelper)
					case _ => _studentsGroup
				}
		}
	}
	def students_=(group: UserGroup) { _studentsGroup = group }

	def maxGroupSize = getIntSetting(Settings.MaxGroupSize)
	def maxGroupSize_=(defaultSize:Int) = settings += (Settings.MaxGroupSize -> defaultSize)
	def removeMaxGroupSize() = settings -= Settings.MaxGroupSize

	def isFull = groupSet.defaultMaxGroupSizeEnabled && maxGroupSize.getOrElse(0) <= students.size

	def toStringProps = Seq(
		"id" -> id,
		"name" -> name,
		"set" -> groupSet)


  def hasEquivalentEventsTo(other:SmallGroup) = {
    (this eq other ) ||
    {
      val eventsSC = events
      val otherEvents = other.events
      val allMyEventsExistOnOther = eventsSC.forall(ev=>otherEvents.exists(oe=>oe.isEquivalentTo(ev)))
      val allOthersEventsExistOnMe = otherEvents.forall(oe=>eventsSC.exists(ev=>ev.isEquivalentTo(oe)))
      allMyEventsExistOnOther && allOthersEventsExistOnMe
    }
  }

  def duplicateTo(groupSet: SmallGroupSet): SmallGroup = {
    val newGroup = new SmallGroup()
    newGroup.id = id
    newGroup.events = events.map(_.duplicateTo(newGroup))
    newGroup.groupSet = groupSet
    newGroup.name = name
		newGroup.linkedDepartmentSmallGroup = linkedDepartmentSmallGroup
    newGroup.permissionsService = permissionsService
		if (_studentsGroup != null) newGroup._studentsGroup = _studentsGroup.duplicate()
		newGroup.settings = Map() ++ (if (settings != null) settings else Map())
    newGroup
  }

	def postLoad {
		ensureSettings
	}
	
	def hasScheduledEvents = events.exists(!_.isUnscheduled)

	override def toEntityReference = new SmallGroupEntityReference().put(this)
}