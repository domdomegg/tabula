package uk.ac.warwick.tabula.commands.coursework.assignments

import java.util.concurrent.TimeoutException

import uk.ac.warwick.tabula.commands._
import uk.ac.warwick.tabula.commands.coursework.assignments.ListSubmissionsCommand._
import uk.ac.warwick.tabula.data.model.{Assignment, Module, Submission}
import uk.ac.warwick.tabula.helpers.DateTimeOrdering._
import uk.ac.warwick.tabula.permissions.Permissions
import uk.ac.warwick.tabula.services.elasticsearch.{AuditEventQueryServiceComponent, AutowiringAuditEventQueryServiceComponent}
import uk.ac.warwick.tabula.system.permissions.{PermissionsChecking, PermissionsCheckingMethods, RequiresPermissionsChecking}

import scala.collection.JavaConverters._
import scala.concurrent.Await
import scala.concurrent.duration._

object ListSubmissionsCommand {
  type CommandType = Appliable[Seq[SubmissionListItem]] with ListSubmissionsRequest

  case class SubmissionListItem(submission: Submission, downloaded: Boolean)

  def apply(module: Module, assignment: Assignment) =
    new ListSubmissionsCommandInternal(module, assignment)
      with ComposableCommand[Seq[SubmissionListItem]]
      with ListSubmissionsRequest
      with ListSubmissionsPermissions
      with AutowiringAuditEventQueryServiceComponent
      with Unaudited with ReadOnly
}

trait ListSubmissionsState {
  def module: Module

  def assignment: Assignment
}

trait ListSubmissionsRequest extends ListSubmissionsState {
  var checkIndex: Boolean = true
}

abstract class ListSubmissionsCommandInternal(val module: Module, val assignment: Assignment)
  extends CommandInternal[Seq[SubmissionListItem]]
    with ListSubmissionsState {
  self: ListSubmissionsRequest with AuditEventQueryServiceComponent =>

  override def applyInternal(): Seq[SubmissionListItem] = {
    val submissions = assignment.submissions.asScala.sortBy(_.submittedDate).reverse
    val downloads =
      if (checkIndex) try {
        Await.result(auditEventQueryService.adminDownloadedSubmissions(assignment, submissions), 15.seconds)
      } catch {
        case _: TimeoutException => Nil
      }
      else Nil

    submissions.map { submission =>
      SubmissionListItem(submission, downloads.contains(submission))
    }
  }
}

trait ListSubmissionsPermissions extends RequiresPermissionsChecking with PermissionsCheckingMethods {
  self: ListSubmissionsState =>

  override def permissionsCheck(p: PermissionsChecking): Unit = {
    mustBeLinked(mandatory(assignment), mandatory(module))
    p.PermissionCheck(Permissions.Submission.Read, assignment)
  }
}