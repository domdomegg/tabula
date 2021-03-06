package uk.ac.warwick.tabula.web.controllers.mitcircs

import javax.validation.Valid
import org.springframework.stereotype.Controller
import org.springframework.validation.Errors
import org.springframework.web.bind.annotation.{ModelAttribute, PathVariable, RequestMapping}
import uk.ac.warwick.spring.Wire
import uk.ac.warwick.tabula.commands.mitcircs.RenderMitCircsAttachmentCommand
import uk.ac.warwick.tabula.commands.mitcircs.submission._
import uk.ac.warwick.tabula.commands.{Appliable, SelfValidating}
import uk.ac.warwick.tabula.data.model.mitcircs.{IssueType, MitCircsContact, MitigatingCircumstancesSubmission}
import uk.ac.warwick.tabula.data.model.{Member, Module, StudentMember}
import uk.ac.warwick.tabula.profiles.web.Routes
import uk.ac.warwick.tabula.services.fileserver.{RenderableAttachment, RenderableFile}
import uk.ac.warwick.tabula.services.mitcircs.MitCircsSubmissionService
import uk.ac.warwick.tabula.web.Mav
import uk.ac.warwick.tabula.web.controllers.BaseController
import uk.ac.warwick.tabula.web.controllers.profiles.ProfileBreadcrumbs
import uk.ac.warwick.tabula.web.controllers.profiles.profile.AbstractViewProfileController
import uk.ac.warwick.tabula.{AcademicYear, CurrentUser, ItemNotFoundException}

import scala.collection.immutable.ListMap

abstract class AbstractMitCircsFormController extends AbstractViewProfileController {

  validatesSelf[SelfValidating]

  var mitCircsSubmissionService: MitCircsSubmissionService = Wire[MitCircsSubmissionService]

  @ModelAttribute("registeredModules")
  def registeredModules(@PathVariable student: StudentMember): ListMap[AcademicYear, Seq[Module]] = {
    val builder = ListMap.newBuilder[AcademicYear, Seq[Module]]

    student.moduleRegistrationsByYear(None)
      .filter { mr => Option(mr.agreedMark).isEmpty && Option(mr.agreedGrade).isEmpty }
      .groupBy(_.academicYear)
      .mapValues(_.map(_.module).toSeq.sorted)
      .toSeq
      .sortBy { case (year, _) => -year.startYear }
      .foreach { case (year, modules) => builder += year -> modules }

    builder.result()
  }

  @ModelAttribute("previousSubmissions")
  def previousSubmissions(
    @PathVariable student: StudentMember,
    @PathVariable(required=false) submission: MitigatingCircumstancesSubmission
  ): Set[MitigatingCircumstancesSubmission] = {
    val allSubmissions = mitCircsSubmissionService.submissionsForStudent(student)
    allSubmissions.toSet -- Option(submission).toSet
  }

  @ModelAttribute("personalTutors")
  def personalTutors(@PathVariable student: StudentMember): Set[Member] = {
    relationshipService.getStudentRelationshipTypeByUrlPart("tutor")
      .map(tutor => relationshipService.findCurrentRelationships(tutor, student))
      .getOrElse(Nil)
      .flatMap(_.agentMember)
      .toSet
  }

  @RequestMapping
  def form(@ModelAttribute("student") student: StudentMember): Mav = {
    Mav("mitcircs/submissions/form", Map(
      "issueTypes" -> IssueType.validIssueTypes(student),
      "possibleContacts" -> MitCircsContact.values,
      "department" -> student.homeDepartment.subDepartmentsContaining(student).find(_.enableMitCircs),
    )).crumbs(breadcrumbsStudent(activeAcademicYear, student.mostSignificantCourse, ProfileBreadcrumbs.Profile.PersonalCircumstances): _*)
  }

}

@Controller
@RequestMapping(value = Array("/profiles/view/{student}/personalcircs/mitcircs/new"))
class CreateMitCircsController extends AbstractMitCircsFormController {

  type CreateCommand = Appliable[MitigatingCircumstancesSubmission] with MitCircsSubmissionState with SelfValidating

  @ModelAttribute("command") def create(@PathVariable student: StudentMember, user: CurrentUser): CreateCommand =
    CreateMitCircsSubmissionCommand(mandatory(student), user.apparentUser)

  @ModelAttribute("student") def student(@PathVariable student: StudentMember): StudentMember = student

  @RequestMapping(method = Array(POST))
  def save(@Valid @ModelAttribute("command") cmd: CreateCommand, errors: Errors, @PathVariable student: StudentMember): Mav = {
    if (errors.hasErrors) form(student)
    else {
      val submission = cmd.apply()
      RedirectForce(Routes.Profile.PersonalCircumstances.view(submission))
    }
  }
}

@Controller
@RequestMapping(value = Array("/profiles/view/{student}/personalcircs/mitcircs/edit/{submission}"))
class EditMitCircsController extends AbstractMitCircsFormController {

  type EditCommand = Appliable[MitigatingCircumstancesSubmission] with EditMitCircsSubmissionState with SelfValidating

  @ModelAttribute("command") def edit(
    @PathVariable submission: MitigatingCircumstancesSubmission,
    @PathVariable student: StudentMember,
    user: CurrentUser
  ): EditCommand = {
    mustBeLinked(submission, student)
    if (!submission.isEditable(user.apparentUser)) throw new ItemNotFoundException(submission, "Not displaying mitigating circumstances submission as it is not currently editable")
    EditMitCircsSubmissionCommand(submission, user.apparentUser)
  }

  @ModelAttribute("student") def student(@PathVariable submission: MitigatingCircumstancesSubmission): StudentMember =
    submission.student

  @ModelAttribute("lastUpdatedByOther") def lastUpdatedByOther(@PathVariable submission: MitigatingCircumstancesSubmission, user: CurrentUser): Boolean = {
    val studentUser = submission.student.asSsoUser
    user.apparentUser == studentUser && submission.lastModifiedBy != studentUser
  }

  @RequestMapping(method = Array(POST))
  def save(@Valid @ModelAttribute("command") cmd: EditCommand, errors: Errors, @PathVariable submission: MitigatingCircumstancesSubmission): Mav = {
    if (errors.hasErrors) form(submission.student)
    else {
      val submission = cmd.apply()
      RedirectForce(Routes.Profile.PersonalCircumstances.view(submission))
    }
  }

}

@Controller
@RequestMapping(Array("/mitcircs/submission/{submission}/supporting-file/{filename}"))
class MitCircsAttachmentController extends BaseController {

  type RenderAttachmentCommand = Appliable[Option[RenderableAttachment]]

  @ModelAttribute("renderAttachmentCommand")
  def attachmentCommand(
    @PathVariable submission: MitigatingCircumstancesSubmission,
    @PathVariable filename: String
  ): RenderAttachmentCommand =
    RenderMitCircsAttachmentCommand(mandatory(submission), mandatory(filename))

  @RequestMapping(method = Array(GET))
  def supportingFile(@ModelAttribute("renderAttachmentCommand") attachmentCommand: RenderAttachmentCommand, @PathVariable filename: String): RenderableFile =
    attachmentCommand.apply().getOrElse(throw new ItemNotFoundException())

}

