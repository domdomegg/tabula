package uk.ac.warwick.tabula.data.model.forms

import scala.collection.JavaConversions._
import org.hibernate.annotations.{BatchSize, Type, AccessType}
import org.joda.time.{Days, DateTime}
import javax.persistence._
import javax.persistence.CascadeType._
import javax.persistence.FetchType._
import javax.validation.constraints.NotNull
import uk.ac.warwick.tabula.JavaImports._
import uk.ac.warwick.tabula.data.model._
import uk.ac.warwick.tabula.permissions._
import uk.ac.warwick.util.workingdays.WorkingDaysHelperImpl
import uk.ac.warwick.userlookup.User
import org.hibernate.`type`.StandardBasicTypes
import java.sql.Types
import uk.ac.warwick.tabula.DateFormats
import org.springframework.format.annotation.DateTimeFormat


@Entity @AccessType("field")
class Extension extends GeneratedId with PermissionsTarget with ToEntityReference {

	type Entity = Extension

	def this(universityId:String=null) {
		this()
		this.universityId = universityId
	}

	@ManyToOne(optional=false, cascade=Array(PERSIST,MERGE), fetch=FetchType.LAZY)
	@JoinColumn(name="assignment_id")
	var assignment:Assignment = _

	def permissionsParents = Option(assignment).toStream

	@NotNull
	var userId: String = _

	@NotNull
	var universityId: String = _

	def isForUser(user: User): Boolean = isForUser(user.getWarwickId, user.getUserId)
	def isForUser(theUniversityId: String, theUsercode: String): Boolean = universityId == theUniversityId || userId == theUsercode

	// TODO should there be a single def that returns the expiry date for approved/manual extensions, and requested expiry date otherwise?
	@Type(`type` = "org.jadira.usertype.dateandtime.joda.PersistentDateTime")
	@DateTimeFormat(pattern = DateFormats.DateTimePicker)
	var requestedExpiryDate: DateTime = _
	@Type(`type` = "org.jadira.usertype.dateandtime.joda.PersistentDateTime")
	var expiryDate: DateTime = _
	@Type(`type` = "org.jadira.usertype.dateandtime.joda.PersistentDateTime")
	var requestedOn: DateTime = _
	@Type(`type` = "org.jadira.usertype.dateandtime.joda.PersistentDateTime")
	@Column(name = "approvedOn")
	var reviewedOn: DateTime = _

	var reason: String = _
	@Column(name = "approvalComments")
	var reviewerComments: String = _
	var disabilityAdjustment: Boolean = false

	@Column(name = "state")
	@Type(`type` = "uk.ac.warwick.tabula.data.model.forms.ExtensionStateUserType")
	var _state: ExtensionState = ExtensionState.Unreviewed
	def state = _state
	/** Don't use rawState_ directly, call approve() or reject() instead **/
	def rawState_= (state: ExtensionState) { _state = state }


	@OneToMany(mappedBy = "extension", fetch = LAZY, cascade = Array(ALL))
	@BatchSize(size = 200)
	var attachments: JSet[FileAttachment] = JSet()

	def nonEmptyAttachments = attachments.toSeq filter(_.hasData)

	def addAttachment(attachment: FileAttachment) {
		if (attachment.isAttached) throw new IllegalArgumentException("File already attached to another object")
		attachment.temporary = false
		attachment.extension = this
		attachments.add(attachment)
	}

	// this extension was manually created by an administrator, rather than requested by a student
	def isManual = requestedOn == null
	def isInitiatedByStudent = !isManual

	// you can't infer from state alone whether there's a request outstanding - use awaitingReview()
	def approved = state == ExtensionState.Approved
	def rejected = state == ExtensionState.Rejected
	def unreviewed = state == ExtensionState.Unreviewed
	def revoked = state == ExtensionState.Revoked

	def rejectable = awaitingReview || (approved && isInitiatedByStudent)
	def revocable = approved && !isInitiatedByStudent

	// keep state encapsulated
	def approve(comments: String = null) {
		_state = ExtensionState.Approved
		reviewedOn = DateTime.now
		reviewerComments = comments
	}

	// keep state encapsulated
	def reject(comments: String = null) {
		_state = ExtensionState.Rejected
		reviewedOn = DateTime.now
		reviewerComments = comments
	}

	def awaitingReview = {
		// wrap nullable dates to be more readable in pattern match
		val requestDate = Option(requestedOn)
		val reviewDate = Option(reviewedOn)

		(requestDate, reviewDate) match {
			case (Some(request), None) => true
			case (Some(latestRequest), Some(lastReview)) if latestRequest.isAfter(lastReview) => true
			case _ => false
		}
	}

	@transient
	lazy val workingDaysHelper = new WorkingDaysHelperImpl

	// feedback deadline taking the extension into account
	def feedbackDeadline = workingDaysHelper.datePlusWorkingDays(expiryDate.toLocalDate, Feedback.PublishDeadlineInWorkingDays).toDateTime(expiryDate)

	def toEntityReference = new ExtensionEntityReference().put(this)

	def duration = if (expiryDate != null) Days.daysBetween(assignment.closeDate, expiryDate).getDays else 0

	def requestedExtraDuration = {
		if (requestedExpiryDate != null && expiryDate != null)
			Days.daysBetween(expiryDate, requestedExpiryDate).getDays

		else if (requestedExpiryDate != null && expiryDate == null)
			Days.daysBetween(assignment.closeDate, requestedExpiryDate).getDays

		else 0
	}
}


object Extension {
	val MaxDaysToDisplayAsProgressBar = 8 * 7
}


sealed abstract class ExtensionState(val dbValue: String, val description: String)

object ExtensionState {
	// you can't infer from state alone whether there's a request outstanding - use extension.awaitingReview()
	case object Unreviewed extends ExtensionState("U", "Unreviewed")
	case object Approved extends ExtensionState("A", "Approved")
	case object Rejected extends ExtensionState("R", "Rejected")
	case object Revoked extends ExtensionState("V", "Revoked")

	def fromCode(code: String) = code match {
		case Unreviewed.dbValue => Unreviewed
		case Approved.dbValue => Approved
		case Rejected.dbValue => Rejected
		case Revoked.dbValue => Revoked
		case _ => throw new IllegalArgumentException()
	}
}

class ExtensionStateUserType extends AbstractBasicUserType[ExtensionState, String] {
	val basicType = StandardBasicTypes.STRING
	override def sqlTypes = Array(Types.VARCHAR)

	val nullValue = null
	val nullObject = null

	override def convertToObject(string: String) = ExtensionState.fromCode(string)
	override def convertToValue(es: ExtensionState) = es.dbValue
}