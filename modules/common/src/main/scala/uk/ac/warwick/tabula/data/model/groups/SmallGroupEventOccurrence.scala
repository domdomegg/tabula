package uk.ac.warwick.tabula.data.model.groups

import javax.persistence._
import org.hibernate.annotations.AccessType
import uk.ac.warwick.tabula.data.model.{UserGroup, GeneratedId}
import uk.ac.warwick.tabula.permissions.PermissionsTarget
import scala.Array
import javax.persistence.CascadeType._
import java.lang.annotation.Annotation

@AccessType("field")
@Entity
@Table(uniqueConstraints = Array(
	new UniqueConstraint(columnNames = Array("event_id", "week"))
))
class SmallGroupEventOccurrence extends GeneratedId with PermissionsTarget with Serializable {
	type WeekNumber = Int

	@ManyToOne
	@JoinColumn(name="event_id")
	var smallGroupEvent: SmallGroupEvent = _

	var week: WeekNumber = _

	@OneToOne(cascade = Array(ALL))
	@JoinColumn(name = "membersgroup_id")
	var attendees: UserGroup = new UserGroup

	def permissionsParents = Stream(smallGroupEvent)

}
