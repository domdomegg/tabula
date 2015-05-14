package uk.ac.warwick.tabula.coursework.commands.assignments.extensions

import scala.collection.JavaConversions._
import uk.ac.warwick.tabula.data.model.{Assignment, Module}
import uk.ac.warwick.tabula.commands._
import uk.ac.warwick.tabula.permissions._
import uk.ac.warwick.spring.Wire
import uk.ac.warwick.tabula.CurrentUser
import uk.ac.warwick.tabula.services.UserLookupService
import uk.ac.warwick.tabula.services.AssessmentMembershipService
import uk.ac.warwick.tabula.ItemNotFoundException
import org.joda.time.Days
import uk.ac.warwick.tabula.coursework.helpers.ExtensionGraph

class ListExtensionsForAssignmentCommand(val module: Module, val assignment: Assignment, val user: CurrentUser)
	extends Command[Seq[ExtensionGraph]] with ReadOnly with Unaudited {

	mustBeLinked(mandatory(assignment), mandatory(module))
	PermissionCheck(Permissions.Extension.Read, assignment)

	if (assignment.openEnded) throw new ItemNotFoundException

	var assignmentMembershipService = Wire.auto[AssessmentMembershipService]
	var userLookup = Wire.auto[UserLookupService]

	def applyInternal() = {
		val assignmentUsers = assignmentMembershipService.determineMembershipUsers(assignment)

		val assignmentMembership = Map() ++ (
			for(assignmentUser <- assignmentUsers)
				yield assignmentUser.getWarwickId -> assignmentUser
		)

		// all the users that aren't members of this assignment, but have submitted work to it
		val extensionsFromNonMembers = assignment.extensions.filterNot(x => assignmentMembership.contains(x.universityId))
		val nonMembers = userLookup.getUsersByWarwickUniIds(extensionsFromNonMembers.map { _.universityId })

		// build lookup of names from non members of the assignment that have submitted work plus members
		val students = nonMembers ++ assignmentMembership

		(for ((universityId, user) <- students) yield {
			// deconstruct the map, bleh
			val extension = assignment.extensions.find(_.universityId == universityId)
			val isAwaitingReview = extension exists (_.awaitingReview)
			val hasApprovedExtension = extension exists (_.approved)
			val hasRejectedExtension = extension exists (_.rejected)

			// use real days not working days, for displayed duration, as markers need to know how late it *actually* would be after deadline
			val duration = extension.flatMap(_.expiryDate).map(Days.daysBetween(assignment.closeDate, _).getDays).getOrElse(0)

			val requestedExtraDuration2 = extension.flatMap(_.expiryDate)

			val requestedExtraDuration = extension match {
				case Some(e) =>
					Days.daysBetween(e.expiryDate.getOrElse(assignment.closeDate), e.requestedExpiryDate).getDays
				case _ => 0
			}

			new ExtensionGraph(universityId, user, assignment.submissionDeadline(user), isAwaitingReview, hasApprovedExtension, hasRejectedExtension, duration, requestedExtraDuration, extension)
		}).toSeq
	}
}
