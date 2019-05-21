package uk.ac.warwick.tabula.web.controllers.mitcircs

import javax.validation.Valid
import org.springframework.stereotype.Controller
import org.springframework.validation.Errors
import org.springframework.web.bind.annotation.{ModelAttribute, PathVariable, RequestMapping}
import uk.ac.warwick.tabula.CurrentUser
import uk.ac.warwick.tabula.commands.mitcircs.submission.{MitCircsReadyForPanelCommand, MitCircsReadyForPanelState}
import uk.ac.warwick.tabula.commands.{Appliable, SelfValidating}
import uk.ac.warwick.tabula.data.model.StudentMember
import uk.ac.warwick.tabula.data.model.mitcircs.MitigatingCircumstancesSubmission
import uk.ac.warwick.tabula.web.Mav
import uk.ac.warwick.tabula.web.controllers.BaseController
import uk.ac.warwick.tabula.web.views.JSONView

@Controller
@RequestMapping(value = Array("/mitcircs/submission/{submission}/ready"))
class MitCircsReadyForPanelController extends BaseController {

  validatesSelf[SelfValidating]
  type Command = Appliable[MitigatingCircumstancesSubmission] with MitCircsReadyForPanelState with SelfValidating

  @ModelAttribute("command") def command(@PathVariable submission: MitigatingCircumstancesSubmission, user: CurrentUser): Command =
    MitCircsReadyForPanelCommand(mandatory(submission), user.apparentUser)

  @RequestMapping
  def form(@ModelAttribute("student") student: StudentMember, @PathVariable submission: MitigatingCircumstancesSubmission): Mav = {
    Mav("mitcircs/submissions/ready_for_panel").noLayout()
  }

  @RequestMapping(method = Array(POST))
  def save(@Valid @ModelAttribute("command") cmd: Command, errors: Errors, @PathVariable submission: MitigatingCircumstancesSubmission): Mav = {
    if (errors.hasErrors) form(submission.student, submission)
    else {
      cmd.apply()
      Mav(new JSONView(Map("success" -> true)))
    }
  }

}