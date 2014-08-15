package uk.ac.warwick.tabula.attendance.commands.manage

import org.springframework.validation.Errors
import uk.ac.warwick.tabula.helpers.StringUtils._
import uk.ac.warwick.tabula.services.attendancemonitoring.AttendanceMonitoringServiceComponent
import scala.collection.mutable
import uk.ac.warwick.tabula.data.model.{Assignment, Module, Department, MeetingFormat, StudentRelationshipType}
import uk.ac.warwick.tabula.JavaImports._
import org.joda.time.LocalDate
import uk.ac.warwick.tabula.services.TermServiceComponent
import uk.ac.warwick.tabula.AcademicYear
import uk.ac.warwick.tabula.data.model.groups.DayOfWeek
import uk.ac.warwick.tabula.data.model.attendance.{AttendanceMonitoringPoint, AttendanceMonitoringPointStyle, AttendanceMonitoringScheme}
import collection.JavaConverters._
import uk.ac.warwick.util.termdates.Term

trait AttendanceMonitoringPointValidation {

	self: TermServiceComponent with AttendanceMonitoringServiceComponent =>

	def validateSchemePointStyles(errors: Errors, style: AttendanceMonitoringPointStyle, schemes: Seq[AttendanceMonitoringScheme]) = {
		if (schemes.exists(_.pointStyle != style)) {
			errors.reject("attendanceMonitoringPoint.pointStyle.mixed")
		}
	}

	def validateName(errors: Errors, name: String) {
		if (!name.hasText) {
			errors.rejectValue("name", "NotEmpty")
		} else if (name.length > 4000) {
			errors.rejectValue("name", "attendanceMonitoringPoint.name.toolong")
		}
	}

	def validateWeek(errors: Errors, week: Int, bindPoint: String) {
		week match {
			case y if y < 1  => errors.rejectValue(bindPoint, "attendanceMonitoringPoint.week.min")
			case y if y > 52 => errors.rejectValue(bindPoint, "attendanceMonitoringPoint.week.max")
			case _ =>
		}
	}

	def validateWeeks(errors: Errors, startWeek: Int, endWeek: Int) {
		if (startWeek > endWeek) {
			errors.rejectValue("startWeek", "attendanceMonitoringPoint.weeks")
		}
	}

	def validateDate(errors: Errors, date: LocalDate, academicYear: AcademicYear, bindPoint: String) {
		if (date == null) {
			errors.rejectValue(bindPoint, "NotEmpty")
		} else {
			termService.getAcademicWeekForAcademicYear(date.toDateTimeAtStartOfDay, academicYear) match {
				case Term.WEEK_NUMBER_BEFORE_START => errors.rejectValue(bindPoint, "attendanceMonitoringPoint.date.min")
				case Term.WEEK_NUMBER_AFTER_END => errors.rejectValue(bindPoint, "attendanceMonitoringPoint.date.max")
				case _ =>
			}
		}
	}

	def validateDates(errors: Errors, startDate: LocalDate, endDate: LocalDate) {
		if (startDate.isAfter(endDate)) {
			errors.rejectValue("startDate", "attendanceMonitoringPoint.dates")
		}
	}

	def validateTypeMeeting(
		errors: Errors,
		meetingRelationships: mutable.Set[StudentRelationshipType],
		meetingFormats: mutable.Set[MeetingFormat],
		meetingQuantity: Int,
		department: Department
	) {

		if (meetingRelationships.size == 0) {
			errors.rejectValue("meetingRelationships", "attendanceMonitoringPoint.meetingType.meetingRelationships.empty")
		} else {
			val invalidRelationships = meetingRelationships.filter(r => !department.displayedStudentRelationshipTypes.contains(r))
			if (invalidRelationships.size > 0)
				errors.rejectValue("meetingRelationships", "attendanceMonitoringPoint.meetingType.meetingRelationships.invalid", invalidRelationships.mkString(", "))
		}

		if (meetingFormats.size == 0) {
			errors.rejectValue("meetingFormats", "attendanceMonitoringPoint.meetingType.meetingFormats.empty")
		}

		if (meetingQuantity < 1) {
			errors.rejectValue("meetingQuantity", "attendanceMonitoringPoint.pointType.quantity")
		}
	}

	def validateTypeSmallGroup(
		errors: Errors,
		smallGroupEventModules: JSet[Module],
		isAnySmallGroupEventModules: Boolean,
		smallGroupEventQuantity: JInteger
	) {

		if (smallGroupEventQuantity < 1) {
			errors.rejectValue("smallGroupEventQuantity", "attendanceMonitoringPoint.pointType.quantity")
		}

		if (!isAnySmallGroupEventModules && (smallGroupEventModules == null || smallGroupEventModules.size == 0)) {
			errors.rejectValue("smallGroupEventModules", "attendanceMonitoringPoint.smallGroupType.smallGroupModules.empty")
		}

	}

	def validateTypeAssignmentSubmission(
		errors: Errors,
		isSpecificAssignments: Boolean,
		assignmentSubmissionQuantity: JInteger,
		assignmentSubmissionModules: JSet[Module],
		assignmentSubmissionAssignments: JSet[Assignment]
	) {

		if (isSpecificAssignments) {
			if (assignmentSubmissionAssignments == null || assignmentSubmissionAssignments.isEmpty) {
				errors.rejectValue("assignmentSubmissionAssignments", "attendanceMonitoringPoint.assingmentSubmissionType.assignmentSubmissionAssignments.empty")
			}
		} else {
			if (assignmentSubmissionQuantity < 1) {
				errors.rejectValue("assignmentSubmissionQuantity", "attendanceMonitoringPoint.pointType.quantity")
			}

			if (assignmentSubmissionModules == null || assignmentSubmissionModules.isEmpty) {
				errors.rejectValue("assignmentSubmissionModules", "attendanceMonitoringPoint.assingmentSubmissionType.assignmentSubmissionModules.empty")
			}
		}
	}
	
	def validateCanPointBeEditedByDate(
		errors: Errors,
		startDate: LocalDate,
		studentIds: Seq[String],
		academicYear: AcademicYear,
		bindPoint: String = "startDate"
	) = {
		val pointTerm = termService.getTermFromDateIncludingVacations(startDate.toDateTimeAtStartOfDay)
		if (attendanceMonitoringService.findReports(studentIds, academicYear, pointTerm.getTermTypeAsString).size > 0) {
			if (bindPoint.isEmpty)
				errors.reject("attendanceMonitoringPoint.hasReportedCheckpoints.add")
			else
				errors.rejectValue(bindPoint, "attendanceMonitoringPoint.hasReportedCheckpoints.add")
		}
	}

	def validateCanPointBeEditedByWeek(
		errors: Errors,
		startWeek: Int,
		studentIds: Seq[String],
		academicYear: AcademicYear,
		bindPoint: String = "startWeek"
	) = {
		val weeksForYear = termService.getAcademicWeeksForYear(academicYear.dateInTermOne).toMap
		val startDate = weeksForYear(startWeek).getStart.withDayOfWeek(DayOfWeek.Monday.jodaDayOfWeek).toLocalDate
		validateCanPointBeEditedByDate(errors, startDate, studentIds, academicYear, bindPoint)
	}

	def validateDuplicateForWeek(
		errors: Errors,
		name: String,
		startWeek: Int,
		endWeek: Int,
		schemes: Seq[AttendanceMonitoringScheme],
		global: Boolean = false
	) = {
		val allPoints = schemes.map(_.points.asScala).flatten
		if (allPoints.exists(point => point.name == name && point.startWeek == startWeek && point.endWeek == endWeek)) {
			if (global) {
				errors.reject("attendanceMonitoringPoint.name.weeks.exists.global", Array(name, startWeek.toString, endWeek.toString), null)
			} else {
				errors.rejectValue("name", "attendanceMonitoringPoint.name.weeks.exists")
				errors.rejectValue("startWeek", "attendanceMonitoringPoint.name.weeks.exists")
			}
		}
	}

	def validateDuplicateForDate(
		errors: Errors,
		name: String,
		startDate: LocalDate,
		endDate: LocalDate,
		schemes: Seq[AttendanceMonitoringScheme],
		global: Boolean = false
	) = {
		val allPoints = schemes.map(_.points.asScala).flatten
		if (allPoints.exists(point => point.name == name && point.startDate == startDate && point.endDate == endDate)) {
			if (global) {
				errors.reject("attendanceMonitoringPoint.name.dates.exists.global", Array(name, startDate, endDate), null)
			} else {
				errors.rejectValue("name", "attendanceMonitoringPoint.name.dates.exists")
				errors.rejectValue("startDate", "attendanceMonitoringPoint.name.dates.exists")
			}
		}
	}

	def validateDuplicateForWeekForEdit(
		errors: Errors,
		name: String,
		startWeek: Int,
		endWeek: Int,
		point: AttendanceMonitoringPoint
	): Boolean = {
		val allPoints = point.scheme.points.asScala
		if (allPoints.exists(p => point.id != p.id && p.name == name && p.startWeek == startWeek && p.endWeek == endWeek)) {
			errors.rejectValue("name", "attendanceMonitoringPoint.name.weeks.exists")
			errors.rejectValue("startWeek", "attendanceMonitoringPoint.name.weeks.exists")
			true
		} else {
			false
		}
	}

	def validateDuplicateForDateForEdit(
		errors: Errors,
		name: String,
		startDate: LocalDate,
		endDate: LocalDate,
		point: AttendanceMonitoringPoint
	): Boolean = {
		val allPoints = point.scheme.points.asScala
		if (allPoints.exists(p => point.id != p.id && p.name == name && p.startDate == startDate && p.endDate == endDate)) {
			errors.rejectValue("name", "attendanceMonitoringPoint.name.dates.exists")
			errors.rejectValue("startDate", "attendanceMonitoringPoint.name.dates.exists")
			true
		} else {
			false
		}
	}

	def validateOverlapMeeting(
		errors: Errors,
		startDate: LocalDate,
		endDate: LocalDate,
		meetingRelationships: mutable.Set[StudentRelationshipType],
		meetingFormats: mutable.Set[MeetingFormat],
		schemes: Seq[AttendanceMonitoringScheme]
	) = {
		val allPoints = schemes.map(_.points.asScala).flatten
		if (allPoints.exists(point =>
			datesOverlap(point, startDate, endDate) &&
				point.meetingRelationships.exists(meetingRelationships.contains) &&
				point.meetingFormats.exists(meetingFormats.contains)
		)) {
			errors.reject("attendanceMonitoringPoint.overlaps")
		}
	}

	def validateOverlapMeetingForEdit(
		errors: Errors,
		startDate: LocalDate,
		endDate: LocalDate,
		meetingRelationships: mutable.Set[StudentRelationshipType],
		meetingFormats: mutable.Set[MeetingFormat],
		point: AttendanceMonitoringPoint
	): Boolean = {
		val allPoints = point.scheme.points.asScala
		if (allPoints.exists(p => point.id != p.id &&
			datesOverlap(p, startDate, endDate) &&
			p.meetingRelationships.exists(meetingRelationships.contains) &&
			p.meetingFormats.exists(meetingFormats.contains)
		)) {
			errors.reject("attendanceMonitoringPoint.overlaps")
			true
		} else {
			false
		}
	}

	private def datesOverlap(point: AttendanceMonitoringPoint, startDate: LocalDate, endDate: LocalDate) =
		(point.startDate.isBefore(endDate) || point.startDate == endDate)	&& (point.endDate.isAfter(startDate) || point.endDate == startDate)
}
