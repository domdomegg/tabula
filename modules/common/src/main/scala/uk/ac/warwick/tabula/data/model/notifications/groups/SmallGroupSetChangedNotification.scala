package uk.ac.warwick.tabula.data.model.notifications.groups

import javax.persistence.{DiscriminatorValue, Entity}

import uk.ac.warwick.tabula.data.model.groups.{SmallGroup, SmallGroupSet}
import uk.ac.warwick.tabula.data.model.{FreemarkerModel, NotificationWithTarget, UserIdRecipientNotification}
import uk.ac.warwick.tabula.services.AutowiringUserLookupComponent
import uk.ac.warwick.tabula.web.Routes

object SmallGroupSetChangedNotification {
	val templateLocation = "/WEB-INF/freemarker/notifications/groups/small_group_modified_notification.ftl"
}

abstract class SmallGroupSetChangedNotification(recipientRole: UserRoleOnGroup)
	extends NotificationWithTarget[SmallGroup, SmallGroupSet]
	with UserIdRecipientNotification
	with AutowiringUserLookupComponent {

	def verb = "Modify"

	def title = "%s: Your %s allocation has changed".format(target.entity.module.code.toUpperCase, target.entity.format.description.toLowerCase)

	def content =
		FreemarkerModel(SmallGroupSetChangedNotification.templateLocation, Map(
			"groups" -> entities,
			"groupSet" -> target.entity,
			"profileUrl" -> url
		))

	def url: String = {
		recipientRole match {
			case UserRoleOnGroup.Student =>Routes.profiles.profile.mine
			case UserRoleOnGroup.Tutor => Routes.groups.tutor.mygroups
		}
	}

	def urlTitle = "view this small group"

	def actionRequired = false
}

@Entity
@DiscriminatorValue(value="SmallGroupSetChangedStudent")
class SmallGroupSetChangedStudentNotification extends SmallGroupSetChangedNotification(UserRoleOnGroup.Student)

@Entity
@DiscriminatorValue(value="SmallGroupSetChangedTutor")
class SmallGroupSetChangedTutorNotification extends SmallGroupSetChangedNotification(UserRoleOnGroup.Tutor)

sealed trait UserRoleOnGroup
object UserRoleOnGroup {
	case object Student extends UserRoleOnGroup
	case object Tutor extends UserRoleOnGroup
}

