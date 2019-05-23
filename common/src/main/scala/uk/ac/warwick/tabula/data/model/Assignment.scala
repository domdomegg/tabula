package uk.ac.warwick.tabula.data.model

import com.google.common.annotations.VisibleForTesting
import javax.persistence.CascadeType._
import javax.persistence.FetchType._
import javax.persistence._
import org.hibernate.annotations.{BatchSize, Filter, FilterDef, Type}
import org.joda.time.{DateTime, LocalDate}
import uk.ac.warwick.spring.Wire
import uk.ac.warwick.tabula.JavaImports._
import uk.ac.warwick.tabula.commands.cm2.assignments.extensions.ExtensionPersistenceComponent
import uk.ac.warwick.tabula.data.{HibernateHelpers, PostLoadBehaviour}
import uk.ac.warwick.tabula.data.model.AssignmentAnonymity.{IDOnly, NameAndID}
import uk.ac.warwick.tabula.data.model.forms.{WordCountField, _}
import uk.ac.warwick.tabula.data.model.markingworkflow.{CM2MarkingWorkflow, ModeratedWorkflow}
import uk.ac.warwick.tabula.helpers.DateTimeOrdering._
import uk.ac.warwick.tabula.helpers.JodaConverters._
import uk.ac.warwick.tabula.helpers.RequestLevelCache
import uk.ac.warwick.tabula.services._
import uk.ac.warwick.tabula.{AcademicYear, ToString}
import uk.ac.warwick.userlookup.User
import uk.ac.warwick.util.workingdays.WorkingDaysHelperImpl

import scala.beans.BeanProperty
import scala.collection.JavaConverters._
import scala.reflect._

object Assignment {
  // don't use the same name in different contexts, as that will kill find methods
  val defaultCommentFieldName = "pretext"
  val defaultUploadName = "upload"
  val defaultFeedbackTextFieldName = "feedbackText"
  val defaultNotesFieldName = "notes"
  val defaultMarkerSelectorName = "marker"
  val defaultWordCountName = "wordcount"
  final val NotDeletedFilter = "notDeleted"
  final val MaximumFileAttachments = 50
  final val MaximumWordCount = 1000000

  case class MarkerAllocation(role: String, description: String, marker: User, students: Set[User])

  object Settings {

    object InfoViewType {
      val Default = "default"
      val Table = "table"
      val Summary = "summary"
    }

    val IncludeInFeedbackReportWithoutSubmissions = "includeInFeedbackReportWithoutSubmissions"
    val AutomaticallyReleaseToMarkers = "automaticallyReleaseToMarkers"
    val AutomaticallySubmitToTurnitin = "automaticallySubmitToTurnitin"
    val ExtensionAttachmentMandatory = "extensionAttachmentMandatory"
    val AllowExtensionsAfterCloseDate = "allowExtensionsAfterCloseDate"
    val TurnitinLtiNotifyUsers = "turnitinLtiNotifyUsers"
    val TurnitinLtiClassWithAcademicYear = "turnitinLtiClassWithAcademicYear"
    val TurnitinStoreInRepository = "turnitinStoreInRepository"
    val TurnitinExcludeBibliography = "turnitinExcludeBibliography"
    val TurnitinExcludeQuoted = "turnitinExcludeQuoted"
    val PublishFeedback = "publishFeedback"
    val UseMarkPoints = "useMarkPoints"
  }

}

/**
  * Represents an assignment within a module, occurring at a certain time.
  *
  * Notes about the notDeleted filter:
  * filters don't run on session.get() but getById will check for you.
  * queries will only include it if it's the entity after "from" and not
  * some other secondary entity joined on. It's usually possible to flip the
  * query around to make this work.
  */
@FilterDef(name = Assignment.NotDeletedFilter, defaultCondition = "deleted = false")
@Filter(name = Assignment.NotDeletedFilter)
@Entity
@Access(AccessType.FIELD)
class Assignment
  extends Assessment
    with ToString
    with HasSettings
    with PostLoadBehaviour
    with Serializable
    with ToEntityReference
    with FormattedHtml {

  import uk.ac.warwick.tabula.data.model.Assignment._

  type Entity = Assignment

  @transient
  var assignmentService: AssessmentService = Wire[AssessmentService]("assignmentService")

  @transient
  var submissionService: SubmissionService = Wire[SubmissionService]("submissionService")

  @transient
  var feedbackService: FeedbackService = Wire[FeedbackService]("feedbackService")

  @transient
  var extensionService: ExtensionService = Wire[ExtensionService]("extensionService")

  @transient
  var userLookup: UserLookupService = Wire[UserLookupService]("userLookup")

  @transient
  var markingDescriptorService: MarkingDescriptorService = Wire[MarkingDescriptorService]("markingDescriptorService")

  def this(_module: Module) {
    this()
    this.module = _module
  }

  @Basic
  @Type(`type` = "uk.ac.warwick.tabula.data.model.AcademicYearUserType")
  @Column(nullable = false)
  override var academicYear: AcademicYear = AcademicYear.now()

  @Type(`type` = "uk.ac.warwick.tabula.data.model.StringListUserType")
  var fileExtensions: Seq[String] = _

  var attachmentLimit: Int = 1

  override var name: String = _

  override def humanReadableId: String = s"$name (${module.code.toUpperCase})"

  @Column(name = "archived")
  @deprecated("118", "Archived will be removed in future in preference to academic year scoping")
  private var _archived: JBoolean = false

  def archived = Option(_archived).getOrElse(false)

  def archive(): Unit = {
    _archived = true
  }

  def unarchive(): Unit = {
    _archived = false
  }

  @Column(name = "hidden_from_students")
  private var _hiddenFromStudents: JBoolean = false

  def hideFromStudents(): Unit = {
    _hiddenFromStudents = true
  }

  var openDate: DateTime = _
  var closeDate: DateTime = _
  var openEndedReminderDate: DateTime = _
  var createdDate: DateTime = DateTime.now()

  // left unset as defaults are set by BooleanAssignmentProperties
  var openEnded: JBoolean = _
  var collectMarks: JBoolean = _
  var collectSubmissions: JBoolean = _
  var restrictSubmissions: JBoolean = _
  var allowLateSubmissions: JBoolean = _
  var allowResubmission: JBoolean = _
  var displayPlagiarismNotice: JBoolean = _
  var summative: JBoolean = _
  var dissertation: JBoolean = _
  var allowExtensions: JBoolean = _

  @Column(name = "anonymous_marking_method")
  @Type(`type` = "uk.ac.warwick.tabula.data.model.AssignmentAnonymityUserType")
  var _anonymity: AssignmentAnonymity = _

  // if the assignment doesn't have a setting use the department default
  def anonymity: AssignmentAnonymity = if (_anonymity != null) _anonymity else {
    if (module.adminDepartment.showStudentName) NameAndID else IDOnly
  }

  def anonymity_=(anonymity: AssignmentAnonymity) {
    _anonymity = anonymity
  }

  var cm2Assignment: JBoolean = false

  var genericFeedback: String = _

  def genericFeedbackFormattedHtml: String = formattedHtml(genericFeedback)

  @Column(name = "turnitin_id")
  var turnitinId: String = _
  var submitToTurnitin: JBoolean = false
  var lastSubmittedToTurnitin: DateTime = _
  var submitToTurnitinRetries: JInteger = 0

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "module_id")
  override var module: Module = _

  override def permissionsParents: Stream[Module] = Option(module).toStream

  @OneToMany(mappedBy = "assignment", fetch = FetchType.LAZY, cascade = Array(CascadeType.ALL), orphanRemoval = true)
  @BatchSize(size = 200)
  override var assessmentGroups: JList[AssessmentGroup] = JArrayList()

  @OneToMany(mappedBy = "assignment", fetch = LAZY, cascade = Array(ALL))
  @OrderBy("submittedDate")
  @BatchSize(size = 200)
  var submissions: JList[Submission] = JArrayList()

  def submissionsFromUnenrolledStudents: JList[Submission] = {
    val enrolledUsercodes = membershipInfo.usercodes
    submissions.asScala.filterNot(sub => enrolledUsercodes.contains(sub.usercode)).asJava
  }

  @OneToMany(mappedBy = "assignment", fetch = LAZY, cascade = Array(ALL))
  @BatchSize(size = 200)
  @VisibleForTesting
  val _extensions: JList[Extension] = JArrayList()

  @OneToMany(mappedBy = "assignment", fetch = LAZY, cascade = Array(ALL))
  @BatchSize(size = 200)
  var feedbacks: JList[AssignmentFeedback] = JArrayList()

  override def allFeedback: Seq[AssignmentFeedback] = RequestLevelCache.cachedBy("Assignment.allFeedback", id) {
    feedbackService.loadFeedbackForAssignment(this)
  }

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "feedback_template_id")
  @BatchSize(size = 200)
  var feedbackTemplate: FeedbackTemplate = _

  def includeInFeedbackReportWithoutSubmissions: Boolean = getBooleanSetting(Settings.IncludeInFeedbackReportWithoutSubmissions, default = false)

  def includeInFeedbackReportWithoutSubmissions_=(include: Boolean): Unit = settings += (Settings.IncludeInFeedbackReportWithoutSubmissions -> include)

  def publishFeedback: Boolean = getBooleanSetting(Settings.PublishFeedback, default = true)

  def publishFeedback_=(publish: Boolean): Unit = settings += (Settings.PublishFeedback -> publish)

  def useMarkPoints: Boolean = getBooleanSetting(Settings.UseMarkPoints, default = false)

  def useMarkPoints_=(markPoints: Boolean): Unit = settings += (Settings.UseMarkPoints -> markPoints)

  def availableMarkPoints: Seq[MarkPoint] = if (useMarkPoints) MarkPoint.all else Nil

  def availableMarkingDescriptors: Seq[MarkingDescriptor] = markingDescriptorService.getMarkingDescriptors(module.adminDepartment)

  def hasFeedbackTemplate: Boolean = feedbackTemplate != null

  @transient
  lazy val workingDaysHelper = new WorkingDaysHelperImpl

  /**
    * deadline based on assignmentclose date logic if there are no extensions OR no approved extensions OR any submission exists without extension OR
    * 0 submissions along with some students with unapproved extensions.
    * deadline based on extension expiry date logic if none of the above AND (there are submissions within extension deadline OR if there are approved extensions  for all students)
    *
    */
  def feedbackDeadline: Option[LocalDate] =
    if (openEnded || dissertation || !publishFeedback) {
      None
    } else if (!hasExtensions || approvedExtensions.isEmpty || submissions.asScala.exists(s => !approvedExtensions.contains(s.usercode)) || !doesAllMembersHaveApprovedExtensions) {
      Option(workingDaysHelper.datePlusWorkingDays(closeDate.toLocalDate.asJava, Feedback.PublishDeadlineInWorkingDays)).map(_.asJoda)
    } else if (approvedExtensions.values.exists(_.feedbackDeadline.nonEmpty)) {
      Option(approvedExtensions.values.flatMap(_.feedbackDeadline).map(_.toLocalDate).min)
    } else if (submissions.size() == 0 && doesAllMembersHaveApprovedExtensions) {
      Option(workingDaysHelper.datePlusWorkingDays(approvedExtensions.values.flatMap(_.expiryDate).min.toLocalDate.asJava, Feedback.PublishDeadlineInWorkingDays)).map(_.asJoda)
    } else None

  private def doesAllMembersHaveApprovedExtensions: Boolean =
    assessmentMembershipService.determineMembershipUsers(upstreamAssessmentGroupInfos, Option(members))
      .forall(user => approvedExtensions.contains(user.getUserId))

  def feedbackDeadlineForSubmission(submission: Submission): Option[LocalDate] = feedbackDeadline.flatMap { wholeAssignmentDeadline =>
    // If we have an extension, use the extension's expiry date
    val extension = approvedExtensions.get(submission.usercode)

    val baseFeedbackDeadline =
      extension.flatMap(_.feedbackDeadline).map(_.toLocalDate).getOrElse(wholeAssignmentDeadline)

    // allow 20 days from the submission day only for submissions that aren't late or suspected plagiarised.
    // Late or possibly plagiarised submissions are excluded from this calculation
    if (submission.hasPlagiarismInvestigation || submission.isLate)
      None
    else
      Some(baseFeedbackDeadline)
  }

  private def workingDaysAway(date: LocalDate) = {
    val now = LocalDate.now

    // need an offset, as the helper always includes both start and end date, off-by-one from what we want to show
    val offset =
      if (date.isBefore(now)) 1
      else -1 // today or in the future

    workingDaysHelper.getNumWorkingDays(now.asJava, date.asJava) + offset
  }

  def feedbackDeadlineWorkingDaysAway: Option[Int] = feedbackDeadline.map(workingDaysAway)

  /**
    * An Assignment has outstanding feedback if it has a submission that isn't feedback deadline-exempt
    * and there isn't released feedback for that submission.
    */
  def hasOutstandingFeedback: Boolean =
    submissionService.getSubmissionsByAssignment(this)
      .exists { submission =>
        submission.feedbackDeadline.nonEmpty &&
          !feedbackService.getAssignmentFeedbackByUsercode(this, submission.usercode).exists(_.released)
      }

  // if any feedback exists that has outstanding stages marking has begun (when marking is finished there is a completed stage)
  override def isReleasedForMarking: Boolean = allFeedback.exists(_.outstandingStages.asScala.nonEmpty)

  // sort order is unpredictable on retrieval from Hibernate; use indexed defs below for access
  @OneToMany(mappedBy = "assignment", fetch = LAZY, cascade = Array(ALL))
  @BatchSize(size = 200)
  var fields: JList[AssignmentFormField] = JArrayList()

  // IndexColumn is a busted flush for fields because of reuse of non-uniqueness.
  // Use manual position management on add/removeFields, and in these getters
  def submissionFields: Seq[AssignmentFormField] = fields.asScala.filter(_.context == FormFieldContext.Submission).sortBy(_.position)

  def feedbackFields: Seq[AssignmentFormField] = fields.asScala.filter(_.context == FormFieldContext.Feedback).sortBy(_.position)

  @OneToOne(cascade = Array(ALL), fetch = FetchType.LAZY)
  @JoinColumn(name = "membersgroup_id")
  private var _members: UserGroup = UserGroup.ofUsercodes

  def members: UnspecifiedTypeUserGroup = {
    Option(_members).map {
      new UserGroupCacheManager(_, assessmentMembershipService.assignmentManualMembershipHelper)
    }.orNull
  }

  def members_=(group: UserGroup) {
    _members = group
  }

  // TAB-1446 If hibernate sets members to null, make a new empty usergroup
  override def postLoad() {
    ensureMembersGroup
    ensureSettings
  }

  def ensureMembersGroup: UserGroup = {
    if (_members == null) _members = UserGroup.ofUsercodes
    _members
  }

  @ManyToOne(fetch = LAZY)
  @JoinColumn(name = "markscheme_id")
  var markingWorkflow: MarkingWorkflow = _

  @ManyToOne(fetch = LAZY)
  @JoinColumn(name = "cm2_workflow_id")
  var cm2MarkingWorkflow: CM2MarkingWorkflow = _

  @Column(name = "workflow_category")
  @Type(`type` = "uk.ac.warwick.tabula.data.model.WorkflowCategoryUserType")
  private var _workflowCategory: WorkflowCategory = _

  def workflowCategory: Option[WorkflowCategory] = Option(_workflowCategory)

  def workflowCategory_=(workflowCategoryOption: Option[WorkflowCategory]): Unit = {
    _workflowCategory = workflowCategoryOption.orNull
  }

  def hasCM2Workflow: Boolean = cm2MarkingWorkflow != null

  def hasModeration: Boolean = Option(HibernateHelpers.initialiseAndUnproxy(cm2MarkingWorkflow)).exists(_.isInstanceOf[ModeratedWorkflow])

  @OneToMany(mappedBy = "assignment", fetch = LAZY, cascade = Array(ALL), orphanRemoval = true)
  @BatchSize(size = 200)
  var firstMarkers: JList[FirstMarkersMap] = JArrayList()

  @OneToMany(mappedBy = "assignment", fetch = LAZY, cascade = Array(ALL), orphanRemoval = true)
  @BatchSize(size = 200)
  var secondMarkers: JList[SecondMarkersMap] = JArrayList()

  def setAllFileTypesAllowed() {
    fileExtensions = Nil
  }

  /**
    * Before we allow customising of assignment submission forms, we just want the basic
    * fields to allow you to attach a file and display some instructions.
    */
  def addDefaultSubmissionFields() {
    val pretext = new CommentField
    pretext.name = defaultCommentFieldName
    pretext.value = ""
    pretext.context = FormFieldContext.Submission

    val file = new FileField
    file.name = defaultUploadName
    file.context = FormFieldContext.Submission

    addFields(pretext, file)
  }

  /**
    * Before we allow customising of assignment feedback forms, we just want the basic
    * fields to allow you to enter a comment.
    */
  override def addDefaultFeedbackFields() {
    val feedback = new TextField
    feedback.name = defaultFeedbackTextFieldName
    feedback.label = "Feedback"
    feedback.value = ""
    feedback.context = FormFieldContext.Feedback

    addField(feedback)

    val notes = new TextField
    notes.name = defaultNotesFieldName
    notes.label = "Notes"
    notes.value = ""
    notes.context = FormFieldContext.Feedback

    addField(notes)
  }

  override def addDefaultFields() {
    addDefaultSubmissionFields()
    addDefaultFeedbackFields()
  }

  def setDefaultBooleanProperties() {
    BooleanAssignmentProperties(this)
  }

  /**
    * Returns whether we're between the opening and closing dates
    */
  def isBetweenDates(now: DateTime = new DateTime): Boolean =
    isOpened(now) && !isClosed(now)

  def isOpened(now: DateTime): Boolean = now.isAfter(openDate)

  def isOpened: Boolean = isOpened(new DateTime)

  /**
    * Whether it's after the close date. Depending on the assignment
    * we might still be allowing submissions, though.
    */
  def isClosed(now: DateTime): Boolean = !openEnded && now.isAfter(closeDate)

  def isClosed: Boolean = isClosed(new DateTime)

  /**
    * True if the specified user has been granted an extension and that extension has not expired on the specified date
    */
  def isWithinExtension(user: User, time: DateTime): Boolean = isWithinExtension(user.getUserId, time)

  def isWithinExtension(usercode: String, time: DateTime): Boolean =
    approvedExtensions.get(usercode).exists(_.expiryDate.exists(_.isAfter(time)))

  /**
    * True if the specified user has been granted an extension and that extension has not expired now
    */
  def isWithinExtension(user: User): Boolean = isWithinExtension(user, new DateTime)

  def isWithinExtension(usercode: String): Boolean = isWithinExtension(usercode, new DateTime)

  /**
    * retrospectively checks if a submission was late. called by submission.isLate to check against extensions
    */
  def isLate(submission: Submission): Boolean =
    !openEnded && closeDate.isBefore(submission.submittedDate) && !isWithinExtension(submission.usercode, submission.submittedDate)

  def lateSubmissionCount: Int = submissions.asScala.count(submission => isLate(submission))

  /**
    * Deadline taking into account any approved extension
    */
  def submissionDeadline(usercode: String): DateTime =
    if (openEnded) null
    else approvedExtensions.get(usercode).flatMap(_.expiryDate).getOrElse(closeDate)

  def submissionDeadline(user: User): DateTime = submissionDeadline(user.getUserId)

  def submissionDeadline(submission: Submission): DateTime = submissionDeadline(submission.usercode)

  def workingDaysLate(submission: Submission): Int =
    if (isLate(submission)) {
      val deadline = submissionDeadline(submission)

      val offset =
        if (deadline.toLocalTime.isAfter(submission.submittedDate.toLocalTime)) -1
        else 0

      val daysLate = workingDaysHelper.getNumWorkingDays(deadline.toLocalDate.asJava, submission.submittedDate.toLocalDate.asJava) + offset
      val lateDay = workingDaysHelper.datePlusWorkingDays(deadline.toLocalDate.asJava, daysLate)

      if (lateDay.isBefore(submission.submittedDate.toLocalDate.asJava)) daysLate + 1
      else daysLate
    } else 0

  def workingDaysLateIfSubmittedNow(usercode: String): Int = {
    val deadline = submissionDeadline(usercode)

    val offset =
      if (deadline.toLocalTime.isAfter(DateTime.now.toLocalTime)) -1
      else 0

    val daysLate = workingDaysHelper.getNumWorkingDays(deadline.toLocalDate.asJava, DateTime.now.toLocalDate.asJava) + offset
    val lateDay = workingDaysHelper.datePlusWorkingDays(deadline.toLocalDate.asJava, daysLate)

    if (lateDay.isBefore(DateTime.now.toLocalDate.asJava)) daysLate + 1
    else daysLate
  }

  /**
    * retrospectively checks if a submission was an 'authorised late'
    * called by submission.isAuthorisedLate to check against extensions
    */
  def isAuthorisedLate(submission: Submission): Boolean =
    !openEnded && closeDate.isBefore(submission.submittedDate) && isWithinExtension(submission.usercode, submission.submittedDate)

  /**
    * Whether the assignment is not archived or deleted.
    */
  def isAlive: Boolean = !deleted && !_archived

  /**
    * Whether this assignment should be visible to students
    */
  def isVisibleToStudents: Boolean = isAlive && !_hiddenFromStudents

  /**
    * Whether this assignment should be visible to students historically (this allows archived assignments)
    */
  def isVisibleToStudentsHistoric: Boolean = !deleted && !_hiddenFromStudents

  /**
    * Calculates whether we could submit to this assignment.
    */
  def submittable(user: User): Boolean = isAlive && collectSubmissions && isOpened && (allowLateSubmissions || !isClosed || isWithinExtension(user))

  /**
    * Calculates whether we could re-submit to this assignment (assuming that the current
    * student has already submitted).
    */
  def resubmittable(user: User): Boolean = submittable(user) && allowResubmission && (!isClosed || isWithinExtension(user))

  def mostRecentFeedbackUpload: DateTime = feedbacks.asScala.maxBy {
    _.updatedDate
  }.updatedDate

  def addField(field: AssignmentFormField) {
    if (field.context == null)
      throw new IllegalArgumentException("Field with name " + field.name + " has no context specified")
    if (fields.asScala.exists(_.name == field.name)) throw new IllegalArgumentException("Field with name " + field.name + " already exists")
    field.assignment = this
    field.position = fields.asScala.count(_.context == field.context)
    fields.add(field)
  }

  def removeField(field: FormField) {
    fields.remove(field)
    assignmentService.deleteFormField(field)
    // manually update all fields in the context to reflect their new positions
    fields.asScala.filter(_.context == field.context).zipWithIndex foreach {
      case (f, index) => f.position = index
    }
  }

  def attachmentField: Option[FileField] = findFieldOfType[FileField](Assignment.defaultUploadName)

  def commentField: Option[CommentField] = findFieldOfType[CommentField](Assignment.defaultCommentFieldName)

  def markerSelectField: Option[MarkerSelectField] =
    findFieldOfType[MarkerSelectField](Assignment.defaultMarkerSelectorName)

  def wordCountField: Option[WordCountField] = findFieldOfType[WordCountField](Assignment.defaultWordCountName)

  def feedbackCommentsField: Option[TextField] = findFieldOfType[TextField](Assignment.defaultFeedbackTextFieldName)

  /**
    * Find a FormField on the Assignment with the given name.
    */
  def findField(name: String): Option[FormField] = fields.asScala.find(_.name == name)

  /**
    * Find a FormField on the Assignment with the given name and type.
    * A field with a matching name but not a matching type is ignored.
    */
  def findFieldOfType[A <: FormField : ClassTag](name: String): Option[A] =
    findField(name) match {
      case Some(field) if classTag[A].runtimeClass.isInstance(field) => Some(field.asInstanceOf[A])
      case _ => None
    }


  def countFullFeedback: Int = fullFeedback.size

  def hasFullFeedback: Boolean = countFullFeedback > 0

  /**
    * Returns a filtered copy of the feedbacks that haven't yet been published.
    * If the old-style assignment-wide published flag is true, then it
    * assumes all feedback has already been published.
    */
  // scalastyle:off
  def unreleasedFeedback: Seq[Feedback] = fullFeedback.filterNot(_.released == true) // ==true because can be null

  // safer to use in overview pages like the department homepage as does not require the feedback list to be inflated
  def countReleasedFeedback: Int = feedbackService.countPublishedFeedback(this)

  def countUnreleasedFeedback: Int = countFullFeedback - countReleasedFeedback

  def hasReleasedFeedback: Boolean = countReleasedFeedback > 0

  def hasUnreleasedFeedback: Boolean = countReleasedFeedback < countFullFeedback

  private class ExtensionInfo {
    lazy val all: Map[String, Seq[Extension]] = extensionService.getAllExtensionsByUserId(Assignment.this)
    lazy val approved: Map[String, Extension] = extensionService.getApprovedExtensionsByUserId(Assignment.this)
    lazy val count: Int = extensionService.countExtensions(Assignment.this)
    lazy val has: Boolean = extensionService.hasExtensions(Assignment.this)
    lazy val countUnapproved: Int = extensionService.countUnapprovedExtensions(Assignment.this)
    lazy val hasUnapproved: Boolean = extensionService.hasUnapprovedExtensions(Assignment.this)
    lazy val getUnapproved: Seq[Extension] = extensionService.getUnapprovedExtensions(Assignment.this)
    lazy val countByStatus: Map[ExtensionState, Int] = extensionService.countExtensionsByState(Assignment.this)
  }
  @transient private var extensionInfo = new ExtensionInfo

  def allExtensions: Map[String, Seq[Extension]] = extensionInfo.all

  /**
    * Get all approved extensions for this assignment, using the latest extended deadline for where a student has multiple.
    */
  def approvedExtensions: Map[String, Extension] = extensionInfo.approved

  /**
    * Get the latest requested extension, if there is one, per-user, falling back to an approved extension if there isn't one
    */
  def requestedOrApprovedExtensions: Map[String, Extension] =
    allExtensions.mapValues { extensions =>
      if (extensions.size == 1) extensions.head
      else extensions.find(_.awaitingReview).getOrElse(extensions.maxBy(_.requestedOn))
    }

  /**
    * Gets the currently in-review request
    */
  def currentExtensionRequests: Map[String, Extension] =
    allExtensions.toSeq.flatMap { case (usercode, extensions) =>
      val extension = extensions.find { e =>
        e.state == ExtensionState.Unreviewed ||
        e.state == ExtensionState.MoreInformationReceived ||
        e.state == ExtensionState.MoreInformationRequired
      }
      extension.map(usercode -> _)
    }.toMap

  def countExtensions: Int = extensionInfo.count
  def hasExtensions: Boolean = extensionInfo.has
  def countUnapprovedExtensions: Int = extensionInfo.countUnapproved
  def hasUnapprovedExtensions: Boolean = extensionInfo.hasUnapproved
  def getUnapprovedExtensions: Seq[Extension] = extensionInfo.getUnapproved
  def extensionCountByStatus: Map[ExtensionState, Int] = extensionInfo.countByStatus

  def addFields(fieldz: AssignmentFormField*): Unit = for (field <- fieldz) addField(field)

  def addFeedback(feedback: AssignmentFeedback) {
    feedbacks.add(feedback)
    feedback.assignment = this
  }

  def addSubmission(submission: Submission) {
    submissions.add(submission)
    submission.assignment = this
  }

  def addExtension(extension: Extension): Unit = {
    _extensions.add(extension)
    extensionInfo = new ExtensionInfo
    extension.assignment = this
  }

  /**
    * TAB-6028 - When we make an assignment open-ended, remove rejected extension requests (as they no longer make sense)
    */
  def removeRejectedExtensions(persistence: ExtensionPersistenceComponent): Unit = {
    _extensions.asScala
      .filterNot(e => e.awaitingReview || e.approved)
      .foreach { extension =>
        extension.attachments.asScala.foreach(persistence.delete)
        _extensions.remove(extension)
        extension.assignment = null
        persistence.delete(extension)
      }

    extensionInfo = new ExtensionInfo
  }

  // returns the submission for a specified student
  def findSubmission(usercode: String): Option[Submission] = submissions.asScala.find(_.usercode == usercode)

  // Help views decide whether to show a publish button.
  def canPublishFeedback: Boolean =
    publishFeedback &&
      fullFeedback.nonEmpty &&
      unreleasedFeedback.nonEmpty &&
      (openEnded || closeDate.isBeforeNow)

  def canSubmit(user: User): Boolean = {
    user.isFoundUser && (
      if (restrictSubmissions) {
        // users can always submit to assignments if they have a submission or piece of feedback
        submissions.asScala.exists(_.usercode == user.getUserId) ||
          fullFeedback.exists(_.usercode == user.getUserId) ||
          assessmentMembershipService.isStudentCurrentMember(user, upstreamAssessmentGroupInfos, Option(members))
      } else {
        true
      })
  }

  // if the department allows extensions we must be able to manually create extensions even if requests aren't allowed
  def extensionsPossible: Boolean = !openEnded && allowExtensions

  def newExtensionsCanBeRequested: Boolean = extensionsPossible && module.adminDepartment.allowExtensionRequests && (!isClosed || allowExtensionsAfterCloseDate)

  @Deprecated
  def getMarkerFeedback(usercode: String, user: User, feedbackPosition: FeedbackPosition): Option[MarkerFeedback] = {
    val parentFeedback = feedbacks.asScala.find(_.usercode == usercode)
    parentFeedback.flatMap {
      f => getMarkerFeedbackForPositionInFeedback(user, feedbackPosition, f)
    }
  }

  @Deprecated
  def getMarkerFeedbackForCurrentPosition(usercode: String, user: User): Option[MarkerFeedback] = for {
    feedback <- feedbacks.asScala.find(_.usercode == usercode)
    position <- feedback.getCurrentWorkflowFeedbackPosition
    markerFeedback <- getMarkerFeedbackForPositionInFeedback(user, position, feedback)
  } yield markerFeedback

  @Deprecated
  def getLatestCompletedMarkerFeedback(usercode: String, user: User): Option[MarkerFeedback] = {
    val parentFeedback = feedbacks.asScala.find(_.usercode == usercode)
    parentFeedback.flatMap {
      f => getUpToThirdFeedbacks(user, f).find(_.state == MarkingState.MarkingCompleted)
    }
  }

  @Deprecated
  def getAllMarkerFeedbacks(usercode: String, user: User): Seq[MarkerFeedback] = {
    feedbacks.asScala.find(_.usercode == usercode).fold(Seq[MarkerFeedback]())(feedback =>
      feedback.getCurrentWorkflowFeedbackPosition match {
        case None => getUpToThirdFeedbacks(user, feedback)
        case Some(ThirdFeedback) => getUpToThirdFeedbacks(user, feedback)
        case Some(SecondFeedback) => getUpToSecondFeedbacks(user, feedback)
        case Some(FirstFeedback) => getUpToFirstFeedbacks(user, feedback)
      })
  }

  @Deprecated
  private def getUpToThirdFeedbacks(user: User, feedback: Feedback): Seq[MarkerFeedback] = {
    if (this.markingWorkflow.hasThirdMarker && this.markingWorkflow.getStudentsThirdMarker(this, feedback.usercode).contains(user.getUserId)) {
      Seq(feedback.getThirdMarkerFeedback, feedback.getSecondMarkerFeedback, feedback.getFirstMarkerFeedback).flatten
    } else {
      getUpToSecondFeedbacks(user, feedback)
    }
  }

  @Deprecated
  private def getUpToSecondFeedbacks(user: User, feedback: Feedback): Seq[MarkerFeedback] = {
    if (this.markingWorkflow.hasSecondMarker && this.markingWorkflow.getStudentsSecondMarker(this, feedback.usercode).contains(user.getUserId)) {
      Seq(feedback.getSecondMarkerFeedback, feedback.getFirstMarkerFeedback).flatten
    } else {
      getUpToFirstFeedbacks(user, feedback)
    }
  }

  @Deprecated
  private def getUpToFirstFeedbacks(user: User, feedback: Feedback): Seq[MarkerFeedback] = {
    if (this.markingWorkflow.getStudentsFirstMarker(this, feedback.usercode).contains(user.getUserId)) {
      Seq(feedback.getFirstMarkerFeedback).flatten
    } else {
      Seq()
    }
  }

  @Deprecated
  private def getMarkerFeedbackForPositionInFeedback(user: User, feedbackPosition: FeedbackPosition, feedback: Feedback): Option[MarkerFeedback] = {
    feedbackPosition match {
      case FirstFeedback =>
        if (this.isFirstMarker(user))
          feedback.getFirstMarkerFeedback
        else
          None
      case SecondFeedback =>
        if (this.markingWorkflow.hasSecondMarker && this.isSecondMarker(user))
          feedback.getSecondMarkerFeedback
        else
          None
      case ThirdFeedback =>
        if (this.markingWorkflow.hasThirdMarker && this.isThirdMarker(user))
          feedback.getThirdMarkerFeedback
        else
          None
    }
  }

  /**
    * Optionally returns the first marker for the given student ID
    * Returns none if this assignment doesn't have a valid marking workflow attached
    */
  @Deprecated
  def getStudentsFirstMarker(usercode: String): Option[User] =
    Option(markingWorkflow)
      .flatMap(_.getStudentsFirstMarker(this, usercode))
      .map(id => userLookup.getUserByUserId(id))

  /**
    * Optionally returns the second marker for the given student ID
    * Returns none if this assignment doesn't have a valid marking workflow attached
    */
  @Deprecated
  def getStudentsSecondMarker(usercode: String): Option[User] =
    Option(markingWorkflow)
      .flatMap(_.getStudentsSecondMarker(this, usercode))
      .map(id => userLookup.getUserByUserId(id))

  /**
    * Optionally returns the second marker for the given student ID
    * Returns none if this assignment doesn't have a valid marking workflow attached
    */
  @Deprecated
  def getStudentsThirdMarker(usercode: String): Option[User] =
    Option(markingWorkflow)
      .flatMap(_.getStudentsThirdMarker(this, usercode))
      .map(id => userLookup.getUserByUserId(id))

  /**
    * Optionally returns the submissions that are to be marked by the given user
    * Returns none if this assignment doesn't have a valid marking workflow attached
    */
  @Deprecated
  def getMarkersSubmissions(marker: User): Seq[Submission] = {
    if (markingWorkflow != null) markingWorkflow.getSubmissions(this, marker)
    else Seq()
  }

  //Return all first markes along with total students allocated
  @Deprecated
  def firstMarkersWithStudentAllocationCountMap: Map[User, Int] = {
    firstMarkerMap.map { case (usercode, userGrp) => userLookup.getUserByUserId(usercode) -> userGrp.size }
  }

  //Return all second markes along with total students allocated
  @Deprecated
  def secondMarkersWithStudentAllocationCountMap: Map[User, Int] = {
    secondMarkerMap.map { case (usercode, userGrp) => userLookup.getUserByUserId(usercode) -> userGrp.size }
  }

  def toStringProps = Seq(
    "id" -> id,
    "name" -> name,
    "openDate" -> openDate,
    "closeDate" -> closeDate,
    "module" -> module)

  def getUsercodesWithSubmissionOrFeedback: Set[String] = {
    val submissionIds = submissions.asScala.map(_.usercode).toSet
    val feedbackIds = fullFeedback.map(_.usercode).toSet

    submissionIds ++ feedbackIds
  }

  // later we may do more complex checks to see if this particular markingWorkflow requires that feedback is released manually
  // for now all markingWorkflow will require you to release feedback so if one exists for this assignment - provide it
  def mustReleaseForMarking: Boolean = hasWorkflow || hasCM2Workflow

  def needsFeedbackPublishing: Boolean = {
    if (openEnded || dissertation || !publishFeedback || !collectSubmissions || _archived) {
      false
    } else {
      !submissions.asScala.forall(s => fullFeedback.exists(f => f.usercode == s.usercode && f.checkedReleased) || s.hasPlagiarismInvestigation)
    }
  }

  def needsFeedbackPublishingIgnoreExtensions: Boolean = {
    if (openEnded || dissertation || !publishFeedback || !collectSubmissions || _archived) {
      false
    } else {
      !submissions.asScala.forall(s => approvedExtensions.contains(s.usercode) || fullFeedback.exists(f => f.usercode == s.usercode && f.checkedReleased) || s.hasPlagiarismInvestigation)
    }
  }

  def needsFeedbackPublishingFor(usercode: String): Boolean = {
    if (openEnded || dissertation || !publishFeedback || !collectSubmissions || _archived) {
      false
    } else {
      !submissions.asScala.find(_.usercode == usercode).forall(s => fullFeedback.exists(f => f.usercode == s.usercode && f.checkedReleased) || s.hasPlagiarismInvestigation)
    }
  }

  def resetMarkerFeedback(): Unit = {
    val markerFeedbacks = allFeedback.flatMap(_.allMarkerFeedback)
    markerFeedbacks.foreach(feedbackService.delete)
    feedbacks.asScala.foreach(f => {
      f.clearAttachments()
      f.outstandingStages.clear()
      f.markerFeedback.clear()
      feedbackService.saveOrUpdate(f)
    })
  }

  def cm2MarkerAllocations: Seq[MarkerAllocation] =
    Option(cm2MarkingWorkflow)
      .map { workflow =>
        lazy val markerFeedback = allFeedback.flatMap(_.allMarkerFeedback)

        workflow.markers.toSeq
          .sortBy { case (stage, _) => stage.order }
          .flatMap { case (stage, markers) =>
            markers.sortBy { u => (u.getLastName, u.getFirstName) }.map { marker =>
              MarkerAllocation(
                stage.roleName,
                stage.description,
                marker,
                markerFeedback.filter { mf => mf.stage == stage && mf.marker == marker }.map(_.student).toSet
              )
            }
          }
      }.getOrElse(Nil)

  def cm2MarkerAllocations(marker: User): Seq[MarkerAllocation] =
    Option(cm2MarkingWorkflow)
      .map { workflow =>
        lazy val markerFeedback = allFeedback.flatMap(_.allMarkerFeedback)

        workflow.markers.toSeq
          .filter { case (_, markers) => markers.contains(marker) }
          .sortBy { case (stage, _) => stage.order }
          .map { case (stage, _) =>
            MarkerAllocation(
              stage.roleName,
              stage.description,
              marker,
              markerFeedback.filter { mf => mf.stage == stage && mf.marker == marker }.map(_.student).toSet
            )
          }
      }.getOrElse(Nil)

  def cm2MarkerStudentUsercodes(marker: User): Set[String] =
    Option(cm2MarkingWorkflow)
      .map { workflow =>
        lazy val markerFeedback = allFeedback.flatMap(_.allMarkerFeedback)

        workflow.markers.toSeq
          .filter { case (_, markers) => markers.contains(marker) }
          .sortBy { case (stage, _) => stage.order }
          .flatMap { case (stage, _) =>
            markerFeedback.filter { mf => mf.stage == stage && mf.marker == marker }.map(_.feedback.usercode)
          }
          .toSet
      }.getOrElse(Set.empty)

  def cm2MarkerSubmissions(marker: User): Seq[Submission] = {
    val usercodes = cm2MarkerStudentUsercodes(marker)

    if (usercodes.isEmpty) Nil
    else submissions.asScala.filter { s => usercodes.contains(s.usercode) }
  }

  def automaticallyReleaseToMarkers: Boolean = getBooleanSetting(Settings.AutomaticallyReleaseToMarkers, default = false)
  def automaticallyReleaseToMarkers_=(include: Boolean): Unit = settings += (Settings.AutomaticallyReleaseToMarkers -> include)

  def automaticallySubmitToTurnitin: Boolean = getBooleanSetting(Settings.AutomaticallySubmitToTurnitin, default = false)
  def automaticallySubmitToTurnitin_=(include: Boolean): Unit = settings += (Settings.AutomaticallySubmitToTurnitin -> include)

  def extensionAttachmentMandatory: Boolean = getBooleanSetting(Settings.ExtensionAttachmentMandatory, default = false)
  def extensionAttachmentMandatory_=(mandatory: Boolean): Unit = settings += (Settings.ExtensionAttachmentMandatory -> mandatory)

  def allowExtensionsAfterCloseDate: Boolean = getBooleanSetting(Settings.AllowExtensionsAfterCloseDate, default = false)
  def allowExtensionsAfterCloseDate_=(allow: Boolean): Unit = settings += (Settings.AllowExtensionsAfterCloseDate -> allow)

  def turnitinLtiNotifyUsers: Seq[User] = UserSeqSetting(Settings.TurnitinLtiNotifyUsers, Seq(), userLookup).value
  def turnitinLtiNotifyUsers_=(users: Seq[User]): Unit = UserSeqSetting(Settings.TurnitinLtiNotifyUsers, Seq(), userLookup).value = users

  def turnitinLtiClassWithAcademicYear: Boolean = getBooleanSetting(Settings.TurnitinLtiClassWithAcademicYear, default = false)
  def turnitinLtiClassWithAcademicYear_=(withAcademicYear: Boolean): Unit = settings += (Settings.TurnitinLtiClassWithAcademicYear -> withAcademicYear)

  /**
    * Determines which repository the student is going to submit their paper to.
    * It will be stored in the standard repository if true.
    */
  def turnitinStoreInRepository: Boolean = getBooleanSetting(Settings.TurnitinStoreInRepository, default = true)
  def turnitinStoreInRepository_=(storeInRepository: Boolean): Unit = settings += (Settings.TurnitinStoreInRepository -> storeInRepository)

  /**
    * Determines whether the bibliography is excluded from the originality score
    */
  def turnitinExcludeBibliography: Boolean = getBooleanSetting(Settings.TurnitinExcludeBibliography, default = false)
  def turnitinExcludeBibliography_=(excludeBibliography: Boolean): Unit = settings += (Settings.TurnitinExcludeBibliography -> excludeBibliography)

  /**
    * Determines whether quoted material is excluded from the originality score
    */
  def turnitinExcludeQuoted: Boolean = getBooleanSetting(Settings.TurnitinExcludeQuoted, default = false)
  def turnitinExcludeQuoted_=(excludeQuoted: Boolean): Unit = settings += (Settings.TurnitinExcludeQuoted -> excludeQuoted)

  def enhance(user: User): EnhancedAssignment = {
    val submission = submissions.asScala.find(_.usercode == user.getUserId)
    val feedbackDeadline = submission.flatMap(feedbackDeadlineForSubmission)
    val feedbackDeadlineWorkingDaysAway = feedbackDeadline.map(workingDaysAway)
    EnhancedAssignment(
      user = user,
      assignment = this,
      submissionDeadline = Option(submissionDeadline(user)),
      submission = submission,
      feedbackDeadlineWorkingDaysAway = feedbackDeadlineWorkingDaysAway,
      feedback = feedbacks.asScala.filter(_.released).find(_.usercode == user.getUserId),
      extension = approvedExtensions.get(user.getUserId),
      withinExtension = isWithinExtension(user),
      extensionRequested = allExtensions.get(user.getUserId).exists(_.exists(!_.isManual))
    )
  }

  def showStudentNames: Boolean = (anonymity == null && module.adminDepartment.showStudentName) || anonymity == NameAndID

  @transient
  private lazy val seatNumbers: Map[String, Int] = assessmentMembershipService.determineMembershipUsersWithOrder(this).collect {
    case (user, Some(seat)) => (user.getUserId, seat)
  }.toMap

  def getSeatNumber(user: User): Option[Int] = Option(user).flatMap(u => seatNumbers.get(u.getUserId))

  def showSeatNumbers: Boolean = seatNumbers.nonEmpty
}

/**
  * One stop shop for setting default boolean values for assignment properties.
  *
  * Includes @BeanProperty to allow JSON binding
  */
trait BooleanAssignmentDetailProperties {
  @BeanProperty var cm2Assignment: JBoolean = false
  @BeanProperty var openEnded: JBoolean = false

  def copyDetailBooleansTo(assignment: Assignment) {
    assignment.openEnded = openEnded
  }
}

trait BooleanAssignmentFeedbackProperties {
  @BeanProperty var collectMarks: JBoolean = true
  @BeanProperty var useMarkPoints: JBoolean = false
  @BeanProperty var automaticallyReleaseToMarkers: JBoolean = false
  @BeanProperty var summative: JBoolean = true
  @BeanProperty var dissertation: JBoolean = false
  @BeanProperty var publishFeedback: JBoolean = true
  @BeanProperty var includeInFeedbackReportWithoutSubmissions: JBoolean = false

  def copyFeedbackBooleansTo(assignment: Assignment) {
    assignment.collectMarks = collectMarks
    assignment.useMarkPoints = useMarkPoints
    assignment.summative = summative
    assignment.dissertation = dissertation
    assignment.publishFeedback = publishFeedback
    assignment.includeInFeedbackReportWithoutSubmissions = includeInFeedbackReportWithoutSubmissions
    assignment.automaticallyReleaseToMarkers = automaticallyReleaseToMarkers
  }
}

trait BooleanAssignmentStudentProperties {
  @BeanProperty var hiddenFromStudents: JBoolean = false

  def copyStudentBooleansTo(assignment: Assignment) {
    // You can only hide an assignment, no un-hiding.
    if (hiddenFromStudents) assignment.hideFromStudents()
  }
}

trait BooleanAssignmentSubmissionProperties {
  @BeanProperty var collectSubmissions: JBoolean = true
  @BeanProperty var restrictSubmissions: JBoolean = true
  @BeanProperty var allowLateSubmissions: JBoolean = true
  @BeanProperty var allowResubmission: JBoolean = true
  @BeanProperty var displayPlagiarismNotice: JBoolean = true
  @BeanProperty var allowExtensions: JBoolean = true
  @BeanProperty var extensionAttachmentMandatory: JBoolean = false
  @BeanProperty var allowExtensionsAfterCloseDate: JBoolean = false
  @BeanProperty var automaticallySubmitToTurnitin: JBoolean = false
  @BeanProperty var turnitinStoreInRepository: JBoolean = true
  @BeanProperty var turnitinExcludeBibliography: JBoolean = false
  @BeanProperty var turnitinExcludeQuoted: JBoolean = false

  def copySubmissionBooleansTo(assignment: Assignment) {
    assignment.collectSubmissions = collectSubmissions
    assignment.restrictSubmissions = restrictSubmissions
    assignment.allowLateSubmissions = allowLateSubmissions
    assignment.allowResubmission = allowResubmission
    assignment.displayPlagiarismNotice = displayPlagiarismNotice
    assignment.allowExtensions = allowExtensions
    assignment.extensionAttachmentMandatory = extensionAttachmentMandatory
    assignment.allowExtensionsAfterCloseDate = allowExtensionsAfterCloseDate
    assignment.automaticallySubmitToTurnitin = automaticallySubmitToTurnitin
    assignment.turnitinStoreInRepository = turnitinStoreInRepository
    assignment.turnitinExcludeBibliography = turnitinExcludeBibliography
    assignment.turnitinExcludeQuoted = turnitinExcludeQuoted
  }
}

trait BooleanAssignmentProperties
  extends BooleanAssignmentDetailProperties
    with BooleanAssignmentFeedbackProperties
    with BooleanAssignmentStudentProperties
    with BooleanAssignmentSubmissionProperties {

  def copyBooleansTo(assignment: Assignment) {
    copyDetailBooleansTo(assignment)
    copyFeedbackBooleansTo(assignment)
    copyStudentBooleansTo(assignment)
    copySubmissionBooleansTo(assignment)
  }
}

object BooleanAssignmentProperties extends BooleanAssignmentProperties {
  def apply(assignment: Assignment): Unit = copyBooleansTo(assignment)
}

case class EnhancedAssignment(
  user: User,
  assignment: Assignment,
  submissionDeadline: Option[DateTime],
  submission: Option[Submission],
  feedbackDeadlineWorkingDaysAway: Option[Int],
  feedback: Option[Feedback],
  extension: Option[Extension],
  withinExtension: Boolean,
  extensionRequested: Boolean
)

trait HasAssignment {
  def assignment: Assignment
}