package uk.ac.warwick.tabula.commands.reports.profiles

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}

import org.joda.time.format.DateTimeFormat
import uk.ac.warwick.spring.Wire
import uk.ac.warwick.tabula.commands._
import uk.ac.warwick.tabula.data.FileDao
import uk.ac.warwick.tabula.data.model.attendance.{AttendanceMonitoringPoint, AttendanceMonitoringPointType, MonitoringPoint, MonitoringPointType}
import uk.ac.warwick.tabula.data.model.groups.DayOfWeek
import uk.ac.warwick.tabula.data.model.{AttendanceNote, FileAttachment, StudentMember}
import uk.ac.warwick.tabula.pdf.FreemarkerXHTMLPDFGeneratorComponent
import uk.ac.warwick.tabula.permissions.Permissions
import uk.ac.warwick.tabula.services._
import uk.ac.warwick.tabula.services.attendancemonitoring.{AttendanceMonitoringServiceComponent, AutowiringAttendanceMonitoringServiceComponent}
import uk.ac.warwick.tabula.system.permissions.{PermissionsChecking, PermissionsCheckingMethods, RequiresPermissionsChecking}
import uk.ac.warwick.tabula.web.views.AutowiredTextRendererComponent
import uk.ac.warwick.tabula.{AcademicYear, CurrentUser}
import uk.ac.warwick.userlookup.User

import scala.collection.JavaConverters._

object ProfileExportSingleCommand {
	val DateFormat = DateTimeFormat.forPattern("dd/MM/yyyy")
	val TimeFormat = DateTimeFormat.forPattern("dd/MM/yyyy HH:mm")

	def apply(student: StudentMember, academicYear: AcademicYear, user: CurrentUser) =
		new ProfileExportSingleCommandInternal(student, academicYear, user)
			with AutowiredTextRendererComponent
			with FreemarkerXHTMLPDFGeneratorComponent
			with AutowiringAttendanceMonitoringServiceComponent
			with AutowiringMonitoringPointServiceComponent
			with AutowiringTermServiceComponent
			with AutowiringUserLookupComponent
			with AutowiringAssessmentServiceComponent
			with AutowiringRelationshipServiceComponent
			with AutowiringMeetingRecordServiceComponent
			with AutowiringSmallGroupServiceComponent
			with ComposableCommand[Seq[FileAttachment]]
			with ProfileExportSingleDescription
			with ProfileExportSinglePermissions
			with ProfileExportSingleCommandState
}


class ProfileExportSingleCommandInternal(val student: StudentMember, val academicYear: AcademicYear, user: CurrentUser)
	extends CommandInternal[Seq[FileAttachment]] with TaskBenchmarking {

	self: FreemarkerXHTMLPDFGeneratorComponent with AttendanceMonitoringServiceComponent
		with MonitoringPointServiceComponent with AssessmentServiceComponent
		with RelationshipServiceComponent with MeetingRecordServiceComponent
		with SmallGroupServiceComponent
		with TermServiceComponent with UserLookupComponent =>

	var fileDao = Wire.auto[FileDao]

	import uk.ac.warwick.tabula.helpers.DateTimeOrdering._

	case class PointData(
		departmentName: String,
		term: String,
		state: String,
		name: String,
		pointType: String,
		pointTypeInfo: String,
		startDate: String,
		endDate: String,
		recordedBy: User,
		recordedDate: String,
		attendanceNote: Option[AttendanceNote]
	)

	case class AssignmentData(
		module: String,
		name: String,
		submissionDeadline: String,
		submissionDate: String
	)

	case class SmallGroupData(
		eventId: String,
		title: String,
		day: String,
		location: String,
		tutors: String,
		week: String,
		state: String,
		recordedBy: User,
		recordedDate: String,
		attendanceNote: Option[AttendanceNote]
	)

	case class MeetingData(
		relationshipType: String,
		agent: String,
		meetingDate: String,
		title: String,
		format: String,
		description: String
	)

	override def applyInternal() = {
		// Get point data
		val pointData = benchmarkTask("pointData") {
			if (academicYear.startYear < 2014) {
				getOldPointData
			} else {
				getPointData
			}
		}

		// Get coursework
		val assignmentData = benchmarkTask("assignmentData") {
			assessmentService.getAssignmentsWithSubmission(student.universityId)
				.filter(_.academicYear == academicYear)
				.sortBy(_.closeDate)
				.flatMap(assignment => {
					assignment.findSubmission(student.universityId).map(submission => {
						AssignmentData(
							assignment.module.code.toUpperCase,
							assignment.name,
							Option(assignment.submissionDeadline(submission)).map(_.toString(ProfileExportSingleCommand.TimeFormat)).getOrElse(""),
							submission.submittedDate.toString(ProfileExportSingleCommand.TimeFormat)
						)
					})
				})
		}

		// Get small groups
		val smallGroupData = benchmarkTask("smallGroupData") { getSmallGroupData }

		// Get meetings
		val startOfYear = termService.getTermFromAcademicWeekIncludingVacations(1, academicYear).getStartDate
		val endOfYear = termService.getTermFromAcademicWeek(1, academicYear + 1).getStartDate
		val meetingData = benchmarkTask("meetingData") {
			relationshipService.getAllPastAndPresentRelationships(student).flatMap(meetingRecordService.list)
				.filter(m => m.meetingDate.isAfter(startOfYear) && m.meetingDate.isBefore(endOfYear) && m.isApproved)
				.sortBy(_.meetingDate)
				.map(meeting => MeetingData(
					meeting.relationship.relationshipType.agentRole.capitalize,
					meeting.relationship.agentName,
					meeting.meetingDate.toString(ProfileExportSingleCommand.TimeFormat),
					meeting.title,
					meeting.format.description,
					meeting.description
				))
		}


		// Build model
		val summary = pointData
			.groupBy(_.departmentName).mapValues(_
			.groupBy(_.term).mapValues(_
			.groupBy(_.state).mapValues(_
			.size)))

		val groupedPoints = pointData
			.groupBy(_.departmentName).mapValues(_
			.groupBy(_.state).mapValues(_
			.groupBy(_.term)))

		// Render PDF
		val tempOutputStream = new ByteArrayOutputStream()
		pdfGenerator.renderTemplate(
			"/WEB-INF/freemarker/reports/profile-export.ftl",
			Map(
				"student" -> student,
				"academicYear" -> academicYear,
				"user" -> user,
				"summary" -> summary,
				"groupedPoints" -> groupedPoints,
				"assignmentData" -> assignmentData,
				"smallGroupData" -> smallGroupData.groupBy(_.eventId),
				"meetingData" -> meetingData.groupBy(_.relationshipType)
			),
			tempOutputStream
		)

		// Create file
		val pdfFileAttachment = new FileAttachment
		pdfFileAttachment.name = s"${student.universityId}-profile.pdf"
		pdfFileAttachment.uploadedData = new ByteArrayInputStream(tempOutputStream.toByteArray)
		pdfFileAttachment.uploadedDataLength = 0
		fileDao.saveTemporary(pdfFileAttachment)

		// Return results
		Seq(pdfFileAttachment) ++
			pointData.flatMap(_.attendanceNote.flatMap(note => Option(note.attachment))) ++
			smallGroupData.flatMap(_.attendanceNote.flatMap(note => Option(note.attachment)))
	}

	private def getOldPointData: Seq[PointData] = {
		val pointSetsByStudent = benchmarkTask("monitoringPointService.findPointSetsForStudentsByStudent") {
			monitoringPointService.findPointSetsForStudentsByStudent(Seq(student), academicYear)
		}
		val allPoints = pointSetsByStudent.flatMap(_._2.points.asScala).toSeq
		val checkpoints = benchmarkTask("monitoringPointService.getCheckpointsByStudent") {
			monitoringPointService.getCheckpointsByStudent(allPoints).map(_._2) }
		val attendanceNotes = benchmarkTask("monitoringPointService.findAttendanceNotes") {
			monitoringPointService.findAttendanceNotes(Seq(student), allPoints).groupBy(_.student).map{
				case (s, notes) => s -> notes.groupBy(_.point).mapValues(_.head)
			}
		}
		val users = benchmarkTask("userLookup.getUsersByUserIds") {
			userLookup.getUsersByUserIds(checkpoints.map(_.updatedBy).asJava).asScala
		}
		val weeksForYear = termService.getAcademicWeeksForYear(academicYear.dateInTermOne).toMap
		checkpoints.map(checkpoint => {
			PointData(
				checkpoint.point.pointSet.route.adminDepartment.name,
				termService.getTermFromAcademicWeek(checkpoint.point.validFromWeek, academicYear).getTermTypeAsString,
				checkpoint.state.dbValue,
				checkpoint.point.name,
				Option(checkpoint.point.pointType).map(_.description).getOrElse("Standard"),
				serializePointTypeOptions(checkpoint.point),
				weeksForYear(checkpoint.point.validFromWeek).getStart.withDayOfWeek(DayOfWeek.Monday.jodaDayOfWeek).toLocalDate.toString(ProfileExportSingleCommand.DateFormat),
				weeksForYear(checkpoint.point.requiredFromWeek).getStart.withDayOfWeek(DayOfWeek.Monday.jodaDayOfWeek).toLocalDate.toString(ProfileExportSingleCommand.DateFormat),
				users(checkpoint.updatedBy),
				checkpoint.updatedDate.toString(ProfileExportSingleCommand.TimeFormat),
				attendanceNotes.get(student).flatMap(_.get(checkpoint.point))
			)
		})
	}

	private def getPointData: Seq[PointData] = {
		val checkpoints = benchmarkTask("attendanceMonitoringService.getAllAttendance") {
			attendanceMonitoringService.getAllAttendance(student.universityId)
		}
		val attendanceNoteMap = benchmarkTask("attendanceMonitoringService.getAttendanceNoteMap") {
			attendanceMonitoringService.getAttendanceNoteMap(student)
		}
		val users = benchmarkTask("userLookup.getUsersByUserIds") {
			userLookup.getUsersByUserIds(checkpoints.map(_.updatedBy).asJava).asScala
		}
		checkpoints.map(checkpoint => {
			PointData(
				checkpoint.point.scheme.department.name,
				termService.getTermFromDateIncludingVacations(checkpoint.point.startDate.toDateTimeAtStartOfDay).getTermTypeAsString,
				checkpoint.state.dbValue,
				checkpoint.point.name,
				checkpoint.point.pointType.description,
				serializePointTypeOptions(checkpoint.point),
				checkpoint.point.startDate.toString(ProfileExportSingleCommand.DateFormat),
				checkpoint.point.endDate.toString(ProfileExportSingleCommand.DateFormat),
				users(checkpoint.updatedBy),
				checkpoint.updatedDate.toString(ProfileExportSingleCommand.TimeFormat),
				attendanceNoteMap.get(checkpoint.point)
			)
		})
	}

	private def serializePointTypeOptions(point: AttendanceMonitoringPoint): String = {
		point.pointType match {
			case AttendanceMonitoringPointType.Standard =>
				"None"
			case AttendanceMonitoringPointType.Meeting =>
				"%s %s with the student's %s".format(
					point.meetingQuantity,
					if (point.meetingFormats.isEmpty)
						"meeting of any format"
					else
						point.meetingFormats.map(_.getDescription).mkString(" or "),
					point.meetingRelationships.map(_.agentRole).mkString(" or ")
				)
			case AttendanceMonitoringPointType.SmallGroup =>
				"Attend %s event%s for %s".format(
					point.smallGroupEventQuantity,
					if (point.smallGroupEventQuantity == 1) "" else "s",
					if (point.smallGroupEventModules.isEmpty)
						"any module"
					else
						point.smallGroupEventModules.map(_.code.toUpperCase).mkString(" or ")
				)
			case AttendanceMonitoringPointType.AssignmentSubmission =>
				point.assignmentSubmissionType match {
					case AttendanceMonitoringPoint.Settings.AssignmentSubmissionTypes.Any =>
						"Submit to %s assignment%s in any module".format(
							point.assignmentSubmissionTypeAnyQuantity,
							if (point.assignmentSubmissionTypeAnyQuantity != 1) "s" else ""
						)
					case AttendanceMonitoringPoint.Settings.AssignmentSubmissionTypes.Modules =>
						"Submit to %s assignment%s in %s".format(
							point.assignmentSubmissionTypeModulesQuantity,
							if (point.assignmentSubmissionTypeModulesQuantity != 1) "s" else "",
							point.assignmentSubmissionModules.map(_.code.toUpperCase).mkString(" or ")
						)
					case AttendanceMonitoringPoint.Settings.AssignmentSubmissionTypes.Assignments =>
						"Submit to %s %s assignment%s: %s".format(
							if (point.assignmentSubmissionIsDisjunction) "any" else "all",
							point.assignmentSubmissionAssignments.size,
							if (point.assignmentSubmissionAssignments.size != 1) "s" else "",
							point.assignmentSubmissionAssignments.map(a => a.module.code.toUpperCase + " " + a.name).mkString(", ")
						)
				}
		}
	}

	private def serializePointTypeOptions(point: MonitoringPoint): String = {
		point.pointType match {
			case MonitoringPointType.Meeting =>
				"%s %s with the student's %s".format(
					point.meetingQuantity,
					if (point.meetingFormats.isEmpty)
						"meeting of any format"
					else
						point.meetingFormats.map(_.getDescription).mkString(" or "),
					point.meetingRelationships.map(_.agentRole).mkString(" or ")
				)
			case MonitoringPointType.SmallGroup =>
				"Attend %s event%s for %s".format(
					point.smallGroupEventQuantity,
					if (point.smallGroupEventQuantity == 1) "" else "s",
					if (point.smallGroupEventModules.isEmpty)
						"any module"
					else
						point.smallGroupEventModules.map(_.code.toUpperCase).mkString(" or ")
				)
			case MonitoringPointType.AssignmentSubmission =>
				point.assignmentSubmissionIsSpecificAssignments match {
					case false =>
						"Submit to %s assignment%s in %s".format(
							point.assignmentSubmissionQuantity,
							if (point.assignmentSubmissionQuantity != 1) "s" else "",
							point.assignmentSubmissionModules.map(_.code.toUpperCase).mkString(" or ")
						)
					case true =>
						"Submit to %s %s assignment%s: %s".format(
							if (point.assignmentSubmissionIsDisjunction) "any" else "all",
							point.assignmentSubmissionAssignments.size,
							if (point.assignmentSubmissionAssignments.size != 1) "s" else "",
							point.assignmentSubmissionAssignments.map(a => a.module.code.toUpperCase + " " + a.name).mkString(", ")
						)
				}
			case _ =>
				"None"
		}
	}

	private def getSmallGroupData: Seq[SmallGroupData] = {
		val allAttendance = benchmarkTask("smallGroupService.findAttendanceForStudentInModulesInWeeks") {
			smallGroupService.findAttendanceForStudentInModulesInWeeks(student, 1, 52, Seq())
		}
		val users = benchmarkTask("userLookup.getUsersByUserIds") {
			userLookup.getUsersByUserIds(allAttendance.map(_.updatedBy).asJava).asScala
		}
		val attendanceNotes = smallGroupService.findAttendanceNotes(Seq(student.universityId), allAttendance.map(_.occurrence))

		allAttendance.map(attendance => SmallGroupData(
			attendance.occurrence.event.id,
			Seq(
				Option(attendance.occurrence.event.group.groupSet.module.code.toUpperCase),
				Option(attendance.occurrence.event.group.groupSet.name),
				Option(attendance.occurrence.event.group.name),
				Option(attendance.occurrence.event.title)
			).flatten.mkString(", "),
			attendance.occurrence.event.day.name,
			Option(attendance.occurrence.event.location).map(_.name).getOrElse(""),
			attendance.occurrence.event.tutors.users.map(_.getFullName).mkString(", "),
			attendance.occurrence.week.toString,
			attendance.state.description,
			users(attendance.updatedBy),
			attendance.updatedDate.toString(ProfileExportSingleCommand.TimeFormat),
			attendanceNotes.find(_.occurrence == attendance.occurrence)
		))
	}

}

trait ProfileExportSinglePermissions extends RequiresPermissionsChecking with PermissionsCheckingMethods {

	self: ProfileExportSingleCommandState =>

	override def permissionsCheck(p: PermissionsChecking) {
		p.PermissionCheck(Permissions.Department.Reports, student)
	}

}

trait ProfileExportSingleDescription extends Describable[Seq[FileAttachment]] {

	self: ProfileExportSingleCommandState =>

	override lazy val eventName = "ProfileExportSingle"

	override def describe(d: Description) {
		d.studentIds(Seq(student.universityId))
	}
}

trait ProfileExportSingleCommandState {
	def student: StudentMember
	def academicYear: AcademicYear
}
