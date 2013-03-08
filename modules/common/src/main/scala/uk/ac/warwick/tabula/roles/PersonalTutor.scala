package uk.ac.warwick.tabula.roles

import uk.ac.warwick.tabula.permissions.Permissions._
import uk.ac.warwick.tabula.data._

case class PersonalTutor(student: model.Member) extends BuiltInRole(student, PersonalTutorRoleDefinition)

object PersonalTutorRoleDefinition extends BuiltInRoleDefinition {
	GrantsScopedPermission(
		Profiles.Read.Core,
		Profiles.Read.UniversityId,
		Profiles.Read.NextOfKin,
		Profiles.Read.HomeAddress,
		Profiles.Read.TermTimeAddress,
		Profiles.Read.TelephoneNumber,
		Profiles.Read.MobileNumber,
		Profiles.Read.Usercode
	)
}