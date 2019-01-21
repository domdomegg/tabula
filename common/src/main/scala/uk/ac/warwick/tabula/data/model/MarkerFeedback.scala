package uk.ac.warwick.tabula.data.model

import scala.collection.JavaConverters._
import org.joda.time.DateTime
import javax.persistence._
import org.hibernate.annotations.{BatchSize, Fetch, FetchMode, Type}
import javax.persistence.CascadeType._
import uk.ac.warwick.tabula.JavaImports._
import uk.ac.warwick.tabula.data.model.forms.{FormField, SavedFormValue}
import javax.persistence.Entity
import uk.ac.warwick.userlookup.User
import uk.ac.warwick.spring.Wire
import uk.ac.warwick.tabula.data.HibernateHelpers
import uk.ac.warwick.tabula.data.model.markingworkflow.MarkingWorkflowStage
import uk.ac.warwick.tabula.services.{ProfileService, UserLookupService}

import scala.collection.JavaConverters._

@Entity @Access(AccessType.FIELD)
class MarkerFeedback extends GeneratedId
	with FeedbackAttachments
	with ToEntityReference
	with CanBeDeleted
	with CM1MarkerFeedbackSupport {
	type Entity = MarkerFeedback

	def this(parent:Feedback){
		this()
		feedback = parent
	}

	@transient
	var userLookup: UserLookupService = Wire[UserLookupService]("userLookup")

	@transient
	var profileService: ProfileService = Wire[ProfileService]

	@ManyToOne(optional = false, fetch = FetchType.LAZY)
	@JoinColumn(name = "feedback_id", nullable = false)
	var feedback: Feedback = _

	@Column(name = "marker")
	private var markerUsercode: String = _

	def marker_=(marker: User): Unit = markerUsercode = marker match {
		case m: User if !m.isFoundUser => throw new IllegalStateException(s"${m.getUserId} is not a valid user.")
		case m: User => m.getUserId
		case _ => null
	}

	// returns an Anon user when the markerUsercode is null
	def marker: User = {
		val marker = userLookup.getUserByUserId(markerUsercode)
		if (markerUsercode != null && !marker.isFoundUser)
			dummyUser(ofUserType = MemberUserType.Other, userId = markerUsercode)
		else
			marker
	}

	// get user profile from tabula if not found from sso
	// or create a dummy user with the same usercode
	@transient
	def dummyUser(ofUserType: MemberUserType, userId: String): User = {
		val possibleMembers = profileService.getAllMembersWithUserId(
			userId = userId,
			disableFilter = true,
			activeOnly = false
		).distinct

		if (possibleMembers.isEmpty) {
			val dummyUser = new User
			dummyUser.setUserId(userId)
			dummyUser.setWarwickId(feedback.universityId.getOrElse("Unknown University ID"))
			dummyUser.setFirstName("[Unknown user]")
			dummyUser.setLastName("[Unknown user]")
			dummyUser.setFullName("[Unknown user]")
			dummyUser.setLoginDisabled(true)
			dummyUser.setUserType(ofUserType.description)
			dummyUser.setExtraProperties(JHashMap(
				"urn:websignon:usertype" -> dummyUser.getUserType,
				"urn:websignon:timestamp" -> DateTime.now.toString,
				"urn:websignon:usersource" -> "Tabula"
			))
			dummyUser.setVerified(true)
			dummyUser.setFoundUser(true)
			dummyUser
		} else {
			possibleMembers.head.asSsoUser
		}
	}

	def student: User = {
		val studentUsercode: String = feedback.usercode
		val student = userLookup.getUserByUserId(studentUsercode)
		if (!student.isFoundUser)
			dummyUser(MemberUserType.Student, studentUsercode)
		else
			student
	}

	@Basic
	@Type(`type` = "uk.ac.warwick.tabula.data.model.markingworkflow.MarkingWorkflowStageUserType")
	var stage: MarkingWorkflowStage = _

	@Column(name = "uploaded_date")
	var uploadedDate: DateTime = new DateTime

	// initially starts as null - if the marker makes any changes then this is set
	// allows us to distinguish between feedback that has been approved (copied from the previous stage) and feedback that has been modified
	@Column(name = "updated_on")
	var updatedOn: DateTime = _

	@Type(`type` = "uk.ac.warwick.tabula.data.model.OptionIntegerUserType")
	var mark: Option[Int] = None

	@Type(`type` = "uk.ac.warwick.tabula.data.model.OptionStringUserType")
	var grade: Option[String] = None

	@Deprecated
	@Type(`type` = "uk.ac.warwick.tabula.data.model.MarkingStateUserType")
	var state : MarkingState = _

	@Deprecated
	var rejectionComments: String = _

	@OneToMany(mappedBy = "markerFeedback", fetch = FetchType.LAZY, cascade = Array(ALL))
	@BatchSize(size=200)
	@Fetch(FetchMode.JOIN)
	var attachments: JSet[FileAttachment] = JHashSet()

	def addAttachment(attachment: FileAttachment) {
		if (attachment.isAttached) throw new IllegalArgumentException("File already attached to another object")
		attachment.temporary = false
		attachment.markerFeedback = this
		attachments.add(attachment)
	}

	@OneToMany(mappedBy = "markerFeedback", fetch = FetchType.LAZY, cascade = Array(ALL))
	@BatchSize(size=200)
	@Fetch(FetchMode.JOIN)
	var customFormValues: JSet[SavedFormValue] = JHashSet()

	def clearCustomFormValues(): Unit = {
		customFormValues.asScala.foreach { v =>
			v.markerFeedback = null
		}
		customFormValues.clear()
	}

	def getValue(field: FormField): Option[SavedFormValue] = {
		customFormValues.asScala.find( _.name == field.name )
	}

	def comments: Option[String] = customFormValues.asScala.find(_.name == Assignment.defaultFeedbackTextFieldName).map(_.value)
	def comments_=(value: String) {
		customFormValues.asScala
			.find(_.name == Assignment.defaultFeedbackTextFieldName)
			.getOrElse({
				val newValue = new SavedFormValue()
				newValue.name = Assignment.defaultFeedbackTextFieldName
				newValue.markerFeedback = this
				this.customFormValues.add(newValue)
				newValue
			}).value = value
	}


	def hasBeenModified: Boolean = updatedOn != null

	def hasContent: Boolean = hasMarkOrGrade || hasFeedbackOrComments

	def hasMarkOrGrade: Boolean = hasMark || hasGrade

	def hasMark: Boolean = mark.isDefined

	def hasGrade: Boolean = grade.isDefined

	def hasFeedbackOrComments: Boolean = hasFeedback || hasComments

	def hasFeedback: Boolean = attachments != null && attachments.size() > 0

	def hasComments: Boolean = customFormValues.asScala.exists(_.value != null)

	def readyForNextStage: Boolean = hasContent && outstanding

	def outstanding: Boolean = feedback.outstandingStages.contains(stage)

	def finalised: Boolean = hasContent && !outstanding
}

trait CM1MarkerFeedbackSupport {
	this: MarkerFeedback =>

	@Deprecated
	def getMarkerUser: Option[User] = getMarkerUsercode.map(userLookup.getUserByUserId)

	@Deprecated
	def getFeedbackPosition: FeedbackPosition = feedback.getFeedbackPosition(this)

	@Deprecated
	def getMarkerUsercode: Option[String] = {
		HibernateHelpers.initialiseAndUnproxy(feedback) match {
			case assignmentFeedback: AssignmentFeedback =>
				val student = feedback.usercode
				val assignment = assignmentFeedback.assignment
				Option(assignment.markingWorkflow).flatMap { workflow =>
					getFeedbackPosition match {
						case FirstFeedback => workflow.getStudentsFirstMarker(assignment, student)
						case SecondFeedback => workflow.getStudentsSecondMarker(assignment, student)
						case ThirdFeedback => workflow.getStudentsFirstMarker(assignment, student)
						case _ => None
					}
				}
			case _ => None
		}
	}
}
