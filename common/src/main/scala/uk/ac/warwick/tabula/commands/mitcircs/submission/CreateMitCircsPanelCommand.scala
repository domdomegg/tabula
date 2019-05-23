package uk.ac.warwick.tabula.commands.mitcircs.submission

import org.joda.time.{LocalDate, LocalTime}
import uk.ac.warwick.tabula.commands._
import uk.ac.warwick.tabula.data.Transactions._
import uk.ac.warwick.tabula.system.permissions.{PermissionsChecking, PermissionsCheckingMethods, RequiresPermissionsChecking}
import org.springframework.validation.Errors
import uk.ac.warwick.tabula.data.model.{Department, MapLocation, NamedLocation}
import uk.ac.warwick.tabula.data.model.mitcircs.{MitigatingCircumstancesPanel, MitigatingCircumstancesSubmission}
import uk.ac.warwick.tabula.helpers.StringUtils._
import uk.ac.warwick.tabula.permissions.{Permission, Permissions}
import uk.ac.warwick.tabula.JavaImports._
import uk.ac.warwick.userlookup.User
import CreateMitCircsPanelCommand._
import uk.ac.warwick.tabula.AcademicYear
import uk.ac.warwick.tabula.services.{AutowiringUserLookupComponent, UserLookupComponent}
import uk.ac.warwick.tabula.services.mitcircs.{AutowiringMitCircsPanelServiceComponent, MitCircsPanelServiceComponent}

import scala.collection.JavaConverters._

object CreateMitCircsPanelCommand {

  type Result = MitigatingCircumstancesPanel
  type Command = Appliable[Result] with CreateMitCircsPanelState with CreateMitCircsPanelRequest with SelfValidating
  val RequiredPermission: Permission = Permissions.MitigatingCircumstancesPanel.Modify

  def apply(department: Department, year: AcademicYear, currentUser: User) = new CreateMitCircsPanelCommandInternal(department, year, currentUser)
    with ComposableCommand[MitigatingCircumstancesPanel]
    with CreateMitCircsPanelRequest
    with CreateMitCircsPanelValidation
    with CreateMitCircsPanelPermissions
    with CreateMitCircsPanelDescription
    with AutowiringMitCircsPanelServiceComponent
    with AutowiringUserLookupComponent
}

class CreateMitCircsPanelCommandInternal(val department: Department, val year: AcademicYear, val currentUser: User)
  extends CommandInternal[MitigatingCircumstancesPanel] with CreateMitCircsPanelState with CreateMitCircsPanelValidation {

  self: CreateMitCircsPanelRequest with MitCircsPanelServiceComponent with UserLookupComponent =>

  def applyInternal(): MitigatingCircumstancesPanel = transactional() {
    val transientPanel = new MitigatingCircumstancesPanel(department, year)
    transientPanel.name = name
    transientPanel.date = date.toDateTime(start)
    transientPanel.endDate = date.toDateTime(end)
    if (locationId.hasText) {
      transientPanel.location = MapLocation(location, locationId)
    } else if (location.hasText) {
      transientPanel.location = NamedLocation(location)
    }
    submissions.asScala.foreach(transientPanel.addSubmission)

    val panel = mitCircsPanelService.saveOrUpdate(transientPanel)
    panel.members.knownType.includedUserIds = members.asScala.toSet
    mitCircsPanelService.saveOrUpdate(transientPanel)
  }
}

trait CreateMitCircsPanelPermissions extends RequiresPermissionsChecking with PermissionsCheckingMethods {
  self: CreateMitCircsPanelState =>

  def permissionsCheck(p: PermissionsChecking) {
    p.PermissionCheck(RequiredPermission, department)
  }
}

trait CreateMitCircsPanelValidation extends SelfValidating {
  self: CreateMitCircsPanelRequest =>
  def validate(errors: Errors) {
    if(!name.hasText) errors.rejectValue("name", "mitigatingCircumstances.panel.name.required")
  }
}

trait CreateMitCircsPanelDescription extends Describable[MitigatingCircumstancesPanel] {
  self: CreateMitCircsPanelState =>

  override lazy val eventName: String =  "CreateMitCircsPanel"

  def describe(d: Description) {
    d.properties("department" -> department)
  }

  override def describeResult(d: Description, result: Result): Unit = {
    d.properties("mitigatingCircumstancesSubmissions" -> result.submissions.map(_.key.toString))
  }
}

trait CreateMitCircsPanelState {
  val department: Department
  val year: AcademicYear
  val currentUser: User
}

trait CreateMitCircsPanelRequest {
  self: CreateMitCircsPanelState =>

  var name: String = _
  var date: LocalDate = _
  var start: LocalTime = _
  var end: LocalTime =_
  var location: String = _
  var locationId: String = _
  var submissions: JList[MitigatingCircumstancesSubmission] = JArrayList()
  var members: JList[String] = JArrayList(currentUser.getUserId)
}