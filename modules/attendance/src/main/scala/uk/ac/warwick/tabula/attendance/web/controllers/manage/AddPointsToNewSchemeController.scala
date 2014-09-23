package uk.ac.warwick.tabula.attendance.web.controllers.manage

import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.RequestMapping
import uk.ac.warwick.tabula.attendance.web.controllers.HasMonthNames
import uk.ac.warwick.tabula.JavaImports
import uk.ac.warwick.tabula.attendance.commands.manage.FindPointsResult
import uk.ac.warwick.tabula.data.model.attendance.{AttendanceMonitoringPointStyle, AttendanceMonitoringPointType, AttendanceMonitoringScheme}

@Controller
@RequestMapping(Array("/manage/{department}/{academicYear}/new/{scheme}/points"))
class AddPointsToNewSchemeController extends AbstractManageSchemePointsController with HasMonthNames {

	override protected def render(
		findCommandResult: FindPointsResult,
		scheme: AttendanceMonitoringScheme,
		points:
		JavaImports.JInteger,
		actionCompleted: String
	) = {
		Mav("manage/addpointsoncreate",
			"findResult" -> findCommandResult,
			"allTypes" -> AttendanceMonitoringPointType.values,
			"allStyles" -> AttendanceMonitoringPointStyle.values,
			"ManageSchemeMappingParameters" -> ManageSchemeMappingParameters,
			"newPoints" -> Option(points).getOrElse(0),
			"actionCompleted" -> actionCompleted
		).crumbs(
			Breadcrumbs.Manage.Home,
			Breadcrumbs.Manage.Department(scheme.department),
			Breadcrumbs.Manage.DepartmentForYear(scheme.department, scheme.academicYear)
		)
	}

}