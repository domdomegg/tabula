package uk.ac.warwick.tabula.roles

import uk.ac.warwick.tabula.permissions.Permissions._
import uk.ac.warwick.tabula.data._
import uk.ac.warwick.tabula.data.model.StudentRelationshipType
import uk.ac.warwick.tabula.permissions.PermissionsSelector
import uk.ac.warwick.tabula.JavaImports

object StudentRelationshipAgent {

	final def profileReadOnlyPermissions(relationshipType: PermissionsSelector[StudentRelationshipType]) = Seq(
		Profiles.Read.Core,
		Profiles.Read.Usercode,
		Profiles.Read.Timetable,

		Profiles.Read.StudentCourseDetails.Core,
		Profiles.Read.StudentCourseDetails.Status,
		Profiles.StudentRelationship.Read(PermissionsSelector.Any[StudentRelationshipType]), // Can read any relationship type for this student

		Profiles.MeetingRecord.Read(relationshipType),
		Profiles.MeetingRecord.ReadDetails(relationshipType),

		SmallGroups.Read,
		Profiles.Read.SmallGroups,
		SmallGroupEvents.ViewRegister,
		Profiles.Read.Coursework,
		Profiles.Read.AccreditedPriorLearning,

		Profiles.Read.ModuleRegistration.Core,
		Profiles.Read.ModuleRegistration.Results,

		MemberNotes.Read,

		MonitoringPoints.View,

		// Can read Coursework info for student
		Submission.Read,
		Feedback.Read,
		Extension.Read
	)

}

/**
 * Granted this role when a member is the agent in a current relationship with an active student
 */
case class StudentRelationshipAgent(student: model.Member, relationshipType: StudentRelationshipType)
	extends BuiltInRole(StudentRelationshipAgentRoleDefinition(relationshipType), student)

case class StudentRelationshipAgentRoleDefinition(relationshipType: PermissionsSelector[StudentRelationshipType])
	extends SelectorBuiltInRoleDefinition(relationshipType) {

	override def description =
		if (relationshipType.isWildcard) "Relationship agent (any relationship)"
		else relationshipType.description

	val readOnlyPermissions = StudentRelationshipAgent.profileReadOnlyPermissions(relationshipType)
	val permissionsForCurrentRelationships = Seq (
		Profiles.Read.NextOfKin,
		Profiles.Read.HomeAddress,
		Profiles.Read.TermTimeAddress,
		Profiles.Read.TelephoneNumber,
		Profiles.Read.MobileNumber,

		Profiles.MeetingRecord.Create(relationshipType),
		Profiles.MeetingRecord.Update(relationshipType),
		Profiles.MeetingRecord.Delete(relationshipType),

		Profiles.ScheduledMeetingRecord.Create(relationshipType),
		Profiles.ScheduledMeetingRecord.Update(relationshipType),
		Profiles.ScheduledMeetingRecord.Delete(relationshipType),

		MemberNotes.Create,

		MonitoringPoints.Record
	)

	GrantsScopedPermission(
		readOnlyPermissions ++ permissionsForCurrentRelationships :_*
	)
	final def canDelegateThisRolesPermissions: JavaImports.JBoolean = true

}

/**
 * Granted this role when a member has had a relationship with the student at some point. The student doesn't need
 * to be active for this role to be granted.
 */
case class HistoricStudentRelationshipAgent(student: model.Member, relationshipType: StudentRelationshipType)
	extends BuiltInRole(HistoricStudentRelationshipAgentRoleDefinition(relationshipType), student)

case class HistoricStudentRelationshipAgentRoleDefinition(relationshipType: PermissionsSelector[StudentRelationshipType])
	extends SelectorBuiltInRoleDefinition(relationshipType) {

	override def description =
		if (relationshipType.isWildcard) "Previous relationship agent (any relationship)"
		else s"Previous ${relationshipType.description}"

	GrantsScopedPermission(
		StudentRelationshipAgent.profileReadOnlyPermissions(relationshipType):_*
	)
	final def canDelegateThisRolesPermissions: JavaImports.JBoolean = true

}