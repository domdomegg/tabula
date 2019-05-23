package uk.ac.warwick.tabula.web.controllers.mitcircs

import javax.validation.Valid
import org.springframework.stereotype.Controller
import org.springframework.validation.Errors
import org.springframework.web.bind.annotation.{ModelAttribute, PathVariable, PostMapping, RequestMapping}
import uk.ac.warwick.tabula.{AcademicYear, CurrentUser}
import uk.ac.warwick.tabula.commands.SelfValidating
import uk.ac.warwick.tabula.commands.mitcircs.submission.CreateMitCircsPanelCommand
import uk.ac.warwick.tabula.data.model.Department
import uk.ac.warwick.tabula.mitcircs.web.Routes
import uk.ac.warwick.tabula.permissions.Permission
import uk.ac.warwick.tabula.services.{AutowiringMaintenanceModeServiceComponent, AutowiringModuleAndDepartmentServiceComponent, AutowiringUserSettingsServiceComponent}
import uk.ac.warwick.tabula.web.Mav
import uk.ac.warwick.tabula.web.controllers.{AcademicYearScopedController, BaseController, DepartmentScopedController}

import scala.collection.JavaConverters._

abstract class AbstractCreateMitCircsPanelController extends BaseController
  with DepartmentScopedController
  with AcademicYearScopedController
  with AutowiringModuleAndDepartmentServiceComponent
  with AutowiringUserSettingsServiceComponent
  with AutowiringMaintenanceModeServiceComponent {

  validatesSelf[SelfValidating]

  override val departmentPermission: Permission = CreateMitCircsPanelCommand.RequiredPermission

  @ModelAttribute("activeDepartment")
  override def activeDepartment(@PathVariable department: Department): Option[Department] =
    retrieveActiveDepartment(Option(department))

  @ModelAttribute("createCommand")
  def createCommand(
    @PathVariable department: Department,
    @ModelAttribute("activeAcademicYear") activeAcademicYear: Option[AcademicYear],
    user: CurrentUser
  ): CreateMitCircsPanelCommand.Command = CreateMitCircsPanelCommand(department, activeAcademicYear.getOrElse(AcademicYear.now()), user.apparentUser)

  @RequestMapping(params = Array("!submit"))
  def form(@ModelAttribute("createCommand") createCommand: CreateMitCircsPanelCommand.Command, @PathVariable department: Department): Mav = {
    val (hasPanel, noPanel) = createCommand.submissions.asScala.partition(_.panel.isDefined)

    Mav("mitcircs/panel/form",
      "hasPanel" -> hasPanel,
      "noPanel" -> noPanel,
      "academicYear" -> createCommand.year
    ).crumbs(MitCircsBreadcrumbs.Admin.Home(department, active = true))
     .secondCrumbs(academicYearBreadcrumbs(createCommand.year)(Routes.Admin.home(department, _)): _*)
  }

  @PostMapping(params = Array("submit"))
  def create(
    @Valid @ModelAttribute("createCommand") createCommand: CreateMitCircsPanelCommand.Command,
    errors: Errors,
    @PathVariable department: Department,
    @ModelAttribute("activeAcademicYear") activeAcademicYear: Option[AcademicYear],
  ): Mav =
    if (errors.hasErrors) form(createCommand, department)
    else {
      createCommand.apply()
      activeAcademicYear
        .map(y => RedirectForce(Routes.Admin.home(department, y)))
        .getOrElse(RedirectForce(Routes.Admin.home(department)))
    }
}


@Controller
@RequestMapping(Array("/mitcircs/admin/{department}/panel/create"))
class CreateMitCircsPanelForDepartmentController extends AbstractCreateMitCircsPanelController {

  @ModelAttribute("activeAcademicYear")
  override def activeAcademicYear: Option[AcademicYear] =
    retrieveActiveAcademicYear(None)

}

@Controller
@RequestMapping(Array("/mitcircs/admin/{department}/{academicYear:\\d{4}}/panel/create"))
class CreateMitCircsPanelForYearController extends AbstractCreateMitCircsPanelController {

  @ModelAttribute("activeAcademicYear")
  override def activeAcademicYear(@PathVariable academicYear: AcademicYear): Option[AcademicYear] =
    retrieveActiveAcademicYear(Option(academicYear))

}

