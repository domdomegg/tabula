package uk.ac.warwick.courses.commands.assignments

import scala.reflect.BeanProperty
import scala.collection.JavaConversions._
import org.hibernate.validator.constraints.Length
import org.hibernate.validator.constraints.NotEmpty
import org.joda.time.DateTime
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.validation.Errors
import javax.validation.constraints.Max
import javax.validation.constraints.Min
import uk.ac.warwick.courses.commands.Command
import uk.ac.warwick.courses.data.model.forms.CommentField
import uk.ac.warwick.courses.data.model.forms.FileField
import uk.ac.warwick.courses.data.model.Assignment
import uk.ac.warwick.courses.data.model.Module
import uk.ac.warwick.courses.helpers.ArrayList
import uk.ac.warwick.courses.services.AssignmentService
import uk.ac.warwick.courses.{UniversityId, AcademicYear, DateFormats}
import uk.ac.warwick.courses.data.model.UpstreamAssessmentGroup
import uk.ac.warwick.courses.data.model.UpstreamAssignment
import uk.ac.warwick.courses.data.model.UserGroup
import uk.ac.warwick.courses.services.UserLookupService

case class UpstreamGroupOption(
        assignmentId:String,
        name:String,
        cats:Option[String], // TODO joke about cats and string
        sequence:String,
        occurrence:String,
        memberCount:Int
    )
    


/**
 * Common behaviour 
 */
abstract class ModifyAssignmentCommand extends Command[Assignment]  {
	
	@Autowired var service:AssignmentService =_
	@Autowired var userLookup:UserLookupService =_
	
	def module:Module
	def assignment:Assignment
	
	@Length(max=200)
	@NotEmpty(message="{NotEmpty.assignmentName}")
	@BeanProperty var name:String = _
	
  @DateTimeFormat(pattern = DateFormats.DateTimePicker)
	@BeanProperty var openDate:DateTime = new DateTime().withTime(12,0,0,0)
	
  @DateTimeFormat(pattern = DateFormats.DateTimePicker)
	@BeanProperty var closeDate:DateTime = openDate.plusWeeks(2)
	
	@BeanProperty var academicYear:AcademicYear = AcademicYear.guessByDate(new DateTime)
	
	def getAcademicYearString = if (academicYear != null) academicYear.toString() else ""
	
	@BeanProperty var collectMarks:Boolean = _
	@BeanProperty var collectSubmissions:Boolean = _
	@BeanProperty var restrictSubmissions:Boolean = _
	@BeanProperty var allowLateSubmissions:Boolean = true
	@BeanProperty var allowResubmission:Boolean = false
	@BeanProperty var displayPlagiarismNotice:Boolean = _
	
	@Min(1) @Max(Assignment.MaximumFileAttachments)
	@BeanProperty var fileAttachmentLimit:Int = 1
	
	val maxFileAttachments:Int = 10
	val invalidAttatchmentPattern = """.*[\*\\/:\?"<>\|\%].*""";
	
	@BeanProperty var fileAttachmentTypes: JList[String] = ArrayList()



  // start complicated membership stuff

	/** linked SITS assignment. Optional. */
	@BeanProperty var upstreamAssignment: UpstreamAssignment =_

  /** If copying from existing Assigment, this must be a DEEP COPY
   * with changes copied back to the original UserGroup, don't pass
   * the same UserGroup around because it'll just cause Hibernate
   * problems. This copy should be transient.
   *
   * Changes to members are done via includeUsers and excludeUsers, since
   * it is difficult to bind additions and removals directly to a collection
   * with Spring binding.
   */
  @BeanProperty var members: UserGroup = new UserGroup

  // items added here are added to members.includeUsers.
  @BeanProperty var includeUsers: JList[String] = ArrayList()

  // items added here are either removed from members.includeUsers or added to members.excludeUsers.
  @BeanProperty var excludeUsers: JList[String] = ArrayList()

  // bind property for the big free-for-all textarea of usercodes/uniIDs to add.
  // These are first resolved to userIds and then added to includeUsers
  @BeanProperty var massAddUsers: String = _

  // parse massAddUsers into a collection of individual tokens
  def massAddUsersEntries: Seq[String] =
    if (massAddUsers == null) Nil
    else massAddUsers split ("\\s+") map ( _.trim ) filterNot ( _.isEmpty )

  ///// end of complicated membership stuff

  // can be set to false if that's not what you want.
  @BeanProperty var prefillFromRecent = true

	/** MAV_OCCURRENCE as per the value in SITS.  */
	@BeanProperty var occurrence: String = _
	
	/**
	 * This isn't actually a property on Assignment, it's one of the default fields added
	 * to all Assignments. When the forms become customisable this will be replaced with
	 * a full blown field editor. 
	 */
	@Length(max=2000)
	@BeanProperty var comment:String = _
	
	private var _prefilled:Boolean = _
	def prefilled = _prefilled
	
	def validate(errors:Errors) {
		service.getAssignmentByNameYearModule(name, academicYear, module)
			.filterNot{ _ eq assignment }
			.map{ a => errors.rejectValue("name", "name.duplicate.assignment", Array(name), "") }
		
		if (upstreamAssignment != null && !(upstreamAssignment.departmentCode equalsIgnoreCase module.department.code)) {
			errors.rejectValue("upstreamAssignment", "upstreamAssignment.notYours")
		}

		if (openDate.isAfter(closeDate)) {
			errors.reject("closeDate.early")
		}
		if(fileAttachmentTypes.mkString("").matches(invalidAttatchmentPattern)){
			errors.rejectValue("fileAttachmentTypes", "attachment.invalidChars")
		}
	}

  // Called by controller after Spring has done its basic binding, to do more complex
  // stuff like resolving users into groups and putting them in the members UserGroup.
	def afterBind() {

    def addUserId(item:String) {
      val user = userLookup.getUserByUserId(item)
      if (user.isFoundUser()) {
        includeUsers.add(user.getUserId)
      }
    }

    // parse items from textarea into includeUsers collection
    for (item <- massAddUsersEntries) {
      if (UniversityId.isValid(item)) {
        val user =  userLookup.getUserByWarwickUniId(item)
        if (user.isFoundUser()) {
          includeUsers.add(user.getUserId)
        } else {
          addUserId(item)
        }
      } else {
        addUserId(item)
      }
    }

    // add includeUsers to members.includeUsers
    (includeUsers map { _.trim } filterNot { _.isEmpty } distinct) foreach { userId =>
      members.addUser(userId)
    }
    // for excludeUsers, either remove from previously-added users or add to excluded users.
    (excludeUsers map { _.trim } filterNot { _.isEmpty } distinct) foreach { userId =>
      if (members.includeUsers contains userId) members.removeUser(userId)
      else members.excludeUser(userId)
    }

    // empty these out to make it clear that we've "moved" the data into members
    massAddUsers = ""
    includeUsers.clear()
    excludeUsers.clear()
	}
	
	def copyTo(assignment:Assignment) {
		assignment.name = name
    assignment.openDate = openDate
    assignment.closeDate = closeDate
    assignment.collectMarks = collectMarks
    assignment.academicYear = academicYear
    assignment.collectSubmissions = collectSubmissions
    assignment.restrictSubmissions = restrictSubmissions
    assignment.allowLateSubmissions = allowLateSubmissions
    assignment.allowResubmission = allowResubmission
    assignment.displayPlagiarismNotice = displayPlagiarismNotice
    assignment.upstreamAssignment = upstreamAssignment
    assignment.occurrence = occurrence
	    
    if (assignment.members == null) assignment.members = new UserGroup
	  assignment.members copyFrom members
	    	
	  findCommentField(assignment) foreach ( field => field.value = comment )
	    findFileField(assignment) foreach { file => 
	    	file.attachmentLimit = fileAttachmentLimit 
	    	file.attachmentTypes = fileAttachmentTypes
		}
	}
	
	def prefillFromRecentAssignment() {
    if (prefillFromRecent) {
      for (a <- service.recentAssignment(module.department)) {
        copyNonspecificFrom(a)
        _prefilled = true
      }
    }
	}
	
	
	/**
	 * Copy just the fields that it might be useful to
	 * prefill. The assignment passed in might typically be
	 * another recently created assignment, that may have good
	 * initial values for submission options.
	 */
	def copyNonspecificFrom(assignment:Assignment) {
		openDate = assignment.openDate
		closeDate = assignment.closeDate
		collectMarks = assignment.collectMarks
		collectSubmissions = assignment.collectSubmissions
		restrictSubmissions = assignment.restrictSubmissions
		allowLateSubmissions = assignment.allowLateSubmissions
		allowResubmission = assignment.allowResubmission
		displayPlagiarismNotice = assignment.displayPlagiarismNotice
		findCommentField(assignment) map ( field => comment = field.value )
		findFileField(assignment) map { field => 
			fileAttachmentLimit = field.attachmentLimit 
			fileAttachmentTypes = field.attachmentTypes
		}
	}
	
	def copyFrom(assignment:Assignment) {
		name = assignment.name
		academicYear = assignment.academicYear
		upstreamAssignment = assignment.upstreamAssignment
    occurrence = assignment.occurrence
    if (assignment.members != null) {
      members copyFrom assignment.members
    }
		copyNonspecificFrom(assignment)
	}
	
	
	/**
	 * Retrieve a list of possible upstream assignments and occurrences
	 * to link to SITS data. Includes the upstream assignment and the
	 * occurrence ID, plus some info like the number of members there.
	 */
	def upstreamGroupOptions: Seq[UpstreamGroupOption] = {
		val assignments = service.getUpstreamAssignments(module)
		assignments flatMap { assignment => 
			val groups = service.getAssessmentGroups(assignment, academicYear)
			groups map { group => 
                UpstreamGroupOption(
                    assignmentId = assignment.id,
                    name = assignment.name,
                    cats = assignment.cats,
                    sequence = assignment.sequence,
                    occurrence = group.occurrence,
                    memberCount = group.members.members.size
                )
            }
		}
	}
	
	/**
	 * Returns a sequence of MembershipItems
	 */
	def membershipDetails = 
		service.determineMembership(assessmentGroup, members)
	
	
	/**
	 * If upstream assignment, academic year and occurrence are all set,
	 * this attempts to return the matching SITS assessment group of people
	 * who should be studying this assignment.
	 */
	def assessmentGroup: Option[UpstreamAssessmentGroup] = {
		if (upstreamAssignment == null || academicYear == null || occurrence == null) {
			None
		} else {
			val template = new UpstreamAssessmentGroup
			template.academicYear = academicYear
			template.assessmentGroup = upstreamAssignment.assessmentGroup
			template.moduleCode = upstreamAssignment.moduleCode
			template.occurrence = occurrence
		    service.getAssessmentGroup(template)
		}
	}
	
	private def findFileField(assignment:Assignment) = 
		assignment.findFieldOfType[FileField](Assignment.defaultUploadName)
	
	/** Find the standard free-text field if it exists */
	private def findCommentField(assignment:Assignment) = 
		assignment.findFieldOfType[CommentField](Assignment.defaultCommentFieldName)
}