package uk.ac.warwick.tabula.commands.mitcircs.submission

import org.joda.time.DateTime
import org.springframework.validation.Errors
import uk.ac.warwick.tabula.commands._
import uk.ac.warwick.tabula.data.Transactions.transactional
import uk.ac.warwick.tabula.data.model.mitcircs.MitigatingCircumstancesSubmission
import uk.ac.warwick.tabula.permissions.Permissions
import uk.ac.warwick.tabula.services.mitcircs.{AutowiringMitCircsSubmissionServiceComponent, MitCircsSubmissionServiceComponent}
import uk.ac.warwick.tabula.system.permissions.{PermissionsChecking, PermissionsCheckingMethods, RequiresPermissionsChecking}
import uk.ac.warwick.userlookup.User

object MitCircsReadyForPanelCommand {
  def apply(submission: MitigatingCircumstancesSubmission, currentUser: User) = new MitCircsReadyForPanelCommandInternal(submission, currentUser)
    with ComposableCommand[MitigatingCircumstancesSubmission]
    with MitCircsReadyForPanelValidation
    with MitCircsReadyForPanelPermissions
    with MitCircsReadyForPanelDescription
    with AutowiringMitCircsSubmissionServiceComponent
}

class MitCircsReadyForPanelCommandInternal(val submission: MitigatingCircumstancesSubmission, val currentUser: User)
  extends CommandInternal[MitigatingCircumstancesSubmission] with MitCircsReadyForPanelState with MitCircsReadyForPanelValidation {

  self: MitCircsSubmissionServiceComponent =>

  def applyInternal(): MitigatingCircumstancesSubmission = transactional() {
    submission.lastModifiedBy = currentUser
    submission.lastModified = DateTime.now()
    if(ready) submission.readyForPanel()
    else submission.notReadyForPanel()
    mitCircsSubmissionService.saveOrUpdate(submission)
    submission
  }
}

trait MitCircsReadyForPanelPermissions extends RequiresPermissionsChecking with PermissionsCheckingMethods {
  self: MitCircsReadyForPanelState =>

  def permissionsCheck(p: PermissionsChecking) {
    p.PermissionCheck(Permissions.MitigatingCircumstancesSubmission.Modify, submission)
  }
}

trait MitCircsReadyForPanelValidation extends SelfValidating {
  self: MitCircsReadyForPanelState =>
  def validate(errors: Errors) {
    if(ready && !confirm) errors.rejectValue("confirm", "mitigatingCircumstances.readyForPanel.confirm.required")
  }
}

trait MitCircsReadyForPanelDescription extends Describable[MitigatingCircumstancesSubmission] {
  self: MitCircsReadyForPanelState =>

  override lazy val eventName: String = if(ready) "MitCircsReadyForPanel" else "MitCircsNotReadyForPanel"

  def describe(d: Description) {
    d.mitigatingCircumstancesSubmission(submission)
  }
}

trait MitCircsReadyForPanelState {
  val submission: MitigatingCircumstancesSubmission
  val currentUser: User
  var ready: Boolean = _
  var confirm: Boolean = _
}