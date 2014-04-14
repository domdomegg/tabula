package uk.ac.warwick.tabula.coursework.commands.assignments


import uk.ac.warwick.tabula.AutowiringFeaturesComponent
import uk.ac.warwick.tabula.FeaturesComponent
import uk.ac.warwick.tabula.commands.Appliable
import uk.ac.warwick.tabula.commands.ComposableCommand
import uk.ac.warwick.tabula.commands.ReadOnly
import uk.ac.warwick.tabula.commands.Unaudited
import uk.ac.warwick.tabula.data.model.StudentCourseYearDetails
import uk.ac.warwick.tabula.permissions.Permissions
import uk.ac.warwick.tabula.services.AssignmentMembershipServiceComponent
import uk.ac.warwick.tabula.services.AssignmentServiceComponent
import uk.ac.warwick.tabula.services.AutowiringAssignmentMembershipServiceComponent
import uk.ac.warwick.tabula.services.AutowiringAssignmentServiceComponent
import uk.ac.warwick.tabula.system.permissions.PermissionsChecking
import uk.ac.warwick.tabula.system.permissions.RequiresPermissionsChecking
import uk.ac.warwick.userlookup.User
import uk.ac.warwick.tabula.coursework.web.controllers.StudentCourseworkCommand.StudentAssignments
import uk.ac.warwick.tabula.coursework.web.controllers.{StudentCourseworkCommandInternal, StudentCourseworkCommandHelper}

object StudentCourseworkGadgetCommand {
	def apply(studentCourseYearDetails: StudentCourseYearDetails): Appliable[StudentAssignments] =
		new StudentCourseworkGadgetCommandInternal(studentCourseYearDetails)
			with ComposableCommand[StudentAssignments]
			with AutowiringFeaturesComponent
			with AutowiringAssignmentServiceComponent
			with AutowiringAssignmentMembershipServiceComponent
			with StudentCourseworkCommandHelper
			with StudentCourseworkGadgetCommandPermissions
			with ReadOnly with Unaudited
}

class StudentCourseworkGadgetCommandInternal(val studentCourseYearDetails: StudentCourseYearDetails)
	extends StudentCourseworkGadgetCommandState with StudentCourseworkCommandInternal{
	self: AssignmentServiceComponent with
		AssignmentMembershipServiceComponent with
		FeaturesComponent with
		StudentCourseworkCommandHelper =>

	override lazy val overridableAssignmentsWithFeedback = {
		assignmentService.getAssignmentsWithFeedback(studentCourseYearDetails)
	}

	override lazy val overridableEnrolledAssignments = {
		val allAssignments = assignmentMembershipService.getEnrolledAssignments(studentCourseYearDetails.studentCourseDetails.student.asSsoUser)
		assignmentService.filterAssignmentsByCourseAndYear(allAssignments, studentCourseYearDetails)
	}

	override lazy val overridableAssignmentsWithSubmission = assignmentService.getAssignmentsWithSubmission(studentCourseYearDetails)

	override val user: User = student.asSsoUser
	override val universityId: String = student.universityId

}

trait StudentCourseworkGadgetCommandState {
	def studentCourseYearDetails: StudentCourseYearDetails
	def student = studentCourseYearDetails.studentCourseDetails.student
}

trait StudentCourseworkGadgetCommandPermissions extends RequiresPermissionsChecking {
	self: StudentCourseworkGadgetCommandState =>
	def permissionsCheck(p: PermissionsChecking) {
		p.PermissionCheck(Permissions.Profiles.Read.Coursework, student)
		p.PermissionCheck(Permissions.Submission.Read, student)
		p.PermissionCheck(Permissions.Feedback.Read, student)
		p.PermissionCheck(Permissions.Extension.Read, student)
	}
}