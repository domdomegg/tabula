package uk.ac.warwick.tabula.dev.web.commands

import uk.ac.warwick.tabula.commands.{Unaudited, ComposableCommand, CommandInternal}
import uk.ac.warwick.spring.Wire
import uk.ac.warwick.tabula.services.AssignmentService
import org.joda.time.DateTime
import uk.ac.warwick.tabula.data.{AutowiringTransactionalComponent, Daoisms, TransactionalComponent, DepartmentDao}
import uk.ac.warwick.tabula.data.model.Assignment
import uk.ac.warwick.tabula.system.permissions.PubliclyVisiblePermissions


class UpdateAssignmentCommand extends CommandInternal[Seq[Assignment]] {
	this: TransactionalComponent =>

	type AssignmentUpdate = Assignment => Unit

	val assignmentSrv = Wire[AssignmentService]
	val departmentDao = Wire[DepartmentDao]

	var assignmentName: String = _
	var deptCode: String = _
	var openDate: DateTime = _
	var closeDate: DateTime = _

	protected def applyInternal() = {
		val updateOpenDate = Option(openDate).map(date => (a: Assignment) => a.openDate = date)
		val updateCloseDate = Option(closeDate).map(date => (a: Assignment) => a.closeDate = date)
		// add more optional update actions here

		Seq(updateOpenDate, updateCloseDate).flatten match {
			case Nil => Nil // nothing to do
			case actions: Seq[AssignmentUpdate] => {
				transactional() {
					val dept = departmentDao.getByCode(deptCode).get
					val assignment = assignmentSrv.getAssignmentsByName(assignmentName, dept).head
					for (action <- actions) yield {
						action(assignment)
						assignment
					}
				}
			}

		}
	}
}

object UpdateAssignmentCommand {
	def apply() ={
		new UpdateAssignmentCommand
			with ComposableCommand[Seq[Assignment]]
			with AutowiringTransactionalComponent
			with PubliclyVisiblePermissions
			with Unaudited
	}
}
