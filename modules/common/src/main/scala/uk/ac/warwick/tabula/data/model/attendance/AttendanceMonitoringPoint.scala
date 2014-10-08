package uk.ac.warwick.tabula.data.model.attendance

import org.joda.time.{LocalDate, DateTime}
import uk.ac.warwick.tabula.data.PostLoadBehaviour
import uk.ac.warwick.tabula.data.model.{GeneratedId, Assignment, Module, MeetingFormat, StudentRelationshipType, HasSettings}
import uk.ac.warwick.spring.Wire
import uk.ac.warwick.tabula.services.{AssignmentService, ModuleAndDepartmentService, RelationshipService}
import uk.ac.warwick.tabula.JavaImports._
import collection.JavaConverters._
import javax.validation.constraints.NotNull
import javax.persistence.{Entity, JoinColumn, FetchType, ManyToOne, Column}
import org.hibernate.annotations.Type

@Entity
class AttendanceMonitoringPoint extends GeneratedId with AttendanceMonitoringPointSettings {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "scheme_id")
	var scheme: AttendanceMonitoringScheme = _

	@NotNull
	var name: String = _

	@Column(name = "start_week")
	private var _startWeek: Int = _

	def startWeek = {
		if (scheme != null && scheme.pointStyle == AttendanceMonitoringPointStyle.Date) {
			throw new IllegalArgumentException
		}
		_startWeek
	}
	def startWeek_= (startWeek: Int) {
		_startWeek = startWeek
	}

	@Column(name = "end_week")
	private var _endWeek: Int = _

	def endWeek = {
		if (scheme != null && scheme.pointStyle == AttendanceMonitoringPointStyle.Date) {
			throw new IllegalArgumentException
		}
		_endWeek
	}
	def endWeek_= (endWeek: Int) {
		_endWeek = endWeek
	}

	@NotNull
	@Column(name = "start_date")
	var startDate: LocalDate = _

	@NotNull
	@Column(name = "end_date")
	var endDate: LocalDate = _

	def isDateValidForPoint(date: LocalDate): Boolean =
		date == startDate || date == endDate || (startDate.isBefore(date) && endDate.isAfter(date))

	def isStartDateInFuture: Boolean =
		DateTime.now.isBefore(startDate.toDateTimeAtStartOfDay)

	@Column(name="point_type")
	@NotNull
	@Type(`type` = "uk.ac.warwick.tabula.data.model.attendance.AttendanceMonitoringPointTypeUserType")
	var pointType: AttendanceMonitoringPointType = _

	@NotNull
	@Column(name = "created_date")
	var createdDate: DateTime = _

	@NotNull
	@Column(name = "updated_date")
	var updatedDate: DateTime = _

	def cloneTo(scheme: Option[AttendanceMonitoringScheme]): AttendanceMonitoringPoint = {
		val newPoint = new AttendanceMonitoringPoint
		scheme.foreach(s => {
			s.points.add(newPoint)
			newPoint.scheme = s
		})
		newPoint.name = this.name
		newPoint.startWeek = _startWeek
		newPoint.endWeek = _endWeek
		newPoint.startDate = startDate
		newPoint.endDate = endDate
		newPoint.pointType = pointType
		pointType match {
			case AttendanceMonitoringPointType.Meeting =>
				newPoint.meetingRelationships = meetingRelationships.toSeq
				newPoint.meetingFormats = meetingFormats.toSeq
				newPoint.meetingQuantity = meetingQuantity
			case AttendanceMonitoringPointType.SmallGroup =>
				newPoint.smallGroupEventQuantity = smallGroupEventQuantity
				newPoint.smallGroupEventModules = smallGroupEventModules
			case AttendanceMonitoringPointType.AssignmentSubmission =>
				newPoint.assignmentSubmissionType = assignmentSubmissionType
				newPoint.assignmentSubmissionTypeAnyQuantity = assignmentSubmissionTypeAnyQuantity
				newPoint.assignmentSubmissionTypeModulesQuantity = assignmentSubmissionTypeModulesQuantity
				newPoint.assignmentSubmissionModules = assignmentSubmissionModules
				newPoint.assignmentSubmissionAssignments = assignmentSubmissionAssignments
				newPoint.assignmentSubmissionIsDisjunction = assignmentSubmissionIsDisjunction
			case _ =>
		}
		newPoint
	}
}

trait AttendanceMonitoringPointSettings extends HasSettings with PostLoadBehaviour {

	import AttendanceMonitoringPoint._

	@transient
	var relationshipService = Wire[RelationshipService]

	@transient
	var moduleAndDepartmentService = Wire[ModuleAndDepartmentService]

	@transient
	var assignmentService = Wire[AssignmentService]

	// Setting for MonitoringPointType.Meeting
	def meetingRelationships = getStringSeqSetting(Settings.MeetingRelationships, Seq())
		.map(relationshipService.getStudentRelationshipTypeById(_).orNull)
	def meetingRelationships_= (relationships: Seq[StudentRelationshipType]):Unit =
		settings += (Settings.MeetingRelationships -> relationships.map(_.id))
	// Ugh. This sucks. But Spring always wants to use the Seq version if they share a method name, and therefore won't bind
	def meetingRelationshipsSpring_= (relationships: JSet[StudentRelationshipType]):Unit = {
		meetingRelationships = relationships.asScala.toSeq
	}

	def meetingFormats = getStringSeqSetting(Settings.MeetingFormats, Seq()).map(MeetingFormat.fromDescription)
	def meetingFormats_= (formats: Seq[MeetingFormat]) =
		settings += (Settings.MeetingFormats -> formats.map(_.description))
	// See above
	def meetingFormatsSpring_= (formats: JSet[MeetingFormat]) =
		meetingFormats = formats.asScala.toSeq

	def meetingQuantity = getIntSetting(Settings.MeetingQuantity, 1)
	def meetingQuantity_= (quantity: Int) = settings += (Settings.MeetingQuantity -> quantity)

	// Setting for MonitoringPointType.SmallGroup

	def smallGroupEventQuantity = getIntSetting(Settings.SmallGroupEventQuantity, 0)
	def smallGroupEventQuantity_= (quantity: Int): Unit = settings += (Settings.SmallGroupEventQuantity -> quantity)
	def smallGroupEventQuantity_= (quantity: JInteger): Unit = {
		smallGroupEventQuantity = quantity match {
			case q: JInteger => q.intValue
			case _ => 0
		}
	}

	def smallGroupEventModules = getStringSeqSetting(Settings.SmallGroupEventModules, Seq())
		.map(moduleAndDepartmentService.getModuleById(_).orNull)
	def smallGroupEventModules_= (modules: Seq[Module]) =
		settings += (Settings.SmallGroupEventModules -> modules.map(_.id))
	// See above
	def smallGroupEventModulesSpring_= (modules: JSet[Module]) =
		smallGroupEventModules = modules.asScala.toSeq

	// Setting for MonitoringPointType.AssignmentSubmission

	def assignmentSubmissionType: String = getStringSetting(Settings.AssignmentSubmissionType) match {
		case Some(stringType) => stringType
		// Where this isn't defined, fall back to the old version
		case None => getBooleanSetting(Settings.AssignmentSubmissionIsSpecificAssignments) match {
			case None => // The new default
				Settings.AssignmentSubmissionTypes.Any
			case Some(true) =>
				Settings.AssignmentSubmissionTypes.Assignments
			case Some(false) =>
				Settings.AssignmentSubmissionTypes.Modules
		}
	}
	def assignmentSubmissionType_= (stringType: String): Unit = stringType match {
		case (Settings.AssignmentSubmissionTypes.Any | Settings.AssignmentSubmissionTypes.Assignments | Settings.AssignmentSubmissionTypes.Modules) =>
			settings += (Settings.AssignmentSubmissionType -> stringType)
		case _ => throw new IllegalArgumentException("No such assignmentSubmissionType")
	}

	def assignmentSubmissionTypeAnyQuantity = getIntSetting(Settings.AssignmentSubmissionTypeAnyQuantity, 0)
	def assignmentSubmissionTypeAnyQuantity_= (quantity: Int): Unit = settings += (Settings.AssignmentSubmissionTypeAnyQuantity -> quantity)
	def assignmentSubmissionTypeAnyQuantity_= (quantity: JInteger): Unit = {
		assignmentSubmissionTypeAnyQuantity = quantity match {
			case q: JInteger => q.intValue
			case _ => 0
		}
	}


	def assignmentSubmissionTypeModulesQuantity = getIntSetting(Settings.AssignmentSubmissionTypeModulesQuantity, 0)
	def assignmentSubmissionTypeModulesQuantity_= (quantity: Int): Unit = settings += (Settings.AssignmentSubmissionTypeModulesQuantity -> quantity)
	def assignmentSubmissionTypeModulesQuantity_= (quantity: JInteger): Unit = {
		assignmentSubmissionTypeModulesQuantity = quantity match {
			case q: JInteger => q.intValue
			case _ => 0
		}
	}

	def assignmentSubmissionModules = getStringSeqSetting(Settings.AssignmentSubmissionModules, Seq())
		.map(moduleAndDepartmentService.getModuleById(_).orNull)
	def assignmentSubmissionModules_= (modules: Seq[Module]) =
		settings += (Settings.AssignmentSubmissionModules -> modules.map(_.id))
	// See above
	def assignmentSubmissionModulesSpring_= (modules: JSet[Module]) =
		assignmentSubmissionModules = modules.asScala.toSeq

	def assignmentSubmissionAssignments = getStringSeqSetting(Settings.AssignmentSubmissionAssignments, Seq())
		.map(assignmentService.getAssignmentById(_).orNull)
	def assignmentSubmissionAssignments_= (assignments: Seq[Assignment]) =
		settings += (Settings.AssignmentSubmissionAssignments -> assignments.map(_.id))
	// See above
	def assignmentSubmissionAssignmentsSpring_= (assignments: JSet[Assignment]) =
		assignmentSubmissionAssignments = assignments.asScala.toSeq

	def assignmentSubmissionIsDisjunction = getBooleanSetting(Settings.AssignmentSubmissionIsDisjunction) getOrElse false
	def assignmentSubmissionIsDisjunction_= (allow: Boolean) = settings += (Settings.AssignmentSubmissionIsDisjunction -> allow)

	override def postLoad() {
		ensureSettings
	}
}

object AttendanceMonitoringPoint {

	object Settings {
		val MeetingRelationships = "meetingRelationships"
		val MeetingFormats = "meetingFormats"
		val MeetingQuantity = "meetingQuantity"

		val SmallGroupEventQuantity = "smallGroupEventQuantity"
		val SmallGroupEventModules = "smallGroupEventModules"

		val AssignmentSubmissionType = "assignmentSubmissionType"
		object AssignmentSubmissionTypes {
			val Any = "any"
			val Modules = "modules"
			val Assignments = "assignments"
		}
		val AssignmentSubmissionIsSpecificAssignments = "assignmentSubmissionIsSpecificAssignments"
		val AssignmentSubmissionTypeAnyQuantity = "assignmentSubmissionAnyQuantity"
		val AssignmentSubmissionTypeModulesQuantity = "assignmentSubmissionQuantity"
		val AssignmentSubmissionModules = "assignmentSubmissionModules"
		val AssignmentSubmissionAssignments = "assignmentSubmissionAssignments"
		val AssignmentSubmissionIsDisjunction = "assignmentSubmissionIsDisjunction"
	}
}
