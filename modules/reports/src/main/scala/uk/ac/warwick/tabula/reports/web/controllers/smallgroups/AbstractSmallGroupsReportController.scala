package uk.ac.warwick.tabula.reports.web.controllers.smallgroups

import java.io.StringWriter

import org.springframework.web.bind.annotation.{ModelAttribute, PathVariable}
import uk.ac.warwick.tabula.AcademicYear
import uk.ac.warwick.tabula.commands.Appliable
import uk.ac.warwick.tabula.data.model.Department
import uk.ac.warwick.tabula.reports.commands.smallgroups._
import uk.ac.warwick.tabula.reports.web.ReportsBreadcrumbs
import uk.ac.warwick.tabula.reports.web.controllers.ReportsController
import uk.ac.warwick.tabula.web.views.{CSVView, ExcelView, JSONView}
import uk.ac.warwick.util.csv.GoodCsvDocument


abstract class AbstractSmallGroupsReportController extends ReportsController {

	def command(department: Department, academicYear: AcademicYear): Appliable[AllSmallGroupsReportCommandResult]

	val pageRenderPath: String
	val filePrefix: String

	@ModelAttribute("processor")
	def processor(@PathVariable department: Department, @PathVariable academicYear: AcademicYear) =
		SmallGroupsReportProcessor(mandatory(department), mandatory(academicYear))

	@RequestMapping(method = Array(GET))
	def page(@PathVariable department: Department, @PathVariable academicYear: AcademicYear) = {
		Mav(s"smallgroups/$pageRenderPath").crumbs(
			ReportsBreadcrumbs.Home.Department(department),
			ReportsBreadcrumbs.Home.DepartmentForYear(department, academicYear),
			ReportsBreadcrumbs.SmallGroups.Home(department, academicYear)
		)
	}

	@RequestMapping(method = Array(POST))
	def apply(
		@ModelAttribute("command") cmd: Appliable[AllSmallGroupsReportCommandResult],
		@PathVariable department: Department,
		@PathVariable academicYear: AcademicYear
	) = {
		val result = cmd.apply()
		val allStudents: Seq[Map[String, String]] = result.students.map(studentUser =>
			Map(
				"universityId" -> studentUser.getWarwickId,
				"firstName" -> studentUser.getFirstName,
				"lastName" -> studentUser.getLastName,
				"userId" -> studentUser.getUserId
			)
		)
		val allEvents: Seq[Map[String, String]] = result.eventWeeks.map(sgew =>
			Map(
				"id" -> sgew.id,
				"moduleCode" -> sgew.event.group.groupSet.module.code.toUpperCase,
				"setName" -> sgew.event.group.groupSet.nameWithoutModulePrefix,
				"format" -> sgew.event.group.groupSet.format.description,
				"groupName" -> sgew.event.group.name,
				"week" -> sgew.week.toString,
				"day" -> sgew.event.day.getAsInt.toString,
				"dayString" -> sgew.event.day.shortName,
				"location" -> Option(sgew.event.location).map(_.toString).orNull,
				"tutors" -> sgew.event.tutors.users.map(u => s"${u.getFullName} (${u.getUserId})").mkString(", "),
				"eventId" -> sgew.event.id,
				"late" -> sgew.late.toString
			)
		)
		Mav(new JSONView(Map(
			"attendance" -> result.attendance.map{case(student, eventMap) =>
				student.getWarwickId -> eventMap.map{case(sgew, state) =>
					sgew.id -> Option(state).map(_.dbValue).orNull
				}
			}.toMap,
			"students" -> allStudents,
			"events" -> allEvents
		)))
	}

	@RequestMapping(method = Array(POST), value = Array("/download.csv"))
	def csv(
		@ModelAttribute("processor") processor: Appliable[SmallGroupsReportProcessorResult],
		@PathVariable department: Department,
		@PathVariable academicYear: AcademicYear
	) = {
		val processorResult = processor.apply()

		val writer = new StringWriter
		val csvBuilder = new SmallGroupsReportExporter(processorResult, department)
		val doc = new GoodCsvDocument(csvBuilder, null)

		doc.setHeaderLine(true)
		csvBuilder.headers foreach (header => doc.addHeaderField(header))
		processorResult.students.foreach(item => doc.addLine(item))
		doc.write(writer)

		new CSVView(s"$filePrefix-${department.code}.csv", writer.toString)
	}

	@RequestMapping(method = Array(POST), value = Array("/download.xlsx"))
	def xlsx(
		@ModelAttribute("processor") processor: Appliable[SmallGroupsReportProcessorResult],
		@PathVariable department: Department,
		@PathVariable academicYear: AcademicYear
	) = {
		val processorResult = processor.apply()

		val workbook = new SmallGroupsReportExporter(processorResult, department).toXLSX

		new ExcelView(s"$filePrefix-${department.code}.xlsx", workbook)
	}

	@RequestMapping(method = Array(POST), value = Array("/download.xml"))
	def xml(
		@ModelAttribute("processor") processor: Appliable[SmallGroupsReportProcessorResult],
		@PathVariable department: Department,
		@PathVariable academicYear: AcademicYear
	) = {
		val processorResult = processor.apply()

		new SmallGroupsReportExporter(processorResult, department).toXML
	}

}