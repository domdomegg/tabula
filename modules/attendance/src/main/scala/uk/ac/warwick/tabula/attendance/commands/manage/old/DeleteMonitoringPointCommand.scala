package uk.ac.warwick.tabula.attendance.commands.manage.old

import uk.ac.warwick.tabula.commands._
import org.springframework.validation.Errors
import uk.ac.warwick.tabula.services.{AutowiringModuleAndDepartmentServiceComponent, AutowiringSmallGroupServiceComponent, AutowiringTermServiceComponent}
import uk.ac.warwick.tabula.data.model.Department
import uk.ac.warwick.tabula.system.permissions.{PermissionsChecking, PermissionsCheckingMethods, RequiresPermissionsChecking}
import uk.ac.warwick.tabula.permissions.Permissions
import uk.ac.warwick.tabula.permissions.CheckablePermission
import scala.collection.JavaConverters._
import uk.ac.warwick.tabula.data.model.attendance.MonitoringPoint

object DeleteMonitoringPointCommand {
	def apply(dept: Department, pointIndex: Int) =
		new DeleteMonitoringPointCommand(dept, pointIndex)
		with ComposableCommand[MonitoringPoint]
		with AutowiringTermServiceComponent
		with AutowiringSmallGroupServiceComponent
		with AutowiringModuleAndDepartmentServiceComponent
		with DeleteMonitoringPointValidation
		with DeleteMonitoringPointPermissions
		with ReadOnly with Unaudited
}

/**
 * Deletes an existing monitoring point from the set of points in the command's state.
 * Does not persist the change (no monitoring point set yet exists)
 */
abstract class DeleteMonitoringPointCommand(val dept: Department, val pointIndex: Int)
	extends CommandInternal[MonitoringPoint] with DeleteMonitoringPointState {

	override def applyInternal() = {
		monitoringPoints.remove(pointIndex)
	}
}

trait DeleteMonitoringPointValidation extends SelfValidating {
	self: DeleteMonitoringPointState =>

	override def validate(errors: Errors) {
		if (!confirm) errors.rejectValue("confirm", "monitoringPoint.delete.confirm")
	}
}

trait DeleteMonitoringPointPermissions extends RequiresPermissionsChecking with PermissionsCheckingMethods {
	self: DeleteMonitoringPointState =>

	override def permissionsCheck(p: PermissionsChecking) {
		p.PermissionCheckAny(
			Seq(CheckablePermission(Permissions.MonitoringPoints.Manage, mandatory(dept))) ++
			dept.routes.asScala.map { route => CheckablePermission(Permissions.MonitoringPoints.Manage, route) }
		)
	}
}

trait DeleteMonitoringPointState extends MonitoringPointState {
	val pointIndex: Int
	var confirm: Boolean = _
}
