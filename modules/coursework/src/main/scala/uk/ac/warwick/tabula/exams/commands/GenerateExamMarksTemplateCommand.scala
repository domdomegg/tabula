package uk.ac.warwick.tabula.exams.commands

import org.apache.poi.ss.usermodel.{ComparisonOperator, IndexedColors}
import org.apache.poi.ss.util.{CellRangeAddress, WorkbookUtil}
import org.apache.poi.xssf.usermodel.{XSSFSheet, XSSFWorkbook}
import uk.ac.warwick.tabula.commands._
import uk.ac.warwick.tabula.coursework.commands.feedback.MarksTemplateCommand
import uk.ac.warwick.tabula.data.model.{Exam, Assessment, Assignment, Module}
import uk.ac.warwick.tabula.permissions.Permissions
import uk.ac.warwick.tabula.services.{AutowiringFeedbackServiceComponent, FeedbackServiceComponent}
import uk.ac.warwick.tabula.system.permissions.{PermissionsChecking, PermissionsCheckingMethods, RequiresPermissionsChecking}
import uk.ac.warwick.userlookup.User

object GenerateExamMarksTemplateCommand {
	def apply(module: Module, exam: Exam, members: Seq[(User, Option[Int])]) =
		new GenerateExamMarksTemplateCommandInternal(module, exam, members)
			with AutowiringFeedbackServiceComponent
			with ComposableCommand[XSSFWorkbook]
			with GenerateAllMarksTemplatePermissions
			with GenerateMarksTemplateCommandState
			with Unaudited with ReadOnly
}

class GenerateExamMarksTemplateCommandInternal(val module: Module, val exam: Exam, val members: Seq[(User, Option[Int])]) extends CommandInternal[XSSFWorkbook] {

	self: FeedbackServiceComponent =>

	override def applyInternal() = {

		val workbook = new XSSFWorkbook()
		val sheet = generateNewMarkSheet(exam, workbook)

		// populate the mark sheet with ids
		for ((memberPair, i) <- members.zipWithIndex) {
			val row = sheet.createRow(i + 1)
			row.createCell(0).setCellValue(memberPair._2.map(_.toString).getOrElse(""))
			row.createCell(1).setCellValue(memberPair._1.getWarwickId)
			val marksCell = row.createCell(2)
			val gradesCell = row.createCell(3)
			val feedbacks = feedbackService.getStudentFeedback(exam, memberPair._1.getWarwickId)
			feedbacks.foreach { feedback =>
				feedback.actualMark.foreach(marksCell.setCellValue(_))
				feedback.actualGrade.foreach(gradesCell.setCellValue)
			}
		}

		// add conditional formatting for invalid marks
		addConditionalFormatting(sheet)

		workbook
	}

	private def generateNewMarkSheet(exam: Exam, workbook: XSSFWorkbook) = {
		val sheet = workbook.createSheet("Marks for " + MarksTemplateCommand.safeAssessmentName(exam))

		// add header row
		val header = sheet.createRow(0)
		header.createCell(0).setCellValue("Seat number")
		header.createCell(1).setCellValue("ID")
		header.createCell(2).setCellValue("Mark")
		// TODO could perhaps have it calculated based on the grade boundaries
		header.createCell(3).setCellValue("Grade")

		sheet
	}

	private def addConditionalFormatting(sheet: XSSFSheet) = {
		val sheetCF = sheet.getSheetConditionalFormatting

		val invalidMarkRule = sheetCF.createConditionalFormattingRule(ComparisonOperator.NOT_BETWEEN, "0", "100")
		val fontFmt = invalidMarkRule.createFontFormatting
		fontFmt.setFontStyle(true, false)
		fontFmt.setFontColorIndex(IndexedColors.DARK_RED.index)

		val marksColumn = Array(new CellRangeAddress(1, sheet.getLastRowNum, 2, 2))
		sheetCF.addConditionalFormatting(marksColumn, invalidMarkRule)
	}

}

trait GenerateMarksTemplateCommandState {
	def module: Module
	def exam: Exam
}

trait GenerateAllMarksTemplatePermissions extends RequiresPermissionsChecking with PermissionsCheckingMethods {

	self: GenerateMarksTemplateCommandState =>

	mustBeLinked(exam, module)

	override def permissionsCheck(p: PermissionsChecking) {
		p.PermissionCheck(Permissions.Marks.DownloadTemplate, exam)
	}

}
