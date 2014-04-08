package uk.ac.warwick.tabula.dev.web.commands

import uk.ac.warwick.tabula.commands.{CommandInternal, ComposableCommand, Unaudited}
import uk.ac.warwick.tabula.system.permissions.PubliclyVisiblePermissions
import uk.ac.warwick.tabula.data.model.{AssessmentType, AssessmentComponent}
import uk.ac.warwick.tabula.data.{AutowiringTransactionalComponent, TransactionalComponent}
import uk.ac.warwick.tabula.services.{AutowiringAssignmentMembershipServiceComponent, AssignmentMembershipServiceComponent}
import uk.ac.warwick.tabula.helpers.StringUtils._

class AssessmentComponentCreationFixtureCommandInternal extends CommandInternal[AssessmentComponent] {
	self: AssignmentMembershipServiceComponent with TransactionalComponent =>

	var moduleCode: String = _
	var assessmentGroup = "A"
	var sequence = "A01"
	var departmentCode: String = _
	var assessmentType = AssessmentType.Assignment
	var name: String = _

	def applyInternal() = transactional() {
		val ac = new AssessmentComponent
		ac.moduleCode = moduleCode
		ac.assessmentGroup = assessmentGroup
		ac.sequence = sequence
		ac.departmentCode = departmentCode.maybeText.getOrElse("[A-Za-z]+".r.findFirstIn(moduleCode).get)
		ac.assessmentType = assessmentType
		ac.name = name

		assignmentMembershipService.save(ac)
	}

}

object AssessmentComponentCreationFixtureCommand {
	def apply()={
		new AssessmentComponentCreationFixtureCommandInternal
			with ComposableCommand[AssessmentComponent]
			with AutowiringAssignmentMembershipServiceComponent
			with AutowiringTransactionalComponent
			with Unaudited
			with PubliclyVisiblePermissions
	}
}