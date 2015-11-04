package uk.ac.warwick.tabula.commands.coursework.markingworkflows

import org.springframework.validation.Errors
import uk.ac.warwick.tabula.JavaImports._
import uk.ac.warwick.tabula.commands._
import uk.ac.warwick.tabula.data.model.{Department, MarkerMap, MarkingWorkflow}
import uk.ac.warwick.tabula.helpers.StringUtils._
import uk.ac.warwick.tabula.permissions.Permissions
import uk.ac.warwick.tabula.services._
import uk.ac.warwick.tabula.system.permissions.{PermissionsChecking, PermissionsCheckingMethods, RequiresPermissionsChecking}

import scala.collection.JavaConverters._

object ReplaceMarkerInMarkingWorkflowCommand {
	def apply(department: Department, markingWorkflow: MarkingWorkflow) =
		new ReplaceMarkerInMarkingWorkflowCommandInternal(department, markingWorkflow)
			with AutowiringUserLookupComponent
			with AutowiringMarkingWorkflowServiceComponent
			with AutowiringAssessmentServiceComponent
			with ComposableCommand[MarkingWorkflow]
			with ReplaceMarkerInMarkingWorkflowValidation
			with ReplaceMarkerInMarkingWorkflowDescription
			with ReplaceMarkerInMarkingWorkflowPermissions
			with ReplaceMarkerInMarkingWorkflowCommandState
			with ReplaceMarkerInMarkingWorkflowCommandRequest
}


class ReplaceMarkerInMarkingWorkflowCommandInternal(val department: Department, val markingWorkflow: MarkingWorkflow)
	extends CommandInternal[MarkingWorkflow] {

	self: MarkingWorkflowServiceComponent with AssessmentServiceComponent with UserLookupComponent
		with ReplaceMarkerInMarkingWorkflowCommandRequest with ReplaceMarkerInMarkingWorkflowCommandState =>

	override def applyInternal() = {
		val oldUser = userLookup.getUserByUserId(oldMarker)
		val newUser = userLookup.getUserByUserId(newMarker)
		def replaceMarker[A <: MarkerMap](markers: JList[A]): Unit = {
			val oldMarkerMap = markers.asScala.find(_.marker_id == oldMarker)
			if (markers.asScala.exists(_.marker_id == newMarker)) {
				// The new marker is an existing marker, so move the students
				oldMarkerMap.foreach(marker => {
					marker.students.knownType.members.foreach(s => markers.asScala.find(_.marker_id == newMarker).get.students.knownType.addUserId(s))
					markers.remove(marker)
				})
			} else {
				oldMarkerMap.foreach(_.marker_id = newMarker)
			}
		}
		affectedAssignments.foreach(assignment => {
			replaceMarker(assignment.firstMarkers)
			replaceMarker(assignment.secondMarkers)
			assessmentService.save(assignment)
		})
		if (markingWorkflow.firstMarkers.includesUser(oldUser)) {
			markingWorkflow.firstMarkers.remove(oldUser)
			markingWorkflow.firstMarkers.add(newUser)
		}
		if (markingWorkflow.hasSecondMarker && markingWorkflow.secondMarkers.includesUser(oldUser)) {
			markingWorkflow.secondMarkers.remove(oldUser)
			markingWorkflow.secondMarkers.add(newUser)
		}
		if (markingWorkflow.hasThirdMarker && markingWorkflow.thirdMarkers.includesUser(oldUser)) {
			markingWorkflow.thirdMarkers.remove(oldUser)
			markingWorkflow.thirdMarkers.add(newUser)
		}
		markingWorkflowService.save(markingWorkflow)
		markingWorkflow
	}

}

trait ReplaceMarkerInMarkingWorkflowValidation extends SelfValidating {

	self: ReplaceMarkerInMarkingWorkflowCommandState with ReplaceMarkerInMarkingWorkflowCommandRequest with UserLookupComponent =>

	override def validate(errors: Errors) {
		if (!oldMarker.hasText) {
			errors.rejectValue("oldMarker", "markingWorkflow.marker.none")
		}
		if (!newMarker.hasText) {
			errors.rejectValue("newMarker", "markingWorkflow.marker.none")
		}
		if (oldMarker.hasText && !allMarkers.exists(u => u.getUserId == oldMarker)) {
			errors.rejectValue("oldMarker", "markingWorkflow.marker.notOldMarker")
		}
		if (newMarker.hasText && !userLookup.getUserByUserId(newMarker).isFoundUser){
			errors.rejectValue("newMarker", "markingWorkflow.marker.unknownUser")
		}
		if (!confirm) {
			errors.rejectValue("confirm", "markingWorkflow.marker.confirm")
		}
	}

}

trait ReplaceMarkerInMarkingWorkflowPermissions extends RequiresPermissionsChecking with PermissionsCheckingMethods {

	self: ReplaceMarkerInMarkingWorkflowCommandState =>

	override def permissionsCheck(p: PermissionsChecking) {
		p.mustBeLinked(mandatory(markingWorkflow), mandatory(department))
		p.PermissionCheck(Permissions.MarkingWorkflow.Manage, markingWorkflow)
	}

}

trait ReplaceMarkerInMarkingWorkflowDescription extends Describable[MarkingWorkflow] {

	self: ReplaceMarkerInMarkingWorkflowCommandState with ReplaceMarkerInMarkingWorkflowCommandRequest =>

	override lazy val eventName = "ReplaceMarkerInMarkingWorkflow"

	override def describe(d: Description) {
		d.markingWorkflow(markingWorkflow)
		d.properties(("assignments", affectedAssignments.map(_.id)), ("oldMarker", oldMarker), ("newMarker", newMarker))
	}
}

trait ReplaceMarkerInMarkingWorkflowCommandState {

	self: MarkingWorkflowServiceComponent =>

	def department: Department
	def markingWorkflow: MarkingWorkflow

	lazy val allMarkers = (markingWorkflow.firstMarkers.users ++ (markingWorkflow.hasSecondMarker match {
		case true => markingWorkflow.secondMarkers.users
		case false => Seq()
	}) ++ (markingWorkflow.hasThirdMarker match {
		case true => markingWorkflow.thirdMarkers.users
		case false => Seq()
	})).distinct.sortBy(u => (u.getLastName, u.getFirstName))

	lazy val affectedAssignments = markingWorkflowService.getAssignmentsUsingMarkingWorkflow(markingWorkflow)
}

trait ReplaceMarkerInMarkingWorkflowCommandRequest {
	var oldMarker: String = _
	var newMarker: String = _
	var confirm: Boolean = false
}