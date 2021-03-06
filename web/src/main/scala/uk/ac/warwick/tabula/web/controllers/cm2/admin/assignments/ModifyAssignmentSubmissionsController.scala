package uk.ac.warwick.tabula.web.controllers.cm2.admin.assignments

import javax.validation.Valid

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Controller
import org.springframework.validation.Errors
import org.springframework.web.bind.annotation._
import uk.ac.warwick.tabula.cm2.web.Routes
import uk.ac.warwick.tabula.commands._
import uk.ac.warwick.tabula.commands.cm2.assignments.{ModifyAssignmentSubmissionsCommand, _}
import uk.ac.warwick.tabula.data.model._
import uk.ac.warwick.tabula.web.Mav

abstract class AbstractAssignmentSubmissionsController extends AbstractAssignmentController {

  type ModifyAssignmentSubmissionsCommand = Appliable[Assignment] with ModifyAssignmentSubmissionsCommandState with PopulateOnForm
  validatesSelf[SelfValidating]

  @ModelAttribute("command") def command(@PathVariable assignment: Assignment): ModifyAssignmentSubmissionsCommand =
    ModifyAssignmentSubmissionsCommand(mandatory(assignment))

  def showForm(form: ModifyAssignmentSubmissionsCommand, mode: String): Mav = {
    val assignment = form.assignment
    Mav("cm2/admin/assignments/assignment_submissions_details",
      "module" -> assignment.module,
      "mode" -> mode)
      .crumbsList(Breadcrumbs.assignment(assignment))
  }

  def submit(cmd: ModifyAssignmentSubmissionsCommand, errors: Errors, mav: Mav, mode: String): Mav = {
    if (errors.hasErrors) showForm(cmd, mode)
    else {
      cmd.apply()
      mav
    }
  }

}


@Profile(Array("cm2Enabled"))
@Controller
@RequestMapping(value = Array("/${cm2.prefix}/admin/assignments/{assignment}"))
class ModifyAssignmentSubmissionsController extends AbstractAssignmentSubmissionsController {

  @RequestMapping(method = Array(GET), value = Array("/new/submissions"))
  def form(
    @PathVariable assignment: Assignment,
    @ModelAttribute("command") cmd: ModifyAssignmentSubmissionsCommand
  ): Mav = {
    cmd.populate()
    showForm(cmd, createMode)
  }


  @RequestMapping(method = Array(GET), value = Array("/edit/submissions"))
  def formEdit(
    @PathVariable assignment: Assignment,
    @ModelAttribute("command") cmd: ModifyAssignmentSubmissionsCommand
  ): Mav = {
    cmd.populate()
    showForm(cmd, editMode)
  }

  @RequestMapping(method = Array(POST), value = Array("/new/submissions"), params = Array(ManageAssignmentMappingParameters.createAndAddOptions, "action!=refresh", "action!=update"))
  def submitAndAddSubmissions(@Valid @ModelAttribute("command") cmd: ModifyAssignmentSubmissionsCommand, errors: Errors, @PathVariable assignment: Assignment): Mav =
    submit(cmd, errors, RedirectForce(Routes.admin.assignment.createOrEditOptions(assignment, createMode)), createMode)

  @RequestMapping(method = Array(POST), value = Array("/new/submissions"), params = Array(ManageAssignmentMappingParameters.createAndAddSubmissions, "action!=refresh", "action!=update"))
  def saveAndExit(@Valid @ModelAttribute("command") cmd: ModifyAssignmentSubmissionsCommand, errors: Errors, @PathVariable assignment: Assignment): Mav =
    submit(cmd, errors, Redirect(Routes.admin.assignment.submissionsandfeedback(assignment)), createMode)


  @RequestMapping(method = Array(POST), value = Array("/edit/submissions"), params = Array(ManageAssignmentMappingParameters.editAndAddOptions, "action!=refresh", "action!=update"))
  def submitAndAddSubmissionsForEdit(@Valid @ModelAttribute("command") cmd: ModifyAssignmentSubmissionsCommand, errors: Errors, @PathVariable assignment: Assignment): Mav =
    submit(cmd, errors, RedirectForce(Routes.admin.assignment.createOrEditOptions(assignment, editMode)), editMode)

  @RequestMapping(method = Array(POST), value = Array("/edit/submissions"), params = Array(ManageAssignmentMappingParameters.editAndAddSubmissions, "action!=refresh", "action!=update"))
  def saveAndExitForEdit(@Valid @ModelAttribute("command") cmd: ModifyAssignmentSubmissionsCommand, errors: Errors, @PathVariable assignment: Assignment): Mav =
    submit(cmd, errors, Redirect(Routes.admin.assignment.submissionsandfeedback(assignment)), editMode)

}