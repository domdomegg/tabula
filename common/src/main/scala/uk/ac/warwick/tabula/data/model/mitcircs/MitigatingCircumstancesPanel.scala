package uk.ac.warwick.tabula.data.model.mitcircs

import javax.persistence.CascadeType.ALL
import javax.persistence.{Access, AccessType, Basic, Column, Entity, FetchType, JoinColumn, ManyToOne, OneToMany}
import org.hibernate.annotations.{BatchSize, Type}
import org.joda.time.{DateTime, LocalTime}
import org.springframework.format.annotation.DateTimeFormat
import uk.ac.warwick.spring.Wire
import uk.ac.warwick.tabula.JavaImports._
import uk.ac.warwick.tabula.{AcademicYear, DateFormats, ToString}
import uk.ac.warwick.tabula.data.model.{Department, GeneratedId, Location, StringId, ToEntityReference, UnspecifiedTypeUserGroup}
import uk.ac.warwick.tabula.data.model.forms.FormattedHtml
import uk.ac.warwick.tabula.permissions.PermissionsTarget
import uk.ac.warwick.tabula.roles.MitigatingCircumstancesPanelMemberRoleDefinition
import uk.ac.warwick.tabula.services.mitcircs.MitCircsPanelService
import uk.ac.warwick.tabula.services.permissions.PermissionsService
import uk.ac.warwick.userlookup.User

import scala.collection.JavaConverters._

@Entity
@Access(AccessType.FIELD)
class MitigatingCircumstancesPanel extends GeneratedId with StringId with Serializable
  with ToString
  with PermissionsTarget
  with ToEntityReference
  with FormattedHtml {

  def this(department: Department, academicYear: AcademicYear) {
    this()
    this.department = department
    this.academicYear = academicYear
  }

  @Column(nullable = false)
  var name: String = _

  @Column(nullable = true)
  @DateTimeFormat(pattern = DateFormats.DateTimePickerPattern)
  var date: DateTime = _

  @Column(nullable = true)
  @DateTimeFormat(pattern = DateFormats.DateTimePickerPattern)
  var endDate: DateTime = _

  def startTime: LocalTime = date.toLocalTime
  def endTime: LocalTime = endDate.toLocalTime

  @Type(`type` = "uk.ac.warwick.tabula.data.model.LocationUserType")
  var location: Location = _

  @ManyToOne(cascade = Array(ALL), fetch = FetchType.LAZY)
  @JoinColumn(name = "department_id")
  var department: Department = _

  @Basic
  @Type(`type` = "uk.ac.warwick.tabula.data.model.AcademicYearUserType")
  @Column(nullable = false)
  var academicYear: AcademicYear = AcademicYear.now()

  @OneToMany(mappedBy = "_panel", fetch = FetchType.LAZY)
  @BatchSize(size = 200)
  private val _submissions: JSet[MitigatingCircumstancesSubmission] = JHashSet()
  def submissions: Set[MitigatingCircumstancesSubmission] = _submissions.asScala.toSet

  def addSubmission(submission: MitigatingCircumstancesSubmission) {
    submission.panel = this
    _submissions.add(submission)
  }

  def removeSubmission(submission: MitigatingCircumstancesSubmission): Boolean = {
    submission.panel = null
    _submissions.remove(submission)
  }

  @Column(nullable = false)
  var lastModified: DateTime = DateTime.now()

  @transient
  var permissionsService: PermissionsService = Wire[PermissionsService]

  @Type(`type` = "uk.ac.warwick.tabula.data.model.SSOUserType")
  var chair: User = _

  @Type(`type` = "uk.ac.warwick.tabula.data.model.SSOUserType")
  var secretary: User = _

  @transient
  private lazy val _viewers: UnspecifiedTypeUserGroup = permissionsService.ensureUserGroupFor(scope = this, MitigatingCircumstancesPanelMemberRoleDefinition)
  def viewers: Set[User] = _viewers.users
  def viewers_=(userIds: Set[String]): Unit = _viewers.knownType.includedUserIds = userIds

  def members: Set[User] = viewers -- Set(chair, secretary)

  @transient
  var mitCircsPanelService: Option[MitCircsPanelService] = Wire.option[MitCircsPanelService]

  override def toStringProps: Seq[(String, Any)] = Seq(
    "id" -> id
  )

  override def permissionsParents: Stream[PermissionsTarget] = Stream(department)

}
