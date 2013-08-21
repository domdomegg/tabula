package uk.ac.warwick.tabula.attendance.commands

import uk.ac.warwick.tabula.commands.CommandInternal
import uk.ac.warwick.tabula.commands.Appliable
import uk.ac.warwick.tabula.services.RouteServiceComponent
import uk.ac.warwick.tabula.services.AutowiringRouteServiceComponent
import uk.ac.warwick.tabula.data.model.attendance.{MonitoringPoint, MonitoringPointSet}
import uk.ac.warwick.tabula.commands.Unaudited
import uk.ac.warwick.tabula.commands.ReadOnly
import uk.ac.warwick.tabula.commands.ComposableCommand
import uk.ac.warwick.tabula.system.permissions.PermissionsChecking
import uk.ac.warwick.tabula.system.permissions.RequiresPermissionsChecking
import uk.ac.warwick.tabula.permissions.Permissions
import uk.ac.warwick.tabula.data.model.Route
import scala.collection.JavaConverters._
import uk.ac.warwick.tabula.helpers.WeekRangesFormatter
import uk.ac.warwick.tabula.data.model.groups.{WeekRange, DayOfWeek}

object GetMonitoringPointsCommand {
	def apply(route: Route, year: Option[Int]) =
		new GetMonitoringPointsCommand(route, year)
			with ComposableCommand[Option[Pair[MonitoringPointSet, Map[String, Seq[MonitoringPoint]]]]]
			with GetMonitoringPointsCommandPermissions
			with AutowiringRouteServiceComponent
			with ReadOnly 
			with Unaudited 
}

abstract class GetMonitoringPointsCommand(val route: Route, val year: Option[Int])
	extends CommandInternal[Option[Pair[MonitoringPointSet, Map[String, Seq[MonitoringPoint]]]]]
	with Appliable[Option[Pair[MonitoringPointSet, Map[String, Seq[MonitoringPoint]]]]]
			with GetMonitoringPointsCommandState {
	self: RouteServiceComponent =>

	def applyInternal(): Option[Pair[MonitoringPointSet, Map[String, Seq[MonitoringPoint]]]] = {
		routeService.findMonitoringPointSet(route, year) match {
			case None => None
			case pso:Some[MonitoringPointSet] => Option(Pair(pso.get, groupByTerm(pso.get)))
		}

	}

	private def groupByTerm(pointSet: MonitoringPointSet) = {
		val wrf = new WeekRangesFormatter(pointSet.academicYear)
		val day = DayOfWeek(1)
		pointSet.points.asScala.groupBy {
			case point => wrf.groupWeekRangesByTerm(Seq(WeekRange(point.week)), day).head._2.getTermTypeAsString
		}
	}

}

trait GetMonitoringPointsCommandPermissions extends RequiresPermissionsChecking {
	self: GetMonitoringPointsCommandState =>
	def permissionsCheck(p: PermissionsChecking) {
		p.PermissionCheck(Permissions.MonitoringPoints.Manage, route)
	}
}

trait GetMonitoringPointsCommandState {
	val route: Route
	val year: Option[Int]
}