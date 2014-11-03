package uk.ac.warwick.tabula.data.model.notifications.coursework

import javax.persistence.{DiscriminatorValue, Entity}

import uk.ac.warwick.tabula.coursework.web.Routes
import uk.ac.warwick.tabula.data.model.{FreemarkerModel, NotificationPriority, SingleRecipientNotification}

abstract class ExtensionStudentNotification extends ExtensionNotification with SingleRecipientNotification {

	def recipient = student
	def url = Routes.assignment(assignment)
	def template: String
	def actionRequired = false

	def content = FreemarkerModel(template, Map (
			"assignment" -> assignment,
			"module" -> assignment.module,
			"user" -> recipient,
			"path" -> url,
			"extension" -> extension,
			"newExpiryDate" -> dateTimeFormatter.print(extension.expiryDate),
			"originalAssignmentDate" -> dateTimeFormatter.print(assignment.closeDate)
	))
}

@Entity
@DiscriminatorValue("ExtensionChanged")
class ExtensionChangedNotification extends ExtensionStudentNotification {
	def verb = "updated"
	def title = titlePrefix + "Your extended deadline for \"%s\" has changed".format(assignment.name)
	def template = "/WEB-INF/freemarker/emails/modified_manual_extension.ftl"
	def urlTitle = "view the modified deadline"
}

@Entity
@DiscriminatorValue("ExtensionGranted")
class ExtensionGrantedNotification extends ExtensionStudentNotification {
	def verb = "grant"
	def title = titlePrefix + "Your deadline for \"%s\" has been extended".format(assignment.name)
	def template = "/WEB-INF/freemarker/emails/new_manual_extension.ftl"
	def urlTitle = "view your new deadline"
}

@Entity
@DiscriminatorValue("ExtensionRequestApproved")
class ExtensionRequestApprovedNotification extends ExtensionStudentNotification {
	def verb = "approve"
	def title = titlePrefix + "Your extension request for \"%s\" has been approved".format(assignment.name)
	def template = "/WEB-INF/freemarker/emails/extension_request_approved.ftl"
	def urlTitle = "view your extension"
}

@Entity
@DiscriminatorValue("ExtensionRequestRejected")
class ExtensionRequestRejectedNotification extends ExtensionStudentNotification {
	def verb = "reject"
	def title = titlePrefix + "Your extension request for \"%s\" has been rejected".format(assignment.name)
	def template = "/WEB-INF/freemarker/emails/extension_request_rejected.ftl"
	def urlTitle = "view the assignment deadline"
	priority = NotificationPriority.Warning
}