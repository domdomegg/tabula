package uk.ac.warwick.tabula.coursework.commands.assignments

import uk.ac.warwick.tabula.commands.{Description, ReadOnly, Command}
import uk.ac.warwick.tabula.web.views.FreemarkerRendering
import uk.ac.warwick.tabula.helpers.UnicodeEmails
import uk.ac.warwick.tabula.permissions.Permissions
import uk.ac.warwick.tabula.data.model.{Assignment, Module}
import uk.ac.warwick.spring.Wire
import freemarker.template.Configuration
import org.joda.time.format.DateTimeFormat
import uk.ac.warwick.util.mail.WarwickMailSender
import uk.ac.warwick.tabula.helpers.StringUtils._
import uk.ac.warwick.tabula.coursework.web.Routes
import uk.ac.warwick.userlookup.User
import language.implicitConversions

class FeedbackChangeNotifyCommand(val module: Module, val assignment: Assignment, val recipient: User)
	extends Command[Boolean] with ReadOnly with FreemarkerRendering with UnicodeEmails {

	mustBeLinked(assignment, module)
	PermissionCheck(Permissions.Marks.Create, assignment)

	implicit var freemarker = Wire[Configuration]

	var studentMailSender = Wire[WarwickMailSender]("studentMailSender")
	var replyAddress = Wire[String]("${mail.noreply.to}")
	var fromAddress = Wire[String]("${mail.exceptions.to}")
	val dateFormatter = DateTimeFormat.forPattern("d MMMM yyyy 'at' HH:mm:ss")

	def applyInternal() = {
		if (recipient.getEmail.hasText) {
			(studentMailSender send messageFor(recipient))
			true
		} else {
			false
		}
	}

	def messageFor(user: User) = createMessage(studentMailSender) { message =>
		val moduleCode = module.code.toUpperCase
		message.setFrom(fromAddress)
		message.setReplyTo(replyAddress)
		message.setTo(user.getEmail)
		message.setSubject(encodeSubject(moduleCode + ": Feedback updated"))
		message.setText(renderToString("/WEB-INF/freemarker/emails/feedbackchanged.ftl", Map(
			"assignment" -> assignment,
			"module" -> module,
			"path" -> Routes.assignment.receipt(assignment)
		)))
	}

	override def describe(d: Description) {
		d.assignment(assignment)
	}
}
