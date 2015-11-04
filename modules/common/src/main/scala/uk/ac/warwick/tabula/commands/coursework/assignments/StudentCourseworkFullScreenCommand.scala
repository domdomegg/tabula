package uk.ac.warwick.tabula.commands.coursework.assignments

import uk.ac.warwick.tabula.commands.{Appliable, ComposableCommand, MemberOrUser, ReadOnly, Unaudited}
import uk.ac.warwick.tabula.coursework.web.controllers.StudentCourseworkCommand.StudentAssignments
import uk.ac.warwick.tabula.coursework.web.controllers.{StudentCourseworkCommandHelper, StudentCourseworkCommandInternal}
import uk.ac.warwick.tabula.permissions.Permissions
import uk.ac.warwick.tabula.services.{AssessmentMembershipServiceComponent, AssessmentServiceComponent, AutowiringAssessmentMembershipServiceComponent, AutowiringAssessmentServiceComponent}
import uk.ac.warwick.tabula.system.permissions.{PermissionsChecking, RequiresPermissionsChecking}
import uk.ac.warwick.tabula.{AutowiringFeaturesComponent, FeaturesComponent}
import uk.ac.warwick.userlookup.User

object StudentCourseworkFullScreenCommand {
	def apply(memberOrUser: MemberOrUser): Appliable[StudentAssignments] =
		new StudentCourseworkFullScreenCommandInternal(memberOrUser)
			with ComposableCommand[StudentAssignments]
			with StudentCourseworkFullScreenCommandPermissions
			with AutowiringAssessmentServiceComponent
			with AutowiringAssessmentMembershipServiceComponent
			with AutowiringFeaturesComponent
			with StudentCourseworkCommandHelper
			with ReadOnly with Unaudited
}

class StudentCourseworkFullScreenCommandInternal(val memberOrUser: MemberOrUser) extends StudentCourseworkCommandInternal
	with StudentCourseworkFullScreenCommandState {

	self: AssessmentServiceComponent with
		  AssessmentMembershipServiceComponent with
		  FeaturesComponent with
			StudentCourseworkCommandHelper =>

	override lazy val overridableAssignmentsWithFeedback = assessmentService.getAssignmentsWithFeedback(memberOrUser.universityId)

	override lazy val overridableEnrolledAssignments = assessmentMembershipService.getEnrolledAssignments(memberOrUser.asUser)

	override lazy val overridableAssignmentsWithSubmission = assessmentService.getAssignmentsWithSubmission(memberOrUser.universityId)

	override val universityId: String = memberOrUser.universityId

	override val user: User = memberOrUser.asUser

}

trait StudentCourseworkFullScreenCommandState {
	def memberOrUser: MemberOrUser
}

trait StudentCourseworkFullScreenCommandPermissions extends RequiresPermissionsChecking {
	self: StudentCourseworkFullScreenCommandState =>
	def permissionsCheck(p: PermissionsChecking) {
		memberOrUser.asMember.foreach { member =>
			p.PermissionCheck(Permissions.Profiles.Read.Coursework, member)
			p.PermissionCheck(Permissions.Submission.Read, member)
			p.PermissionCheck(Permissions.AssignmentFeedback.Read, member)
			p.PermissionCheck(Permissions.Extension.Read, member)
		}
	}
}