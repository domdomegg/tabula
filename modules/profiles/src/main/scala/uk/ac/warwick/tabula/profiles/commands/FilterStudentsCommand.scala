package uk.ac.warwick.tabula.profiles.commands

import uk.ac.warwick.tabula.data.model.StudentMember
import uk.ac.warwick.tabula.commands.{FiltersStudents, CommandInternal, ReadOnly, Unaudited, ComposableCommand}
import uk.ac.warwick.tabula.system.permissions.RequiresPermissionsChecking
import uk.ac.warwick.tabula.system.permissions.PermissionsCheckingMethods
import uk.ac.warwick.tabula.system.permissions.PermissionsChecking
import uk.ac.warwick.tabula.permissions.Permissions
import uk.ac.warwick.tabula.services.{AutowiringModuleAndDepartmentServiceComponent, AutowiringCourseAndRouteServiceComponent, ProfileServiceComponent, AutowiringProfileServiceComponent}
import org.hibernate.criterion.Order._
import uk.ac.warwick.tabula.data.model.Module
import uk.ac.warwick.tabula.data.model.Route
import uk.ac.warwick.tabula.JavaImports._
import uk.ac.warwick.tabula.data.model.SitsStatus
import uk.ac.warwick.tabula.data.model.CourseType
import uk.ac.warwick.tabula.data.model.ModeOfAttendance
import uk.ac.warwick.tabula.data.model.Department
import uk.ac.warwick.tabula.system.BindListener
import org.springframework.validation.BindingResult
import org.hibernate.criterion.Order
import uk.ac.warwick.tabula.data.{AutowiringSitsStatusDaoComponent, AutowiringModeOfAttendanceDaoComponent}

case class FilterStudentsResults(
	students: Seq[StudentMember],
	totalResults: Int
)

object FilterStudentsCommand {
	def apply(department: Department) =
		new FilterStudentsCommand(department)
			with ComposableCommand[FilterStudentsResults]
			with FilterStudentsPermissions
			with AutowiringProfileServiceComponent
			with AutowiringCourseAndRouteServiceComponent
			with AutowiringModeOfAttendanceDaoComponent
			with AutowiringModuleAndDepartmentServiceComponent
			with AutowiringSitsStatusDaoComponent
			with ReadOnly with Unaudited
}

abstract class FilterStudentsCommand(val department: Department) extends CommandInternal[FilterStudentsResults] with FilterStudentsState with BindListener {
	self: ProfileServiceComponent =>

	def applyInternal() = {
		val restrictions = buildRestrictions()

		val totalResults = profileService.countStudentsByRestrictions(
			department = department,
			restrictions = restrictions
		)

		val (offset, students) = profileService.findStudentsByRestrictions(
			department = department,
			restrictions = restrictions,
			orders = buildOrders(),
			maxResults = studentsPerPage,
			startResult = studentsPerPage * (page-1)
		)

		if (offset == 0) page = 1

		FilterStudentsResults(students, totalResults)
	}

	def onBind(result: BindingResult) {
		// Add all non-withdrawn codes to SPR statuses by default
		if (sprStatuses.isEmpty) {
			allSprStatuses.filter { status => !status.code.startsWith("P") && !status.code.startsWith("T") }.foreach { sprStatuses.add }
		}
	}
}

trait FilterStudentsState extends FiltersStudents {
	override def department: Department

	var studentsPerPage = FiltersStudents.DefaultStudentsPerPage
	var page = 1

	val defaultOrder = Seq(asc("lastName"), asc("firstName")) // Don't allow this to be changed atm
	var sortOrder: JList[Order] = JArrayList()

	var courseTypes: JList[CourseType] = JArrayList()
	var routes: JList[Route] = JArrayList()
	var modesOfAttendance: JList[ModeOfAttendance] = JArrayList()
	var yearsOfStudy: JList[JInteger] = JArrayList()
	var sprStatuses: JList[SitsStatus] = JArrayList()
	var modules: JList[Module] = JArrayList()
}

trait FilterStudentsPermissions extends RequiresPermissionsChecking with PermissionsCheckingMethods {
	self: FilterStudentsState =>

	def permissionsCheck(p: PermissionsChecking) {
		p.PermissionCheck(Permissions.Profiles.Search, department)
	}
}