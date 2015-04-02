package uk.ac.warwick.tabula.coursework.web.controllers.admin

import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation._
import uk.ac.warwick.spring.Wire
import uk.ac.warwick.tabula.coursework.commands.assignments.SubmissionAndFeedbackCommand
import uk.ac.warwick.tabula.coursework.commands.assignments.SubmissionAndFeedbackCommand.SubmissionAndFeedbackResults
import uk.ac.warwick.tabula.coursework.web.controllers.CourseworkController
import uk.ac.warwick.tabula.data.model._
import uk.ac.warwick.tabula.services._
import uk.ac.warwick.tabula.services.AuditEventIndexService
import java.io.StringWriter
import uk.ac.warwick.util.csv.GoodCsvDocument
import uk.ac.warwick.tabula.web.views.CSVView
import uk.ac.warwick.tabula.web.views.ExcelView
import org.springframework.web.bind.WebDataBinder
import uk.ac.warwick.util.web.bind.AbstractPropertyEditor
import uk.ac.warwick.tabula.coursework.helpers.{CourseworkFilter, CourseworkFilters}
import uk.ac.warwick.tabula.coursework.web.Routes
import uk.ac.warwick.tabula.Features
import javax.validation.Valid
import org.springframework.validation.Errors

@Controller
@RequestMapping(Array("/admin/module/{module}/assignments/{assignment}"))
class SubmissionAndFeedbackController extends CourseworkController {

	var auditIndexService = Wire[AuditEventIndexService]
	var assignmentService = Wire[AssessmentService]
	var userLookup = Wire[UserLookupService]
	var features = Wire[Features]

	validatesSelf[SubmissionAndFeedbackCommand]

	@ModelAttribute("assignment")
	def assignment(@PathVariable("assignment") assignment: Assignment) = assignment

	@ModelAttribute("submissionAndFeedbackCommand")
	def command(@PathVariable("module") module: Module, @PathVariable("assignment") assignment: Assignment) =
		SubmissionAndFeedbackCommand(module, assignment)

	@ModelAttribute("allFilters")
	def allFilters(@PathVariable("assignment") assignment: Assignment) =
		CourseworkFilters.AllFilters.filter(_.applies(assignment))

	@RequestMapping(Array("/list"))
	def list(@Valid command: SubmissionAndFeedbackCommand, errors: Errors) = {
		val (assignment, module) = (command.assignment, command.module)

		module.adminDepartment.assignmentInfoView match {
			case Assignment.Settings.InfoViewType.Summary =>
				Redirect(Routes.admin.assignment.submissionsandfeedback.summary(assignment))
			case Assignment.Settings.InfoViewType.Table =>
				Redirect(Routes.admin.assignment.submissionsandfeedback.table(assignment))
			case _ => // default
				if (features.assignmentProgressTableByDefault)
					Redirect(Routes.admin.assignment.submissionsandfeedback.summary(assignment))
				else
					Redirect(Routes.admin.assignment.submissionsandfeedback.table(assignment))
		}
	}

	@RequestMapping(Array("/summary"))
	def summary(@Valid command: SubmissionAndFeedbackCommand, errors: Errors) = {
		val (assignment, module) = (command.assignment, command.module)

		if (!features.assignmentProgressTable) Redirect(Routes.admin.assignment.submissionsandfeedback.table(assignment))
		else {
			if (errors.hasErrors) {
				Mav("admin/assignments/submissionsandfeedback/progress")
					.crumbs(Breadcrumbs.Department(module.adminDepartment), Breadcrumbs.Module(module), Breadcrumbs.Current(s"Assignment progress for ${assignment.name}"))
			} else {
				val results = command.apply()

				Mav("admin/assignments/submissionsandfeedback/progress",
					resultMap(results)
				).crumbs(Breadcrumbs.Department(module.adminDepartment), Breadcrumbs.Module(module), Breadcrumbs.Current(s"Assignment progress for ${assignment.name}"))
			}
		}
	}

	@RequestMapping(Array("/table"))
	def table(@Valid command: SubmissionAndFeedbackCommand, errors: Errors) = {
		val (assignment, module) = (command.assignment, command.module)

		if (errors.hasErrors) {
			Mav("admin/assignments/submissionsandfeedback/list")
				.crumbs(Breadcrumbs.Department(module.adminDepartment), Breadcrumbs.Module(module), Breadcrumbs.Current(s"Assignment table for ${assignment.name}"))
		} else {
			val results = command.apply()

			Mav("admin/assignments/submissionsandfeedback/list",
				resultMap(results)
			).crumbs(Breadcrumbs.Department(module.adminDepartment), Breadcrumbs.Module(module), Breadcrumbs.Current(s"Assignment table for ${assignment.name}"))
		}
	}

	def resultMap(results: SubmissionAndFeedbackResults): Map[String, Any] = {
		Map("students" -> results.students,
				"whoDownloaded" -> results.whoDownloaded,
				"stillToDownload" -> results.stillToDownload,
				"hasPublishedFeedback" -> results.hasPublishedFeedback,
				"hasOriginalityReport" -> results.hasOriginalityReport,
				"mustReleaseForMarking" -> results.mustReleaseForMarking)
	}

	@RequestMapping(Array("/export.csv"))
	def csv(@Valid command: SubmissionAndFeedbackCommand) = {
		val (assignment, module) = (command.assignment, command.module)
		val results = command.apply()

		val items = results.students

		val writer = new StringWriter
		val csvBuilder = new CSVBuilder(items, assignment, module)
		val doc = new GoodCsvDocument(csvBuilder, null)

		doc.setHeaderLine(true)
		csvBuilder.headers foreach (header => doc.addHeaderField(header))
		items foreach (item => doc.addLine(item))
		doc.write(writer)

		new CSVView(module.code + "-" + assignment.id + ".csv", writer.toString)
	}

	@RequestMapping(Array("/export.xml"))
	def xml(@Valid command: SubmissionAndFeedbackCommand) = {
		val (assignment, module) = (command.assignment, command.module)
		val results = command.apply()

		val items = results.students

		new XMLBuilder(items, assignment, module).toXML
	}

	@RequestMapping(Array("/export.xlsx"))
	def xlsx(@Valid command: SubmissionAndFeedbackCommand) = {
		val (assignment, module) = (command.assignment, command.module)
		val results = command.apply()

		val items = results.students

		val workbook = new ExcelBuilder(items, assignment, module).toXLSX

		new ExcelView(assignment.name + ".xlsx", workbook)
	}

	override def binding[A](binder: WebDataBinder, cmd: A) {
		binder.registerCustomEditor(classOf[CourseworkFilter], new AbstractPropertyEditor[CourseworkFilter] {
			override def fromString(name: String) = CourseworkFilters.of(name)
			override def toString(filter: CourseworkFilter) = filter.getName
		})
	}

}