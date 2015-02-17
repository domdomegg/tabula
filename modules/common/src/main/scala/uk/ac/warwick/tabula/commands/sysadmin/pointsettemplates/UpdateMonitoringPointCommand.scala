package uk.ac.warwick.tabula.commands.sysadmin.pointsettemplates

import uk.ac.warwick.tabula.data.model.attendance.{MonitoringPointTemplate, MonitoringPointSetTemplate}
import uk.ac.warwick.tabula.commands._
import org.springframework.validation.Errors
import scala.collection.JavaConverters._
import org.joda.time.DateTime
import uk.ac.warwick.tabula.services.{AutowiringMonitoringPointServiceComponent, MonitoringPointServiceComponent}


object UpdateMonitoringPointCommand {
	def apply(template: MonitoringPointSetTemplate, point: MonitoringPointTemplate) =
		new UpdateMonitoringPointCommand(template, point)
		with ComposableCommand[MonitoringPointTemplate]
		with AutowiringMonitoringPointServiceComponent
		with UpdateMonitoringPointValidation
		with UpdateMonitoringPointDescription
		with MonitoringPointSetTemplatesPermissions
}

/**
 * Update a monitoring point
 */
abstract class UpdateMonitoringPointCommand(val template: MonitoringPointSetTemplate, val point: MonitoringPointTemplate)
	extends CommandInternal[MonitoringPointTemplate] with UpdateMonitoringPointState {

	self: MonitoringPointServiceComponent =>

	copyFrom(point)

	override def applyInternal() = {
		copyTo(point)
		point.updatedDate = new DateTime()
		monitoringPointService.saveOrUpdate(point)
		point
	}
}

trait UpdateMonitoringPointValidation extends SelfValidating with MonitoringPointValidation {
	self: UpdateMonitoringPointState =>

	override def validate(errors: Errors) {
		validateWeek(errors, validFromWeek, "validFromWeek")
		validateWeek(errors, requiredFromWeek, "requiredFromWeek")
		validateWeeks(errors, validFromWeek, requiredFromWeek, "validFromWeek")
		validateName(errors, name, "name")

		if (template.points.asScala.count(p =>
			p.name == name && p.validFromWeek == validFromWeek && p.requiredFromWeek == requiredFromWeek && p.id != point.id
		) > 0) {
			errors.rejectValue("name", "monitoringPoint.name.exists")
			errors.rejectValue("validFromWeek", "monitoringPoint.name.exists")
		}
	}
}

trait UpdateMonitoringPointDescription extends Describable[MonitoringPointTemplate] {
	self: UpdateMonitoringPointState =>

	override lazy val eventName = "UpdateMonitoringPoint"

	override def describe(d: Description) {
		d.monitoringPointSetTemplate(template)
		d.monitoringPointTemplate(point)
	}
}

trait UpdateMonitoringPointState {
	def template: MonitoringPointSetTemplate
	def point: MonitoringPointTemplate
	var name: String = _
	var validFromWeek: Int = 0
	var requiredFromWeek: Int = 0

	def copyTo(point: MonitoringPointTemplate) {
		point.name = this.name
		point.validFromWeek = this.validFromWeek
		point.requiredFromWeek = this.requiredFromWeek
	}

	def copyFrom(point: MonitoringPointTemplate) {
		this.name = point.name
		this.validFromWeek = point.validFromWeek
		this.requiredFromWeek = point.requiredFromWeek
	}
}

