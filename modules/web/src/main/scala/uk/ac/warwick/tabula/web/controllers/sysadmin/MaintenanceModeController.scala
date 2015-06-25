package uk.ac.warwick.tabula.web.controllers.sysadmin

import javax.validation.Valid

import org.joda.time.DateTime
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.stereotype.Controller
import org.springframework.validation.Errors
import org.springframework.web.bind.annotation.{ModelAttribute, RequestMapping}
import uk.ac.warwick.spring.Wire
import uk.ac.warwick.tabula.DateFormats
import uk.ac.warwick.tabula.commands.{Command, ReadOnly, SelfValidating, Unaudited}
import uk.ac.warwick.tabula.permissions._
import uk.ac.warwick.tabula.services.{MaintenanceModeMessage, MaintenanceModeService}
import uk.ac.warwick.tabula.validators.WithinYears
import uk.ac.warwick.util.queue.Queue

class MaintenanceModeCommand(service: MaintenanceModeService) extends Command[Unit] with ReadOnly with Unaudited with SelfValidating {

	PermissionCheck(Permissions.ManageMaintenanceMode)

	val DefaultMaintenanceMinutes = 30

	var queue = Wire.named[Queue]("settingsSyncTopic")

	var enable: Boolean = service.enabled

	@WithinYears(maxFuture = 1, maxPast = 1) @DateTimeFormat(pattern = DateFormats.DateTimePicker)
	var until: DateTime = service.until getOrElse DateTime.now.plusMinutes(DefaultMaintenanceMinutes)

	var message: String = service.message.orNull

	def applyInternal() {
		if (!enable) {
			message = null
			until = null
		}
		service.message = Option(message)
		service.until = Option(until)
		if (enable) service.enable
		else service.disable

		queue.send(new MaintenanceModeMessage(service))
	}

	def validate(errors: Errors) {

	}
}

@Controller
@RequestMapping(Array("/sysadmin/maintenance"))
class MaintenanceModeController extends BaseSysadminController {
	var service = Wire.auto[MaintenanceModeService]

	validatesSelf[MaintenanceModeCommand]

	@ModelAttribute def cmd = new MaintenanceModeCommand(service)

	@RequestMapping(method = Array(GET, HEAD))
	def showForm(form: MaintenanceModeCommand, errors: Errors) =
		Mav("sysadmin/maintenance").crumbs(Breadcrumbs.Current("Sysadmin maintenance mode"))
			.noLayoutIf(ajax)

	@RequestMapping(method = Array(POST))
	def submit(@Valid form: MaintenanceModeCommand, errors: Errors) = {
		if (errors.hasErrors)
			showForm(form, errors)
		else {
			form.apply()
			Redirect("/sysadmin")
		}
	}
}