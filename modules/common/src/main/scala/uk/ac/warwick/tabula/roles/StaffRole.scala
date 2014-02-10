package uk.ac.warwick.tabula.roles

import uk.ac.warwick.tabula.data._
import uk.ac.warwick.tabula.permissions.Permissions._
import uk.ac.warwick.tabula.permissions.PermissionsSelector
import uk.ac.warwick.tabula.data.model.StudentRelationshipType

case class StaffRole(department: model.Department) extends BuiltInRole(StaffRoleDefinition, department)

case object StaffRoleDefinition extends UnassignableBuiltInRoleDefinition {

	override def description = "Staff Member"

	GrantsScopelessPermission(
		UserPicker,
		MonitoringPointSetTemplates.View
	)

	GrantsGlobalPermission(
		Profiles.Search,
		Profiles.Read.Core, // As per discussion in TAB-753, anyone at the University can see anyone else's core information

		// TAB-1619
		Profiles.Read.Usercode,
		Profiles.Read.StudentCourseDetails.Core,
		Profiles.StudentRelationship.Read(PermissionsSelector.Any[StudentRelationshipType]),

		// TAB-128 University Disability Services confirm disability status should be visible to staff generally
		Profiles.Read.Disability
	)

	GrantsScopedPermission(
		Profiles.Read.StudentCourseDetails.Status,

		// TAB-1619
		Profiles.Read.Timetable,
		Profiles.Read.SmallGroups
	)
}
