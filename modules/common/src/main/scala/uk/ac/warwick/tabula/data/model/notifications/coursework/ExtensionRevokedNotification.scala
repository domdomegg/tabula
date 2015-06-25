package uk.ac.warwick.tabula.data.model.notifications.coursework

import javax.persistence.{DiscriminatorValue, Entity}

import uk.ac.warwick.tabula.coursework.web.Routes
import uk.ac.warwick.tabula.data.model.NotificationPriority.Warning
import uk.ac.warwick.tabula.data.model.{Assignment, FreemarkerModel, Notification, SingleItemNotification, SingleRecipientNotification, UniversityIdRecipientNotification}
import uk.ac.warwick.tabula.services.AutowiringUserLookupComponent

object ExtensionRevokedNotification {
	val templateLocation = "/WEB-INF/freemarker/emails/revoke_manual_extension.ftl"
}

@Entity
@DiscriminatorValue("ExtensionRevoked")
class ExtensionRevokedNotification extends Notification[Assignment, Unit]
	with SingleItemNotification[Assignment]
	with SingleRecipientNotification
	with UniversityIdRecipientNotification
	with AutowiringUserLookupComponent {

	priority = Warning

	def assignment = item.entity

	def verb = "revoke"

	def title =	"%s: Your extended deadline for \"%s\" has been revoked".format(assignment.module.code.toUpperCase, assignment.name)
	def url = Routes.assignment.apply(assignment)
	def urlTitle = "view the assignment"

	def content = FreemarkerModel(ExtensionRevokedNotification.templateLocation, Map (
		"assignment" -> assignment,
		"module" -> assignment.module,
		"user" -> recipient,
		"originalAssignmentDate" -> dateTimeFormatter.print(assignment.closeDate)
	))
}