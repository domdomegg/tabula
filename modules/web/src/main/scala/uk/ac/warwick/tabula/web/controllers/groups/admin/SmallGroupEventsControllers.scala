package uk.ac.warwick.tabula.web.controllers.groups.admin

import javax.validation.Valid

import org.joda.time.DateTime
import org.springframework.stereotype.Controller
import org.springframework.validation.Errors
import org.springframework.web.bind.annotation.{PathVariable, ModelAttribute, RequestMapping}
import uk.ac.warwick.tabula.AcademicYear
import uk.ac.warwick.tabula.commands.{Appliable, SelfValidating}
import uk.ac.warwick.tabula.data.model.Module
import uk.ac.warwick.tabula.data.model.groups._
import uk.ac.warwick.tabula.commands.groups.admin.{UpdateSmallGroupEventFromExternalSystemCommand, ModifySmallGroupEventCommandState, ModifySmallGroupEventCommand}
import uk.ac.warwick.tabula.groups.web.Routes
import uk.ac.warwick.tabula.helpers.SystemClockComponent
import uk.ac.warwick.tabula.services.timetables.{ScientiaHttpTimetableFetchingServiceComponent, AutowiringScientiaConfigurationComponent}
import uk.ac.warwick.tabula.services.{TermService, AutowiringTermServiceComponent, TermServiceComponent}
import uk.ac.warwick.tabula.web.controllers.groups.GroupsController
import uk.ac.warwick.util.termdates.Term

trait SmallGroupEventsController extends GroupsController {
	self: TermServiceComponent =>

	validatesSelf[SelfValidating]

	@ModelAttribute("allDays") def allDays = DayOfWeek.members

	case class NamedTerm(name: String, term: Term, weekRange: WeekRange)

	@ModelAttribute("allTerms") def allTerms(@PathVariable("smallGroupSet") set: SmallGroupSet) = {
		val year = Option(set.academicYear).getOrElse(AcademicYear.guessSITSAcademicYearByDate(DateTime.now))
		val weeks = termService.getAcademicWeeksForYear(year.dateInTermOne).toMap

		val terms =
			weeks
				.map { case (weekNumber, dates) =>
					(weekNumber, termService.getTermFromAcademicWeekIncludingVacations(weekNumber, year))
				}
				.groupBy { _._2 }
				.map { case (term, weekNumbersAndTerms) =>
					(term, WeekRange(weekNumbersAndTerms.keys.min, weekNumbersAndTerms.keys.max))
				}
				.toSeq
				.sortBy { case (_, weekRange) => weekRange.minWeek.toInt }

		TermService.orderedTermNames.zip(terms).map { case (name, (term, weekRange)) => NamedTerm(name, term, weekRange) }
	}
}

abstract class AbstractCreateSmallGroupEventController extends SmallGroupEventsController with AutowiringTermServiceComponent {

	type CreateSmallGroupEventCommand = Appliable[SmallGroupEvent] with ModifySmallGroupEventCommandState

	@ModelAttribute("createSmallGroupEventCommand") def cmd(
		@PathVariable("module") module: Module,
		@PathVariable("smallGroupSet") set: SmallGroupSet,
		@PathVariable("smallGroup") group: SmallGroup
	): CreateSmallGroupEventCommand =
		ModifySmallGroupEventCommand.create(module, set, group)

	protected def cancelUrl(set: SmallGroupSet): String

	@RequestMapping
	def form(@ModelAttribute("createSmallGroupEventCommand") cmd: CreateSmallGroupEventCommand) = {
		Mav("groups/admin/groups/events/new", "cancelUrl" -> cancelUrl(cmd.set))
			.crumbs(Breadcrumbs.DepartmentForYear(cmd.module.adminDepartment, cmd.academicYear), Breadcrumbs.ModuleForYear(cmd.module, cmd.academicYear))
	}

	protected def submit(cmd: CreateSmallGroupEventCommand, errors: Errors, route: String) = {
		if (errors.hasErrors) form(cmd)
		else {
			cmd.apply()
			RedirectForce(route)
		}
	}

}

@RequestMapping(Array("/groups/admin/module/{module}/groups/new/{smallGroupSet}/events/{smallGroup}/new"))
@Controller
class CreateSmallGroupSetCreateEventController extends AbstractCreateSmallGroupEventController {

	override def cancelUrl(set: SmallGroupSet) = Routes.admin.createAddEvents(set)

	@RequestMapping(method = Array(POST))
	def saveAndExit(@Valid @ModelAttribute("createSmallGroupEventCommand") cmd: CreateSmallGroupEventCommand, errors: Errors, @PathVariable("smallGroupSet") set: SmallGroupSet) =
		submit(cmd, errors, Routes.admin.createAddEvents(set))

}

@RequestMapping(Array("/groups/admin/module/{module}/groups/edit/{smallGroupSet}/events/{smallGroup}/new"))
@Controller
class EditSmallGroupSetCreateEventController extends AbstractCreateSmallGroupEventController {

	override def cancelUrl(set: SmallGroupSet) = Routes.admin.editAddEvents(set)

	@RequestMapping(method = Array(POST))
	def saveAndExit(@Valid @ModelAttribute("createSmallGroupEventCommand") cmd: CreateSmallGroupEventCommand, errors: Errors, @PathVariable("smallGroupSet") set: SmallGroupSet) =
		submit(cmd, errors, Routes.admin.editAddEvents(set))

}

abstract class AbstractEditSmallGroupEventController extends SmallGroupEventsController with AutowiringTermServiceComponent
	with AutowiringScientiaConfigurationComponent with ScientiaHttpTimetableFetchingServiceComponent with SystemClockComponent
	with SyllabusPlusEventCountForModule {

	type EditSmallGroupEventCommand = Appliable[SmallGroupEvent] with ModifySmallGroupEventCommandState

	@ModelAttribute("editSmallGroupEventCommand") def cmd(
		@PathVariable("module") module: Module,
		@PathVariable("smallGroupSet") set: SmallGroupSet,
		@PathVariable("smallGroup") group: SmallGroup,
		@PathVariable("smallGroupEvent") event: SmallGroupEvent
	): EditSmallGroupEventCommand =
		ModifySmallGroupEventCommand.edit(module, set, group, event)

	protected def cancelUrl(set: SmallGroupSet): String

	@RequestMapping
	def form(@ModelAttribute("editSmallGroupEventCommand") cmd: EditSmallGroupEventCommand) = {
		Mav("groups/admin/groups/events/edit", "cancelUrl" -> cancelUrl(cmd.set))
			.crumbs(Breadcrumbs.DepartmentForYear(cmd.module.adminDepartment, cmd.academicYear), Breadcrumbs.ModuleForYear(cmd.module, cmd.academicYear))
	}

	protected def submit(cmd: EditSmallGroupEventCommand, errors: Errors, route: String) = {
		if (errors.hasErrors) form(cmd)
		else {
			cmd.apply()
			RedirectForce(route)
		}
	}

}

@RequestMapping(Array("/groups/admin/module/{module}/groups/new/{smallGroupSet}/events/{smallGroup}/edit/{smallGroupEvent}"))
@Controller
class CreateSmallGroupSetEditEventController extends AbstractEditSmallGroupEventController {

	override def cancelUrl(set: SmallGroupSet) = Routes.admin.createAddEvents(set)

	@RequestMapping(method = Array(POST))
	def saveAndExit(@Valid @ModelAttribute("editSmallGroupEventCommand") cmd: EditSmallGroupEventCommand, errors: Errors, @PathVariable("smallGroupSet") set: SmallGroupSet) =
		submit(cmd, errors, Routes.admin.createAddEvents(set))

	@ModelAttribute("is_edit_set") def isEditingSmallGroupSet = false

}

@RequestMapping(Array("/groups/admin/module/{module}/groups/edit/{smallGroupSet}/events/{smallGroup}/edit/{smallGroupEvent}"))
@Controller
class EditSmallGroupSetEditEventController extends AbstractEditSmallGroupEventController {

	override def cancelUrl(set: SmallGroupSet) = Routes.admin.editAddEvents(set)

	@RequestMapping(method = Array(POST))
	def saveAndExit(@Valid @ModelAttribute("editSmallGroupEventCommand") cmd: EditSmallGroupEventCommand, errors: Errors, @PathVariable("smallGroupSet") set: SmallGroupSet) =
		submit(cmd, errors, Routes.admin.editAddEvents(set))

	@ModelAttribute("is_edit_set") def isEditingSmallGroupSet = true

}

abstract class AbstractUpdateSmallGroupEventFromExternalSystemController extends GroupsController {

	validatesSelf[SelfValidating]

	type UpdateSmallGroupEventFromExternalSystemCommand = Appliable[SmallGroupEvent] with SelfValidating

	@ModelAttribute("command") def cmd(
		@PathVariable("module") module: Module,
		@PathVariable("smallGroupSet") set: SmallGroupSet,
		@PathVariable("smallGroup") group: SmallGroup,
		@PathVariable("smallGroupEvent") event: SmallGroupEvent
	): UpdateSmallGroupEventFromExternalSystemCommand =
		UpdateSmallGroupEventFromExternalSystemCommand(module, set, group, event)

	protected def render(event: SmallGroupEvent) = {
		val set = event.group.groupSet

		Mav("groups/admin/groups/events/update", "cancelUrl" -> postSaveRoute(event))
			.crumbs(Breadcrumbs.DepartmentForYear(set.module.adminDepartment, set.academicYear), Breadcrumbs.ModuleForYear(set.module, set.academicYear))
	}

	protected def postSaveRoute(event: SmallGroupEvent): String

	@RequestMapping
	def form(
		@PathVariable("smallGroupEvent") event: SmallGroupEvent,
		@ModelAttribute("command") cmd: UpdateSmallGroupEventFromExternalSystemCommand
	) = render(event)

	protected def submit(cmd: UpdateSmallGroupEventFromExternalSystemCommand, errors: Errors, event: SmallGroupEvent, route: String) = {
		if (errors.hasErrors) {
			render(event)
		} else {
			cmd.apply()
			RedirectForce(route)
		}
	}

	@RequestMapping(method = Array(POST))
	def save(
		@Valid @ModelAttribute("command") cmd: UpdateSmallGroupEventFromExternalSystemCommand,
		errors: Errors,
		@PathVariable("smallGroupEvent") event: SmallGroupEvent
	) = submit(cmd, errors, event, postSaveRoute(event))

}

@RequestMapping(Array("/groups/admin/module/{module}/groups/new/{smallGroupSet}/events/{smallGroup}/edit/{smallGroupEvent}/import"))
@Controller
class CreateSmallGroupSetUpdateEventFromExternalSystemController extends AbstractUpdateSmallGroupEventFromExternalSystemController {
	override def postSaveRoute(event: SmallGroupEvent): String = Routes.admin.createEditEvent(event)
}

@RequestMapping(Array("/groups/admin/module/{module}/groups/edit/{smallGroupSet}/events/{smallGroup}/edit/{smallGroupEvent}/import"))
@Controller
class EditSmallGroupSetUpdateEventFromExternalSystemController extends AbstractUpdateSmallGroupEventFromExternalSystemController {
	override def postSaveRoute(event: SmallGroupEvent): String = Routes.admin.editEditEvent(event)
}