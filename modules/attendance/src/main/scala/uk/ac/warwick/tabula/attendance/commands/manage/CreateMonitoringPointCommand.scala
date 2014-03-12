package uk.ac.warwick.tabula.attendance.commands.manage

import uk.ac.warwick.tabula.data.model.attendance.{MonitoringPointType, MonitoringPointSet, MonitoringPoint}
import uk.ac.warwick.tabula.commands._
import org.springframework.validation.Errors
import scala.collection.JavaConverters._
import org.joda.time.DateTime
import uk.ac.warwick.tabula.system.permissions.{PermissionsChecking, PermissionsCheckingMethods, RequiresPermissionsChecking}
import uk.ac.warwick.tabula.permissions.Permissions
import uk.ac.warwick.tabula.services.{AutowiringModuleAndDepartmentServiceComponent, AutowiringSmallGroupServiceComponent, AutowiringMonitoringPointServiceComponent, AutowiringTermServiceComponent}


object CreateMonitoringPointCommand {
	def apply(set: MonitoringPointSet) =
		new CreateMonitoringPointCommand(set)
		with ComposableCommand[MonitoringPoint]
		with CreateMonitoringPointValidation
		with CreateMonitoringPointDescription
		with CreateMonitoringPointPermission
		with AutowiringTermServiceComponent
		with AutowiringMonitoringPointServiceComponent
		with AutowiringSmallGroupServiceComponent
		with AutowiringModuleAndDepartmentServiceComponent
}

/**
 * Create a new monitoring point for the given set.
 */
abstract class CreateMonitoringPointCommand(val set: MonitoringPointSet) extends CommandInternal[MonitoringPoint] with CreateMonitoringPointState {

	override def applyInternal() = {
		val point = new MonitoringPoint
		copyTo(point)
		point.createdDate = new DateTime()
		point.updatedDate = new DateTime()
		set.add(point)
		monitoringPoints.add(point)
		point
	}
}

trait CreateMonitoringPointValidation extends SelfValidating with MonitoringPointValidation {
	self: CreateMonitoringPointState =>

	override def validate(errors: Errors) {
		validateWeek(errors, validFromWeek, "validFromWeek")
		validateWeek(errors, requiredFromWeek, "requiredFromWeek")
		validateWeeks(errors, validFromWeek, requiredFromWeek, "validFromWeek")
		validateName(errors, name, "name")

		pointType match {
			case MonitoringPointType.Meeting =>
				validateTypeMeeting(errors,
					meetingRelationships.asScala, "meetingRelationships",
					meetingFormats.asScala, "meetingFormats",
					meetingQuantity, "meetingQuantity",
					dept
				)
			case MonitoringPointType.SmallGroup =>
				validateTypeSmallGroup(errors,
					smallGroupEventModules, "smallGroupEventModules",
					isAnySmallGroupEventModules,
					smallGroupEventQuantity, "smallGroupEventQuantity",
					dept
				)
			case MonitoringPointType.AssignmentSubmission =>
				validateTypeAssignmentSubmission(errors,
					isSpecificAssignments,
					assignmentSubmissionQuantity, "assignmentSubmissionQuantity",
					assignmentSubmissionModules, "assignmentSubmissionModules",
					assignmentSubmissionAssignments, "assignmentSubmissionAssignments",
					dept
				)
			case _ =>
		}

		if (anyStudentsReportedForThisTerm(set, validFromWeek, academicYear)) {
			errors.rejectValue("validFromWeek", "monitoringPoint.hasReportedCheckpoints.add")
		} else if (set.points.asScala.count(p =>
			p.name == name && p.validFromWeek == validFromWeek && p.requiredFromWeek == requiredFromWeek
		) > 0) {
			errors.rejectValue("name", "monitoringPoint.name.exists")
			errors.rejectValue("validFromWeek", "monitoringPoint.name.exists")
		}
	}

}

trait CreateMonitoringPointPermission extends RequiresPermissionsChecking with PermissionsCheckingMethods {
	self: CreateMonitoringPointState =>

	override def permissionsCheck(p: PermissionsChecking) {
		p.PermissionCheck(Permissions.MonitoringPoints.Manage, mandatory(set))
	}
}

trait CreateMonitoringPointDescription extends Describable[MonitoringPoint] {
	self: CreateMonitoringPointState =>

	override lazy val eventName = "CreateMonitoringPoint"

	override def describe(d: Description) {
		d.monitoringPointSet(set)
		d.property("name", name)
		d.property("validFromWeek", validFromWeek)
		d.property("requiredFromWeek", requiredFromWeek)
		d.property("pointType", pointType)
	}
}

trait CreateMonitoringPointState extends MonitoringPointState with CanPointBeChanged {
	def set: MonitoringPointSet
	val dept = set.route.department
	monitoringPoints.addAll(set.points)
}

