package uk.ac.warwick.courses.commands

import scala.collection.JavaConversions._
import scala.reflect.BeanProperty
import org.hibernate.annotations.AccessType
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Configurable
import org.springframework.beans.factory.annotation.Value
import org.springframework.mail.MailException
import org.springframework.mail.SimpleMailMessage
import org.springframework.transaction.annotation.Transactional
import org.springframework.validation.Errors
import javax.annotation.Resource
import javax.persistence.Entity
import uk.ac.warwick.courses.data.model.Assignment
import uk.ac.warwick.courses.data.model.Module
import uk.ac.warwick.courses.helpers.ArrayList
import uk.ac.warwick.courses.services.AssignmentService
import uk.ac.warwick.userlookup.User
import uk.ac.warwick.util.core.StringUtils
import uk.ac.warwick.util.mail.WarwickMailSender
import uk.ac.warwick.courses.helpers.FreemarkerRendering
import freemarker.template.Configuration

@Configurable
class PublishFeedbackCommand extends Command[Unit] with FreemarkerRendering {
  
	@Resource(name="studentMailSender") var studentMailSender:WarwickMailSender =_
	@Autowired var assignmentService:AssignmentService =_
	@Autowired implicit var freemarker:Configuration =_
	
	@BeanProperty var assignment:Assignment =_
	@BeanProperty var module:Module =_
	
	@BeanProperty var confirm:Boolean = false
	
	@Value("${toplevel.url}") var topLevelUrl:String = _
	
	@Value("${mail.noreply.to}") var replyAddress:String = _
	@Value("${mail.exceptions.to}") var fromAddress:String = 
  _
	
	case class MissingUser(val universityId:String)
	case class BadEmail(val user:User, val exception:Exception=null)
	
	var missingUsers: JList[MissingUser] = ArrayList()
	var badEmails: JList[BadEmail] = ArrayList()
	
	// validation done even when showing initial form.
	def prevalidate(errors:Errors) {
	  if (assignment.closeDate.isAfterNow()) {
	    errors.rejectValue("assignment","feedback.publish.notclosed")
	  } else if (assignment.feedbacks.isEmpty()) {
	    errors.rejectValue("assignment","feedback.publish.nofeedback")
	  }
	}
	
	def validate(errors:Errors) {
	  prevalidate(errors)
	  if (!confirm) {
	    errors.rejectValue("confirm","feedback.publish.confirm")
	  }
	}
	
	@Transactional
	def apply {
	  val users = assignmentService.getUsersForFeedback(assignment)
	  // note: after setting these to true, unreleasedFeedback will return empty.
	  for (feedback <- assignment.unreleasedFeedback) {
	 	  feedback.released = true
	  }
	  for (info <- users) email(info)
	}
	
	private def email(info:Pair[String,User]) {
	  val (id,user) = info
	  if (user.isFoundUser()) {
	    val email = user.getEmail
	    if (StringUtils.hasText(email)) {
	      val message = messageFor(user)
	      try {
	    	  studentMailSender.send(message)
	      } catch {
	     	  case e:MailException => badEmails add BadEmail(user, exception=e)
	      }
	    } else {
	      badEmails add BadEmail(user)
	    }
	  } else {
	    missingUsers add MissingUser(id)
	  }
	}
	
	private def messageFor(user:User): SimpleMailMessage = {
	  val message = new SimpleMailMessage
	  val moduleCode = assignment.module.code.toUpperCase
      message.setFrom(fromAddress)
      message.setReplyTo(replyAddress)
      message.setTo(user.getEmail)
      // TODO configurable subject
      message.setSubject(moduleCode + ": Your coursework feedback is ready")
      // TODO configurable body (or at least, FIXME Freemarker this up)
      message.setText(messageTextFor(user))
    	
      return message
	}
	
	def describe(d:Description) = d
		.assignment(assignment)
		.studentIds(assignment.feedbacks.map { _.universityId })
  
  def messageTextFor(user:User) = 
	  renderToString("/WEB-INF/freemarker/emails/feedbackready.ftl", Map(
    		  "name" -> user.getFirstName,
    		  "assignmentName" -> assignment.name,
    		  "moduleCode" -> assignment.module.code.toUpperCase,
    		  "moduleName" -> assignment.module.name,
    		  "url" -> ("%s/module/%s/%s" format (topLevelUrl, assignment.module.code, assignment.id))
      ))
	
}