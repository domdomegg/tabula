package uk.ac.warwick.tabula.commands.mitcircs.submission

import uk.ac.warwick.tabula.commands._
import uk.ac.warwick.tabula.data.Transactions._
import uk.ac.warwick.tabula.permissions.{Permission, Permissions}
import uk.ac.warwick.tabula.system.permissions.{PermissionsChecking, PermissionsCheckingMethods, RequiresPermissionsChecking}
import org.springframework.validation.Errors
import MitCircsRecordAcuteOutcomesCommand._
import org.joda.time.DateTime
import uk.ac.warwick.tabula.JavaImports._
import uk.ac.warwick.tabula.data.model.mitcircs.MitigatingCircumstancesGrading.Rejected
import uk.ac.warwick.tabula.helpers.StringUtils._
import uk.ac.warwick.tabula.data.model.mitcircs.{MitigatingCircumstancesAcuteOutcome, MitigatingCircumstancesGrading, MitigatingCircumstancesRejectionReason, MitigatingCircumstancesSubmission}
import uk.ac.warwick.tabula.services.mitcircs.{AutowiringMitCircsSubmissionServiceComponent, MitCircsSubmissionServiceComponent}
import uk.ac.warwick.userlookup.User

import scala.collection.JavaConverters._

object MitCircsRecordAcuteOutcomesCommand {

  type Result = MitigatingCircumstancesSubmission
  type Command = Appliable[Result] with MitCircsRecordAcuteOutcomesState with MitCircsRecordAcuteOutcomesRequest with SelfValidating
  val RequiredPermission: Permission = Permissions.MitigatingCircumstancesSubmission.Manage

  def apply(submission: MitigatingCircumstancesSubmission, user: User) = new MitCircsRecordAcuteOutcomesCommandInternal(submission, user)
    with ComposableCommand[Result]
    with MitCircsRecordAcuteOutcomesRequest
    with MitCircsRecordAcuteOutcomesValidation
    with MitCircsRecordAcuteOutcomesPermissions
    with MitCircsRecordAcuteOutcomesDescription
    with AutowiringMitCircsSubmissionServiceComponent
}

class MitCircsRecordAcuteOutcomesCommandInternal(val submission: MitigatingCircumstancesSubmission, val user: User) extends CommandInternal[Result]
  with MitCircsRecordAcuteOutcomesState with MitCircsRecordAcuteOutcomesValidation {

  self: MitCircsRecordAcuteOutcomesRequest with MitCircsSubmissionServiceComponent =>

  outcomeGrading = submission.outcomeGrading
  outcomeReasons = submission.outcomeReasons
  acuteOutcome = submission.acuteOutcome
  rejectionReasons = submission.rejectionReasons.asJava
  rejectionReasonsOther = submission.rejectionReasonsOther

  def applyInternal(): Result = transactional() {
    require(submission.canRecordAcuteOutcomes, "Cannot record acute outcomes for this submission")

    submission.outcomeGrading = outcomeGrading
    submission.outcomeReasons = outcomeReasons
    submission.acuteOutcome = acuteOutcome
    submission.rejectionReasons = rejectionReasons.asScala
    if (rejectionReasons.asScala.contains(MitigatingCircumstancesRejectionReason.Other) && rejectionReasonsOther.hasText) {
      submission.rejectionReasonsOther = rejectionReasonsOther
    } else {
      submission.rejectionReasonsOther = null
    }


    submission.outcomesRecorded()
    submission.lastModifiedBy = user
    submission.lastModified = DateTime.now
    mitCircsSubmissionService.saveOrUpdate(submission)
    submission
  }
}

trait MitCircsRecordAcuteOutcomesPermissions extends RequiresPermissionsChecking with PermissionsCheckingMethods {
  self: MitCircsRecordAcuteOutcomesState =>

  def permissionsCheck(p: PermissionsChecking) {
    p.PermissionCheck(RequiredPermission, submission)
  }
}

trait MitCircsRecordAcuteOutcomesValidation extends SelfValidating {
  self: MitCircsRecordAcuteOutcomesRequest =>

  def validate(errors: Errors) {
    if(outcomeGrading == null) errors.rejectValue("outcomeGrading", "mitigatingCircumstances.outcomes.outcomeGrading.required")
    if(!outcomeReasons.hasText) errors.rejectValue("outcomeReasons", "mitigatingCircumstances.outcomes.outcomeReasons.required")
    if(acuteOutcome == null) errors.rejectValue("acuteOutcome", "mitigatingCircumstances.outcomes.acuteOutcome.required")

    if(outcomeGrading == Rejected && rejectionReasons.isEmpty) errors.rejectValue("rejectionReasons", "mitigatingCircumstances.outcomes.rejectionReasons.required")
    else if(outcomeGrading == Rejected && rejectionReasons.contains(MitigatingCircumstancesRejectionReason.Other) && !rejectionReasonsOther.hasText)
      errors.rejectValue("rejectionReasonsOther", "mitigatingCircumstances.outcomes.rejectionReasonsOther.required")
  }
}

trait MitCircsRecordAcuteOutcomesDescription extends Describable[Result] {
  self: MitCircsRecordAcuteOutcomesState =>

  def describe(d: Description) {
    d.mitigatingCircumstancesSubmission(submission)
  }
}

trait MitCircsRecordAcuteOutcomesState {
  val submission: MitigatingCircumstancesSubmission
  val user: User
}

trait MitCircsRecordAcuteOutcomesRequest {
  var outcomeGrading: MitigatingCircumstancesGrading = _
  var outcomeReasons: String = _
  var rejectionReasons: JList[MitigatingCircumstancesRejectionReason] = JArrayList()
  var rejectionReasonsOther: String = _
  var acuteOutcome: MitigatingCircumstancesAcuteOutcome = _
}