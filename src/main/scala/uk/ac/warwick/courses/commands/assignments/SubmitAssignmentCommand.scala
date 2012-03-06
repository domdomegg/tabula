package uk.ac.warwick.courses.commands.assignments

import uk.ac.warwick.courses.commands._
import uk.ac.warwick.courses.data.model._
import org.springframework.beans.factory.annotation.Autowired
import uk.ac.warwick.courses.services.AssignmentService
import org.springframework.transaction.annotation.Transactional
import org.joda.time.DateTime
import uk.ac.warwick.courses.CurrentUser
import org.springframework.validation.Errors
import collection.JavaConversions._
import collection.JavaConverters._
import uk.ac.warwick.courses.JavaImports._
import scala.reflect.BeanProperty
import java.beans.PropertyEditorSupport
import uk.ac.warwick.util.web.bind.AbstractPropertyEditor
import uk.ac.warwick.courses.data.model.forms.SubmissionValue


class SubmittedFieldsPropertyEditor extends PropertyEditorSupport {
	
}

class SubmitAssignmentCommand(val assignment:Assignment, val user:CurrentUser) extends Command[Submission] {

  @Autowired var service:AssignmentService =_

  @BeanProperty var fields = buildEmptyFields
  
  // not important to command - only used to bind to request.
  @transient @BeanProperty var module:Module =_
  
  def onBind:Unit = for ((key, field) <- fields) field.onBind
  
  /**
   * Goes through the assignment's fields building a set of empty SubmissionValue
   * objects that can be attached to the form and used for binding form values.
   * The key is the form field's ID, so binding should be impervious to field reordering,
   * though it will fail if a field is removed between a user loading a submission form
   * and submitting it.
   */
  private def buildEmptyFields: JMap[String, SubmissionValue] = {
	  val pairs = assignment.fields.map { field => field.id -> field.blankSubmissionValue.asInstanceOf[SubmissionValue] }
	  Map(pairs:_*)
  }
  
  def validate(errors:Errors) {
	  if (!assignment.active) {
	 	  errors.reject("assignment.submit.inactive")
	  }
	  if (!assignment.isOpened()) {
	 	  errors.reject("assignment.submit.notopen")
	  }
	  if (!assignment.allowLateSubmissions && assignment.isClosed()) {
	 	  errors.reject("assignment.submit.closed")
	  }
	   
	  // Individually validate all the custom fields
	  // If a submitted ID is not found in assignment, it's ignored.
	  assignment.fields.foreach { field =>
	 	  errors.pushNestedPath("fields[%s]".format(field.id))
	 	  fields.asScala.get(field.id).map{ field.validate(_, errors) }
	 	  errors.popNestedPath()
	  }
	   
	  //FIXME obviously remove this ASAP
	  errors.reject("assignment.submit.notimplemented");
  }

  @Transactional
  override def apply = {
	val submission = new Submission
	submission.assignment = assignment
	submission.submitted = true
	submission.submittedDate = new DateTime
	submission.userId = user.apparentUser.getUserId
	submission.universityId = user.apparentUser.getWarwickId
	// FIXME add submission values! especially attachments!
	
	service.saveSubmission(submission)
	submission
  }

  override def describe(d: Description) = d.properties(
    
  )
  

}