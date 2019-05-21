package uk.ac.warwick.tabula.data.model.mitcircs

import java.io.Serializable

import javax.persistence.CascadeType._
import javax.persistence._
import org.hibernate.annotations.{BatchSize, Type}
import org.joda.time.{DateTime, LocalDate}
import uk.ac.warwick.tabula.JavaImports._
import uk.ac.warwick.tabula.ToString
import uk.ac.warwick.tabula.data.Transactions._
import uk.ac.warwick.tabula.data.model._
import uk.ac.warwick.tabula.data.model.forms.FormattedHtml
import uk.ac.warwick.tabula.data.model.mitcircs.MitigatingCircumstancesSubmissionState._
import uk.ac.warwick.tabula.helpers.DateTimeOrdering._
import uk.ac.warwick.tabula.permissions.PermissionsTarget
import uk.ac.warwick.userlookup.User

import scala.collection.JavaConverters._

@Entity
@Access(AccessType.FIELD)
class MitigatingCircumstancesSubmission extends GeneratedId
  with ToString
  with PermissionsTarget
  with Serializable
  with ToEntityReference
  with FormattedHtml {
    type Entity = MitigatingCircumstancesSubmission

  def this(student: StudentMember, creator: User, department: Department) {
    this()
    this.creator = creator
    this.lastModifiedBy = creator
    this.student = student
    this.department = department
  }

  @Column(nullable = false, unique = true)
  var key: JLong = _

  @Column(nullable = false)
  val createdDate: DateTime = DateTime.now()

  @Column(name = "lastModified", nullable = false)
  private var _lastModified: DateTime = DateTime.now()

  /**
    * This will return the latest of:
    * - the submission's last modified date
    * - the latest message that was sent
    * - the latest modified date of any notes
    */
  def lastModified: DateTime = transactional(readOnly = true) {
    Seq(
      Option(_lastModified),
      messages.sortBy(_.createdDate).lastOption.map(_.createdDate),
      notes.sortBy(_.lastModified).lastOption.map(_.lastModified),
    ).flatten.max
  }

  def lastModified_=(lastModified: DateTime): Unit = _lastModified = lastModified

  @Column(nullable = false)
  @Type(`type` = "uk.ac.warwick.tabula.data.model.SSOUserType")
  final var creator: User = _ // the user that created this

  @Column(nullable = false)
  @Type(`type` = "uk.ac.warwick.tabula.data.model.SSOUserType")
  var lastModifiedBy: User = _

  @ManyToOne(cascade = Array(ALL), fetch = FetchType.EAGER)
  @JoinColumn(name = "universityId", referencedColumnName = "universityId")
  var student: StudentMember = _

  @ManyToOne(cascade = Array(ALL), fetch = FetchType.LAZY)
  @JoinColumn(name = "department_id")
  var department: Department = _

  @ManyToOne(cascade = Array(ALL), fetch = FetchType.LAZY)
  @JoinColumn(name = "relatedSubmission")
  var relatedSubmission: MitigatingCircumstancesSubmission = _

  @Column(nullable = false)
  var startDate: LocalDate = _

  @Column(nullable = true)
  var endDate: LocalDate = _

  @Type(`type` = "uk.ac.warwick.tabula.data.model.mitcircs.IssueTypeUserType")
  var issueTypes: Seq[IssueType] = _

  @Type(`type` = "uk.ac.warwick.tabula.data.model.EncryptedStringUserType")
  @Column(name = "issueTypeDetails")
  private var encryptedIssueTypeDetails: CharSequence = _

  // free text for use when the issue type includes Other
  def issueTypeDetails: String = Option(encryptedIssueTypeDetails).map(_.toString).orNull
  def issueTypeDetails_=(issueTypeDetails: String): Unit = encryptedIssueTypeDetails = issueTypeDetails

  var contacted: JBoolean = _

  @Type(`type` = "uk.ac.warwick.tabula.data.model.mitcircs.MitCircsContactUserType")
  var contacts: Seq[MitCircsContact] = _

  @Type(`type` = "uk.ac.warwick.tabula.data.model.EncryptedStringUserType")
  @Column(name = "contactOther")
  private var encryptedContactOther: CharSequence = _

  // free text for use when the contacts includes Other
  def contactOther: String = Option(encryptedContactOther).map(_.toString).orNull
  def contactOther_=(contactOther: String): Unit = encryptedContactOther = contactOther

  @Type(`type` = "uk.ac.warwick.tabula.data.model.EncryptedStringUserType")
  @Column(name = "noContactReason")
  private var encryptedNoContactReason: CharSequence = _
  def noContactReason: String = Option(encryptedNoContactReason).map(_.toString).orNull
  def noContactReason_=(noContactReason: String): Unit = encryptedNoContactReason = noContactReason

  @Type(`type` = "uk.ac.warwick.tabula.data.model.EncryptedStringUserType")
  @Column(name = "reason", nullable = false)
  private var encryptedReason: CharSequence = _
  def reason: String = Option(encryptedReason).map(_.toString).orNull
  def reason_=(reason: String): Unit = encryptedReason = reason

  def formattedReason: String = formattedHtml(reason)

  @OneToMany(fetch = FetchType.LAZY, cascade = Array(ALL), orphanRemoval = true)
  @JoinColumn(name = "submission_id")
  @BatchSize(size = 200)
  @OrderBy("academicYear, moduleCode, sequence")
  var affectedAssessments: JList[MitigatingCircumstancesAffectedAssessment] = JArrayList()

  @Type(`type` = "uk.ac.warwick.tabula.data.model.EncryptedStringUserType")
  @Column(name = "pendingEvidence", nullable = false)
  private var encryptedPendingEvidence: CharSequence = _
  def pendingEvidence: String = Option(encryptedPendingEvidence).map(_.toString).orNull
  def pendingEvidence_=(pendingEvidence: String): Unit = encryptedPendingEvidence = pendingEvidence

  def formattedPendingEvidence: String = formattedHtml(pendingEvidence)

  var pendingEvidenceDue: LocalDate = _

  @Type(`type` = "uk.ac.warwick.tabula.data.model.EncryptedStringUserType")
  @Column(name = "sensitiveEvidenceComments")
  private var encryptedSensitiveEvidenceComments: CharSequence = _
  def sensitiveEvidenceComments: String = Option(encryptedSensitiveEvidenceComments).map(_.toString).orNull
  def sensitiveEvidenceComments_=(sensitiveEvidenceComments: String): Unit = encryptedSensitiveEvidenceComments = sensitiveEvidenceComments

  def formattedSensitiveEvidenceComments: String = formattedHtml(sensitiveEvidenceComments)

  @Type(`type` = "uk.ac.warwick.tabula.data.model.SSOUserType")
  var sensitiveEvidenceSeenBy: User = _

  var sensitiveEvidenceSeenOn: LocalDate = _

  @Column(name = "approvedOn")
  private var _approvedOn: DateTime = _

  // Guarded against being set directly; use .approveAndSubmit() below
  def approvedOn: Option[DateTime] = Option(_approvedOn)

  @OneToMany(mappedBy = "mitigatingCircumstancesSubmission", fetch = FetchType.LAZY, cascade = Array(ALL))
  @BatchSize(size = 200)
  private val _attachments: JSet[FileAttachment] = JHashSet()
  def attachments: Seq[FileAttachment] = _attachments.asScala.toSeq.sortBy(_.dateUploaded)

  def addAttachment(attachment: FileAttachment) {
    if (attachment.isAttached) throw new IllegalArgumentException("File already attached to another object")
    attachment.temporary = false
    attachment.mitigatingCircumstancesSubmission = this
    _attachments.add(attachment)
  }

  def removeAttachment(attachment: FileAttachment): Boolean = {
    attachment.mitigatingCircumstancesSubmission = null
    _attachments.remove(attachment)
  }

  @OneToMany(mappedBy = "submission", fetch = FetchType.LAZY, cascade = Array(ALL), orphanRemoval = true)
  @BatchSize(size = 200)
  private val _messages: JSet[MitigatingCircumstancesMessage] = JHashSet()
  def messages: Seq[MitigatingCircumstancesMessage] = _messages.asScala.toSeq.sortBy(_.createdDate)

  @OneToMany(mappedBy = "submission", fetch = FetchType.LAZY, cascade = Array(ALL), orphanRemoval = true)
  @BatchSize(size = 200)
  private val _notes: JSet[MitigatingCircumstancesNote] = JHashSet()
  def notes: Seq[MitigatingCircumstancesNote] = _notes.asScala.toSeq.sortBy(_.createdDate)

  @Column(name = "lastViewedByStudent")
  private var _lastViewedByStudent: DateTime = _
  def lastViewedByStudent: Option[DateTime] = Option(_lastViewedByStudent)
  def lastViewedByStudent_=(dt: DateTime): Unit = _lastViewedByStudent = dt

  @Column(name = "lastViewedByOfficer")
  private var _lastViewedByOfficer: DateTime = _
  def lastViewedByOfficer: Option[DateTime] = Option(_lastViewedByOfficer)
  def lastViewedByOfficer_=(dt: DateTime): Unit = _lastViewedByOfficer = dt

  def isUnreadByOfficer: Boolean = transactional(readOnly = true) {
    // Doesn't include notes, whereas normally lastModified does, and only includes student-sent messages
    val lastModified =
      Seq(
        Option(_lastModified),
        messages.filter(_.studentSent).sortBy(_.createdDate).lastOption.map(_.createdDate),
      ).flatten.max

    lastViewedByOfficer.forall(_.isBefore(lastModified))
  }

  // Intentionally no default here, rely on a state being set explicitly
  @Type(`type` = "uk.ac.warwick.tabula.data.model.mitcircs.MitigatingCircumstancesSubmissionStateUserType")
  @Column(name = "state", nullable = false)
  private var _state: MitigatingCircumstancesSubmissionState = _

  // Can get but not set state directly, use the methods below to transition
  def state: MitigatingCircumstancesSubmissionState = _state

  // State transitions
  def saveAsDraft(): Unit = {
    // TODO guard against doing this from invalid states

    _state = MitigatingCircumstancesSubmissionState.Draft
    _approvedOn = null
  }

  def saveOnBehalfOfStudent(): Unit = {
    require(_state != Submitted, "Cannot save on behalf of a student if they have already submitted")
    _state = MitigatingCircumstancesSubmissionState.CreatedOnBehalfOfStudent
    _approvedOn = null
  }

  def approveAndSubmit(): Unit = {
    // TODO guard against doing this from invalid states

    _state = MitigatingCircumstancesSubmissionState.Submitted
    _approvedOn = DateTime.now()
  }

  def readyForPanel(): Unit = {
    require(_state == Submitted, "Cannot set as ready for review until this has been submitted by the student")
    _state = MitigatingCircumstancesSubmissionState.ReadyForPanel
  }

  def notReadyForPanel(): Unit = {
    require(_state == ReadyForPanel, "Cannot set as not ready for panel")
    _state = MitigatingCircumstancesSubmissionState.Submitted
  }

  def isDraft: Boolean = state == MitigatingCircumstancesSubmissionState.Draft
  def isEditable(user: User): Boolean = {
    state == MitigatingCircumstancesSubmissionState.CreatedOnBehalfOfStudent ||
    (user.getWarwickId == student.universityId && (
      state == MitigatingCircumstancesSubmissionState.Draft ||
      state == MitigatingCircumstancesSubmissionState.Submitted
    ))
  }

  def hasEvidence: Boolean = attachments.nonEmpty
  def isEvidencePending: Boolean = pendingEvidenceDue != null

  override def toStringProps: Seq[(String, Any)] = Seq(
    "id" -> id,
    "student" -> student.universityId,
    "creator" -> creator.getWarwickId
  )

  // Don't use the student directly as the permission parent here. We don't want permissions to bubble up to all the students touchedDepartments
  override def permissionsParents: Stream[PermissionsTarget] = Stream(MitigatingCircumstancesStudent(student))
}




