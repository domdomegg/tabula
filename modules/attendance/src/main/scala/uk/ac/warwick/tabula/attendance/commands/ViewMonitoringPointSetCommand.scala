package uk.ac.warwick.tabula.attendance.commands

import scala.collection.JavaConverters._

import uk.ac.warwick.tabula.data.model.attendance.MonitoringPointSet
import uk.ac.warwick.tabula.commands.{Unaudited, ComposableCommand, CommandInternal}
import uk.ac.warwick.tabula.system.permissions.{PermissionsCheckingMethods, RequiresPermissionsChecking, PermissionsChecking}
import uk.ac.warwick.tabula.permissions.Permissions
import uk.ac.warwick.tabula.AcademicYear
import org.joda.time.DateTime
import uk.ac.warwick.tabula.services.AutowiringTermServiceComponent
import uk.ac.warwick.tabula.data.model.Department

object ViewMonitoringPointSetCommand {
	def apply(set: MonitoringPointSet) = new ViewMonitoringPointSetCommand(set)
		with ComposableCommand[MonitoringPointSet]
		with Unaudited
		with ViewMonitoringPointSetPermissions
		with ViewMonitoringPointSetState
		with AutowiringTermServiceComponent
}

/**
 * Simply returns a point set as passed in, which would be pointless if it weren't
 * for the permissions checks we do in the accompanying trait.
 */
abstract class ViewMonitoringPointSetCommand(val set: MonitoringPointSet)
	extends CommandInternal[MonitoringPointSet] with ViewMonitoringPointSetState {
	def applyInternal = set
}

trait ViewMonitoringPointSetState extends GroupMonitoringPointsByTerm {
	def set: MonitoringPointSet

	var academicYear: AcademicYear = AcademicYear.guessByDate(DateTime.now)

	// Just used to access week render setting
	var department: Department = set.route.department

	def academicYearToUse = set.academicYear

	def monitoringPointsByTerm = groupByTerm(set.points.asScala, academicYearToUse)
}

trait ViewMonitoringPointSetPermissions extends RequiresPermissionsChecking with PermissionsCheckingMethods {
	self: ViewMonitoringPointSetState =>

	def permissionsCheck(p: PermissionsChecking) {
		p.PermissionCheck(Permissions.MonitoringPoints.View, mandatory(set))
	}

}
