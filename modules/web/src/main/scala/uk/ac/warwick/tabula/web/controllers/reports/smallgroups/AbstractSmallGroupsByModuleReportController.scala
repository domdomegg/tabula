package uk.ac.warwick.tabula.web.controllers.reports.smallgroups

import java.io.StringWriter

import org.springframework.web.bind.annotation.{ModelAttribute, PathVariable, RequestParam}
import uk.ac.warwick.tabula.JavaImports._
import uk.ac.warwick.tabula.commands.Appliable
import uk.ac.warwick.tabula.commands.reports.smallgroups._
import uk.ac.warwick.tabula.data.model.Department
import uk.ac.warwick.tabula.helpers.LazyMaps
import uk.ac.warwick.tabula.web.Mav
import uk.ac.warwick.tabula.web.controllers.reports.ReportsController
import uk.ac.warwick.tabula.web.views.{CSVView, ExcelView, JSONView}
import uk.ac.warwick.tabula.{AcademicYear, JsonHelper}
import uk.ac.warwick.util.csv.GoodCsvDocument

import scala.collection.JavaConverters._


abstract class AbstractSmallGroupsByModuleReportController extends ReportsController {

	def filteredAttendanceCommand(department: Department, academicYear: AcademicYear): Appliable[AllSmallGroupsReportCommandResult]

	val filePrefix: String

	def page(cmd: Appliable[AllSmallGroupsReportCommandResult], department: Department, academicYear: AcademicYear): Mav

	type SmallGroupsByModuleReportProcessor = Appliable[SmallGroupsByModuleReportProcessorResult] with SmallGroupsByModuleReportProcessorState

	@ModelAttribute("command")
	def command(@PathVariable("department") department: Department, @PathVariable("academicYear") academicYear: AcademicYear) =
		SmallGroupsByModuleReportCommand(department, academicYear)

	@ModelAttribute("processor")
	def processor(@PathVariable department: Department, @PathVariable academicYear: AcademicYear) =
		SmallGroupsByModuleReportProcessor(mandatory(department), mandatory(academicYear))

	@RequestMapping(method = Array(POST))
	def apply(
		@ModelAttribute("command") cmd: Appliable[SmallGroupsByModuleReportCommandResult] with SetsFilteredAttendance,
		@ModelAttribute("filteredAttendanceCommand") filteredAttendanceCmd: Appliable[AllSmallGroupsReportCommandResult],
		@PathVariable department: Department,
		@PathVariable academicYear: AcademicYear
	) = {
		cmd.setFilteredAttendance(filteredAttendanceCmd.apply())
		val result = cmd.apply()
		val allStudents: Seq[Map[String, String]] = result.students.map(studentUser =>
			Map(
				"firstName" -> studentUser.getFirstName,
				"lastName" -> studentUser.getLastName,
				"userId" -> studentUser.getUserId,
				"universityId" -> studentUser.getWarwickId
			)
		)
		val allModules: Seq[Map[String, String]] = result.modules.map(module =>
			Map(
				"id" -> module.id,
				"code" -> module.code,
				"name" -> module.name
			)
		)
		Mav(new JSONView(Map(
			"counts" -> result.counts.map{case(student, moduleMap) =>
				student.getWarwickId -> moduleMap.map{case(module, count) =>
					module.id -> count.toString
				}
			}.toMap,
			"students" -> allStudents,
			"modules" -> allModules
		)))
	}

	@RequestMapping(method = Array(POST), value = Array("/download.csv"))
	def csv(
		@ModelAttribute("processor") processor: SmallGroupsByModuleReportProcessor,
		@PathVariable department: Department,
		@PathVariable academicYear: AcademicYear,
		@RequestParam data: String
	) = {
		val processorResult = getProcessorResult(processor, data)

		val writer = new StringWriter
		val csvBuilder = new SmallGroupsByModuleReportExporter(processorResult, department)
		val doc = new GoodCsvDocument(csvBuilder, null)

		doc.setHeaderLine(true)
		csvBuilder.headers foreach (header => doc.addHeaderField(header))
		processorResult.students.foreach(item => doc.addLine(item))
		doc.write(writer)

		new CSVView(s"$filePrefix-${department.code}.csv", writer.toString)
	}

	@RequestMapping(method = Array(POST), value = Array("/download.xlsx"))
	def xlsx(
		@ModelAttribute("processor") processor: SmallGroupsByModuleReportProcessor,
		@PathVariable department: Department,
		@PathVariable academicYear: AcademicYear,
		@RequestParam data: String
	) = {
		val processorResult = getProcessorResult(processor, data)

		val workbook = new SmallGroupsByModuleReportExporter(processorResult, department).toXLSX

		new ExcelView(s"$filePrefix-${department.code}.xlsx", workbook)
	}

	@RequestMapping(method = Array(POST), value = Array("/download.xml"))
	def xml(
		@ModelAttribute("processor") processor: SmallGroupsByModuleReportProcessor,
		@PathVariable department: Department,
		@PathVariable academicYear: AcademicYear,
		@RequestParam data: String
	) = {
		val processorResult = getProcessorResult(processor, data)

		new SmallGroupsByModuleReportExporter(processorResult, department).toXML
	}

	private def getProcessorResult(processor: SmallGroupsByModuleReportProcessor, data: String): SmallGroupsByModuleReportProcessorResult = {
		val request = JsonHelper.fromJson[SmallGroupsByModuleReportRequest](data)
		request.copyTo(processor)
		processor.apply()
	}

}

class SmallGroupsByModuleReportRequest extends Serializable {

	var counts: JMap[String, JMap[String, String]] =
		LazyMaps.create{_: String => JMap[String, String]() }.asJava

	var students: JList[JMap[String, String]] = JArrayList()

	var modules: JList[JMap[String, String]] = JArrayList()

	def copyTo(state: SmallGroupsByModuleReportProcessorState) {
		state.counts = counts

		state.students = students

		state.modules = modules
	}
}