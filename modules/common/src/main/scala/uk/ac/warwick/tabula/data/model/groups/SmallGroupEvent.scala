package uk.ac.warwick.tabula.data.model.groups

import javax.persistence.CascadeType._
import javax.persistence._

import org.hibernate.annotations.Type
import org.joda.time.LocalTime
import uk.ac.warwick.spring.Wire
import uk.ac.warwick.tabula.ToString
import uk.ac.warwick.tabula.data.model._
import uk.ac.warwick.tabula.helpers.StringUtils
import uk.ac.warwick.tabula.permissions.PermissionsTarget
import uk.ac.warwick.tabula.services._
import uk.ac.warwick.tabula.services.permissions.PermissionsService

object SmallGroupEvent {

	// Companion object is one of the places searched for an implicit Ordering, so
	// this will be the default when ordering a list of small group events.
	implicit val defaultOrdering = new Ordering[SmallGroupEvent] {
		final val FirstInstanceOrdering = Ordering.by { event: SmallGroupEvent =>
			(Option(event.weekRanges).filter(_.nonEmpty).map { _.minBy { _.minWeek }.minWeek }, Option(event.day).map { _.jodaDayOfWeek }, Option(event.startTime).map { _.getMillisOfDay }, Option(event.endTime).map { _.getMillisOfDay })
		}

		def compare(a: SmallGroupEvent, b: SmallGroupEvent) = {
			val firstInstanceCompare = FirstInstanceOrdering.compare(a, b)
			if (firstInstanceCompare != 0) firstInstanceCompare
			else {
				val titleCompare = StringUtils.AlphaNumericStringOrdering.compare(a.title, b.title)
				if (titleCompare != 0) titleCompare else Ordering.by { event: SmallGroupEvent => Option(event.id) }.compare(a, b)
			}
		}
	}

}

@Entity
@Access(AccessType.FIELD)
class SmallGroupEvent extends GeneratedId with ToString with PermissionsTarget with Serializable {
	import uk.ac.warwick.tabula.data.model.groups.SmallGroupEvent._

	@transient var permissionsService = Wire[PermissionsService]

	// FIXME this isn't really optional, but testing is a pain unless it's made so
	@transient var smallGroupService = Wire.option[SmallGroupService with SmallGroupMembershipHelpers]

	def this(_group: SmallGroup) {
		this()
		this.group = _group
	}

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "group_id", insertable = false, updatable = false)
	var group: SmallGroup = _

	// Store Week Ranges as ACADEMIC week numbers - so week 1 is the first week of Autumn term, week 1 of spring term is 15 or 16, etc.
	@Type(`type` = "uk.ac.warwick.tabula.data.model.groups.WeekRangeListUserType")
	var weekRanges: Seq[WeekRange] = Nil

	@Type(`type` = "uk.ac.warwick.tabula.data.model.groups.DayOfWeekUserType")
	var day: DayOfWeek = _

	var startTime: LocalTime = _
	var endTime: LocalTime = _

	@Type(`type` = "uk.ac.warwick.tabula.data.model.LocationUserType")
	var location: Location = _

	var title: String = _

	def isUnscheduled = day == null || (startTime == null && endTime == null)
	def isSingleEvent = weekRanges.size == 1 && weekRanges.head.isSingleWeek

	@OneToOne(cascade = Array(ALL), fetch = FetchType.LAZY)
	@JoinColumn(name = "tutorsgroup_id")
	private var _tutors: UserGroup = UserGroup.ofUsercodes
	def tutors: UnspecifiedTypeUserGroup = {
		smallGroupService match {
			case Some(sgs) =>
				new UserGroupCacheManager(_tutors, sgs.eventTutorsHelper)
			case _ => _tutors
		}
	}
	def tutors_=(group: UserGroup) { _tutors = group }

	def permissionsParents = Option(group).toStream

	def toStringProps = Seq(
		"id" -> id,
		"weekRanges" -> weekRanges,
		"day" -> day,
		"startTime" -> startTime,
		"endTime" -> endTime)

  def isEquivalentTo(other: SmallGroupEvent):Boolean = {
    weekRanges == other.weekRanges &&
    day == other.day &&
    startTime == other.startTime &&
    endTime == other.endTime &&
    location == other.location &&
    tutors.hasSameMembersAs(other.tutors)
  }

  def duplicateTo(group: SmallGroup, transient: Boolean): SmallGroupEvent = {
    val newEvent = new SmallGroupEvent
		if (!transient) newEvent.id = id
    newEvent.day = day
    newEvent.endTime = endTime
    newEvent.group = group
    newEvent.location = location
    newEvent.permissionsService = permissionsService
    newEvent.startTime = startTime
    newEvent.title = title
    newEvent._tutors = _tutors.duplicate()
    newEvent.weekRanges = weekRanges

    newEvent
  }

	def allWeeks: Seq[WeekRange.Week] = weekRanges.flatMap(_.toWeeks)

}