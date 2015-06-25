package uk.ac.warwick.tabula.exams.web.controllers.admin

import java.io.StringWriter

import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.{PathVariable, RequestMapping}
import uk.ac.warwick.tabula.AcademicYear
import uk.ac.warwick.tabula.data.model.{Exam, Module}
import uk.ac.warwick.tabula.exams.commands.ViewExamCommand
import uk.ac.warwick.tabula.exams.web.controllers.ExamsController
import uk.ac.warwick.tabula.web.views.{CSVView, ExcelView}
import uk.ac.warwick.util.csv.GoodCsvDocument

@Controller
@RequestMapping(Array("/exams/admin/module/{module}/{academicYear}/exams/{exam}"))
class ExportExamsController extends ExamsController with ExamExports  {

	@RequestMapping(Array("/export.csv"))
	def csv (
		@PathVariable module: Module,
		@PathVariable exam: Exam,
		@PathVariable academicYear: AcademicYear
	) = {

		val command = ViewExamCommand(module, academicYear, exam)
		val results = command.apply()
		val writer = new StringWriter
		val csvBuilder = new CSVBuilder(results.students, results, exam, module, academicYear)
		val doc = new GoodCsvDocument(csvBuilder, null)

		doc.setHeaderLine(true)
		csvBuilder.headers foreach (header => doc.addHeaderField(header))

		results.students foreach (item => doc.addLine(item))
		doc.write(writer)

		new CSVView(module.code + "-" + exam.name + ".csv", writer.toString)
	}

	@RequestMapping(Array("/export.xml"))
	def xml(
		@PathVariable module: Module,
		@PathVariable exam: Exam,
		@PathVariable academicYear: AcademicYear
	) = {

		val command = ViewExamCommand(module, academicYear, exam)
		val results = command.apply()
		new XMLBuilder(results.students, results, exam, module, academicYear).toXML
	}

	@RequestMapping(Array("/export.xlsx"))
	def toXLSX(
		@PathVariable module: Module,
		@PathVariable exam: Exam,
		@PathVariable academicYear: AcademicYear
	) = {

		val command = ViewExamCommand(module, academicYear, exam)
		val results = command.apply()

		val workbook = new ExcelBuilder(results.students, results, module).toXLSX
		new ExcelView(module.code + "-" + exam.name + ".xlsx", workbook)
	}
}