package uk.ac.warwick.tabula.web.controllers.groups.admin

import org.joda.time.DateTime
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.{ModelAttribute, PathVariable, RequestMapping}
import uk.ac.warwick.tabula.AcademicYear
import uk.ac.warwick.tabula.data.model.Module
import uk.ac.warwick.tabula.groups.web.Routes
import uk.ac.warwick.tabula.services.AutowiringUserSettingsServiceComponent
import uk.ac.warwick.tabula.web.controllers.AcademicYearScopedController
import uk.ac.warwick.tabula.web.controllers.groups.GroupsController

@Controller
@RequestMapping(value=Array("/groups/admin/module/{module}", "/groups/admin/module/{module}/groups"))
class GroupsAdminModuleHomeController extends GroupsController
	with AcademicYearScopedController with AutowiringUserSettingsServiceComponent {

	@ModelAttribute("activeAcademicYear")
	override def activeAcademicYear: Option[AcademicYear] = retrieveActiveAcademicYear(None)

	@RequestMapping
	def adminModule(@PathVariable module: Module, @ModelAttribute("activeAcademicYear") academicYear: Option[AcademicYear]) = {
		Redirect(Routes.admin.module(module, academicYear.getOrElse(AcademicYear.guessSITSAcademicYearByDate(DateTime.now))))
	}
}
