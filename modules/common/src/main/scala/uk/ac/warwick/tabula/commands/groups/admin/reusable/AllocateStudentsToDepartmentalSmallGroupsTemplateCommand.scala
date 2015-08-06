package uk.ac.warwick.tabula.commands.groups.admin.reusable

import org.apache.poi.ss.util.CellRangeAddressList
import org.apache.poi.xssf.usermodel._
import uk.ac.warwick.tabula.commands._
import uk.ac.warwick.tabula.data.model.Department
import uk.ac.warwick.tabula.data.model.groups.DepartmentSmallGroupSet
import uk.ac.warwick.tabula.permissions.Permissions
import uk.ac.warwick.tabula.system.permissions.{PermissionsChecking, PermissionsCheckingMethods, RequiresPermissionsChecking}
import uk.ac.warwick.tabula.web.views.ExcelView
import scala.collection.JavaConverters._

object AllocateStudentsToDepartmentalSmallGroupsTemplateCommand {
	def apply(department: Department, set: DepartmentSmallGroupSet) =
		new AllocateStudentsToDepartmentalSmallGroupsTemplateCommandInternal(department, set)
			with ComposableCommand[ExcelView]
			with AllocateStudentsToDepartmentalSmallGroupsTemplatePermissions
			with ReadOnly with Unaudited
}

class AllocateStudentsToDepartmentalSmallGroupsTemplateCommandInternal(val department: Department, val set: DepartmentSmallGroupSet)
	extends CommandInternal[ExcelView] with AllocateStudentsToDepartmentalSmallGroupsTemplateCommandState {

	val groupLookupSheetName = "GroupLookup"
	val allocateSheetName = "AllocateStudents"
	val spreadsheetRows = 1000
	val sheetPassword = "roygbiv"

	def applyInternal() = {
		val workbook = generateWorkbook()
		new ExcelView("Allocation for " + set.name +  ".xlsx", workbook)
	}

	def generateWorkbook() = {
		val groups = set.groups.asScala.toList

		val workbook = new XSSFWorkbook()
		val sheet: XSSFSheet = generateAllocationSheet(workbook)
		generateGroupLookupSheet(workbook)
		generateGroupDropdowns(sheet, groups)

		val groupLookupRange = groupLookupSheetName + "!$A2:$B" + (groups.length + 1)
		val userIterator = set.allStudents.iterator

		while (sheet.getLastRowNum < spreadsheetRows) {
			val row = sheet.createRow(sheet.getLastRowNum + 1)

			// put the student details into the cells
			// otherwise create blank unprotected cells for the user to enter
			if (userIterator.hasNext) {
				val user = userIterator.next
				val thisUser = user
				row.createCell(0).setCellValue(thisUser.getWarwickId)
				row.createCell(1).setCellValue(thisUser.getFullName)

				val groupNameCell = createUnprotectedCell(workbook, row, 2) // unprotect cell for the dropdown group name

				// If this user is already in a group, prefill
				groups.find { _.students.includesUser(user) }.foreach { group =>
					groupNameCell.setCellValue(group.name)
				}
			} else {
				createUnprotectedCell(workbook, row, 0) // cell for student_id
				createUnprotectedCell(workbook, row, 1) // cell for name (for the user's info only)
				createUnprotectedCell(workbook, row, 2) // cell for the group name
			}

			row.createCell(3).setCellFormula(
				"IF(ISTEXT($C" + (row.getRowNum + 1) + "), VLOOKUP($C" + (row.getRowNum + 1) + ", " + groupLookupRange + ", 2, FALSE), \" \")"
			)
		}

		formatWorkbook(workbook)
		workbook
	}

	def createUnprotectedCell(workbook: XSSFWorkbook, row: XSSFRow, col: Int, value: String = "") = {
		val lockedCellStyle = workbook.createCellStyle()
		lockedCellStyle.setLocked(false)
		val cell = row.createCell(col)
		cell.setCellValue(value)
		cell.setCellStyle(lockedCellStyle)
		cell
	}

	// attaches the data validation to the sheet
	def generateGroupDropdowns(sheet: XSSFSheet, groups: Seq[_]) {
		val dropdownRange = new CellRangeAddressList(1, spreadsheetRows, 2, 2)
		val validation = getDataValidation(groups, sheet, dropdownRange)

		sheet.addValidationData(validation)
	}

	// Excel data validation - will only accept the values fed to this method, also puts a dropdown on each cell
	def getDataValidation(groups: Seq[_], sheet: XSSFSheet, addressList: CellRangeAddressList) = {
		val dvHelper = new XSSFDataValidationHelper(sheet)
		val dvConstraint = dvHelper.createFormulaListConstraint(groupLookupSheetName + "!$A$2:$A$" + (groups.length + 1)).asInstanceOf[XSSFDataValidationConstraint]
		val validation = dvHelper.createValidation(dvConstraint, addressList).asInstanceOf[XSSFDataValidation]

		validation.setShowErrorBox(true)
		validation
	}

	def generateGroupLookupSheet(workbook: XSSFWorkbook) = {
		val groupSheet: XSSFSheet = workbook.createSheet(groupLookupSheetName)

		for (group <- set.groups.asScala) {
			val row = groupSheet.createRow(groupSheet.getLastRowNum() + 1)
			row.createCell(0).setCellValue(group.name)
			row.createCell(1).setCellValue(group.id)
		}

		groupSheet.protectSheet(sheetPassword)
		groupSheet
	}

	def generateAllocationSheet(workbook: XSSFWorkbook): XSSFSheet =  {
		val sheet = workbook.createSheet(allocateSheetName)

		// add header row
		val header = sheet.createRow(0)
		header.createCell(0).setCellValue("student_id")
		header.createCell(1).setCellValue("Student name")
		header.createCell(2).setCellValue("Group name")
		header.createCell(3).setCellValue("group_id")

		// using apache-poi, we can't protect certain cells - rather we have to protect
		// the entire sheet and then unprotect the ones we want to remain editable
		sheet.protectSheet(sheetPassword)
		sheet
	}

	def formatWorkbook(workbook: XSSFWorkbook) = {
		val style = workbook.createCellStyle
		val format = workbook.createDataFormat

		// using an @ sets text format (from BuiltinFormats.class)
		style.setDataFormat(format.getFormat("@"))

		val sheet = workbook.getSheet(allocateSheetName)

		// set style on all columns
		0 to 3 foreach  {
			col => sheet.setDefaultColumnStyle(col, style)
			sheet.autoSizeColumn(col)
		}

		// set ID column to be wider
		sheet.setColumnWidth(3, 7000)

	}
}

trait AllocateStudentsToDepartmentalSmallGroupsTemplateCommandState {
	def department: Department
	def set: DepartmentSmallGroupSet
}

trait AllocateStudentsToDepartmentalSmallGroupsTemplatePermissions extends RequiresPermissionsChecking with PermissionsCheckingMethods {
	self: AllocateStudentsToDepartmentalSmallGroupsTemplateCommandState =>

	override def permissionsCheck(p: PermissionsChecking) {
		mustBeLinked(set, department)
		p.PermissionCheck(Permissions.SmallGroups.Allocate, mandatory(set))
	}
}