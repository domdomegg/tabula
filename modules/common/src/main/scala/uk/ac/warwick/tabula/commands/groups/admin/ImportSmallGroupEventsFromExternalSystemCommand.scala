package uk.ac.warwick.tabula.commands.groups.admin

import uk.ac.warwick.tabula.AcademicYear
import uk.ac.warwick.tabula.JavaImports._
import uk.ac.warwick.tabula.commands._
import uk.ac.warwick.tabula.data.Transactions._
import uk.ac.warwick.tabula.data.model.Module
import uk.ac.warwick.tabula.data.model.groups._
import uk.ac.warwick.tabula.helpers.SystemClockComponent
import uk.ac.warwick.tabula.permissions.Permissions
import uk.ac.warwick.tabula.services.{SmallGroupServiceComponent, AutowiringSmallGroupServiceComponent, AutowiringUserLookupComponent, UserLookupComponent}
import uk.ac.warwick.tabula.services.timetables.{ScientiaHttpTimetableFetchingServiceComponent, ModuleTimetableFetchingServiceComponent, AutowiringScientiaConfigurationComponent}
import uk.ac.warwick.tabula.system.permissions.{RequiresPermissionsChecking, PermissionsCheckingMethods, PermissionsChecking}
import uk.ac.warwick.tabula.timetables.{TimetableEventType, TimetableEvent}
import scala.collection.JavaConverters._
import scala.concurrent.duration._

import ImportSmallGroupEventsFromExternalSystemCommand._

import scala.concurrent.Await

object ImportSmallGroupEventsFromExternalSystemCommand {

	val RequiredPermission = Permissions.SmallGroups.Update

	def apply(module: Module, set: SmallGroupSet) =
		new ImportSmallGroupEventsFromExternalSystemCommandInternal(module, set)
			with ComposableCommand[Seq[SmallGroupEvent]]
			with ImportSmallGroupEventsFromExternalSystemPermissions
			with ImportSmallGroupEventsFromExternalSystemDescription
			with CommandSmallGroupEventGenerator
			with AutowiringSmallGroupServiceComponent
			with AutowiringUserLookupComponent
			with AutowiringScientiaConfigurationComponent
			with SystemClockComponent
			with ScientiaHttpTimetableFetchingServiceComponent
			with LookupEventsFromModuleTimetable

	def isValidForYear(academicYear: AcademicYear)(event: TimetableEvent) =
		event.year == academicYear &&
			(event.eventType == TimetableEventType.Practical || event.eventType == TimetableEventType.Seminar || event.eventType == TimetableEventType.Other("WRB-ACTIVE"))

	class EventToImport {
		def this(event: TimetableEvent) {
			this()
			timetableEvent = event
		}

		var timetableEvent: TimetableEvent = _
		var group: SmallGroup = _
	}

}

trait ImportSmallGroupEventsFromExternalSystemRequestState {
	var eventsToImport: JList[EventToImport] = JArrayList()
}

trait ImportSmallGroupEventsFromExternalSystemCommandState {
	def module: Module
	def set: SmallGroupSet
}

class ImportSmallGroupEventsFromExternalSystemCommandInternal(val module: Module, val set: SmallGroupSet)
	extends CommandInternal[Seq[SmallGroupEvent]]
		with ImportSmallGroupEventsFromExternalSystemCommandState
		with ImportSmallGroupEventsFromExternalSystemRequestState {
	self: SmallGroupEventGenerator with UserLookupComponent with SmallGroupServiceComponent =>

	override def applyInternal() = transactional() {
		val events = eventsToImport.asScala
			.map { e => (e.timetableEvent, Option(e.group)) }
			.filter { case (_, group) => group.nonEmpty }
			.map { case (e, group) =>
				val tutorUsercodes = e.staff.map { _.getUserId }

				createEvent(module, set, group.get, e.weekRanges, e.day, e.startTime, e.endTime, e.location, e.name, tutorUsercodes)
			}

		smallGroupService.saveOrUpdate(set)
		events
	}
}

trait LookupEventsFromModuleTimetable extends PopulateOnForm {
	self: ImportSmallGroupEventsFromExternalSystemCommandState
		with ImportSmallGroupEventsFromExternalSystemRequestState
		with ModuleTimetableFetchingServiceComponent =>

	eventsToImport.clear()
	eventsToImport.addAll(
		Await.result(timetableFetchingService.getTimetableForModule(module.code.toUpperCase), 15.seconds)
			.filter(ImportSmallGroupEventsFromExternalSystemCommand.isValidForYear(set.academicYear))
			.sorted
			.map(new EventToImport(_))
			.asJava
	)

	override def populate(): Unit = {
		if (set.groups.asScala.forall { _.events.isEmpty }) {
			set.groups.asScala.sorted.zipWithIndex.foreach { case (group, index) =>
				if (eventsToImport.size() > index) {
					eventsToImport.get(index).group = group
				}
			}
		}
	}
}

trait ImportSmallGroupEventsFromExternalSystemPermissions extends RequiresPermissionsChecking with PermissionsCheckingMethods {
	self: ImportSmallGroupEventsFromExternalSystemCommandState =>

	override def permissionsCheck(p: PermissionsChecking): Unit = {
		mustBeLinked(mandatory(set), mandatory(module))
		p.PermissionCheck(RequiredPermission, set)
	}

}

trait ImportSmallGroupEventsFromExternalSystemDescription extends Describable[Seq[SmallGroupEvent]] {
	self: ImportSmallGroupEventsFromExternalSystemCommandState =>

	override def describe(d: Description) =
		d.smallGroupSet(set)

}