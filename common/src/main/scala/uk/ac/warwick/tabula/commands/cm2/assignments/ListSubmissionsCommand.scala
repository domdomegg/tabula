package uk.ac.warwick.tabula.commands.cm2.assignments

import java.util.concurrent.TimeoutException

import uk.ac.warwick.tabula.commands._
import uk.ac.warwick.tabula.data.model.Assignment
import uk.ac.warwick.tabula.helpers.cm2.SubmissionListItem
import uk.ac.warwick.tabula.permissions.Permissions
import uk.ac.warwick.tabula.services.elasticsearch.{AuditEventQueryServiceComponent, AutowiringAuditEventQueryServiceComponent}
import uk.ac.warwick.tabula.services.{AutowiringSubmissionServiceComponent, SubmissionServiceComponent}
import uk.ac.warwick.tabula.system.permissions.{PermissionsChecking, PermissionsCheckingMethods, RequiresPermissionsChecking}

import scala.concurrent.Await
import scala.concurrent.duration._

object ListSubmissionsCommand {
	type CommandType = Appliable[Seq[SubmissionListItem]] with ListSubmissionsRequest

	def apply(assignment: Assignment) =
		new ListSubmissionsCommandInternal(assignment)
			with ComposableCommand[Seq[SubmissionListItem]]
			with ListSubmissionsRequest
			with ListSubmissionsPermissions
			with AutowiringAuditEventQueryServiceComponent
			with AutowiringSubmissionServiceComponent
			with Unaudited with ReadOnly
}

trait ListSubmissionsState {
	def assignment: Assignment
}

trait ListSubmissionsRequest extends ListSubmissionsState {
	var checkIndex: Boolean = true
}

abstract class ListSubmissionsCommandInternal(val assignment: Assignment)
	extends CommandInternal[Seq[SubmissionListItem]]
		with ListSubmissionsState {
	self: ListSubmissionsRequest with AuditEventQueryServiceComponent with SubmissionServiceComponent =>

	override def applyInternal(): Seq[SubmissionListItem] = {
		val submissions = submissionService.loadSubmissionsForAssignment(assignment)

		val downloads =
			if (checkIndex) try {
				Await.result(auditEventQueryService.adminDownloadedSubmissions(assignment, submissions), 15.seconds)
			} catch { case _: TimeoutException => Nil }
			else Nil

		submissions.map { submission =>
			SubmissionListItem(submission, downloads.contains(submission))
		}
	}
}

trait ListSubmissionsPermissions extends RequiresPermissionsChecking with PermissionsCheckingMethods {
	self: ListSubmissionsState =>

	override def permissionsCheck(p: PermissionsChecking): Unit = {
		p.PermissionCheck(Permissions.Submission.Read, mandatory(assignment))
	}
}