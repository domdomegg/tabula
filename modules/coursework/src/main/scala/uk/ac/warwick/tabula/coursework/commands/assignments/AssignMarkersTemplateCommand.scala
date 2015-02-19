package uk.ac.warwick.tabula.coursework.commands.assignments

import org.apache.poi.ss.util.{CellRangeAddressList, WorkbookUtil}
import org.apache.poi.xssf.usermodel._
import uk.ac.warwick.tabula.ItemNotFoundException
import uk.ac.warwick.tabula.commands.{CommandInternal, Unaudited, ReadOnly, ComposableCommand}
import uk.ac.warwick.tabula.data.model.Assignment
import uk.ac.warwick.tabula.permissions.Permissions

import uk.ac.warwick.tabula.services.{AutowiringUserLookupComponent, AutowiringAssessmentMembershipServiceComponent}
import uk.ac.warwick.tabula.system.permissions.{PermissionsChecking, PermissionsCheckingMethods, RequiresPermissionsChecking}
import uk.ac.warwick.tabula.web.views.ExcelView
import uk.ac.warwick.userlookup.User


object AssignMarkersTemplateCommand {
	def apply(assignment: Assignment) =
		new AssignMarkersTemplateCommandInternal(assignment)
			with ComposableCommand[ExcelView]
			with AssignMarkersTemplateCommandState
			with AssignMarkersTemplateCommandPermissions
			with ReadOnly
			with Unaudited
}

class AssignMarkersTemplateCommandInternal(val assignment:Assignment) extends CommandInternal[ExcelView]
	with AutowiringAssessmentMembershipServiceComponent
	with AutowiringUserLookupComponent {

	val students = assessmentMembershipService.determineMembershipUsers(assignment)

	def applyInternal() = {
		val workbook = generateWorkbook
		new ExcelView("Allocation for " + assignment.name + ".xlsx", workbook)
	}

	case class AllocationInfo (
		markers: Seq[User],
		roleName: String,
		sheet: XSSFSheet,
		getStudentsMarker: User => Option[User]
	) {
		def lookupSheetName =  s"${roleName.replaceAll(" ", "").toLowerCase}lookup"
	}

	private def generateWorkbook = {
		val workbook = new XSSFWorkbook()
		val allocations = addAllocationSheets(workbook)
		generateMarkerLookupSheets(workbook, allocations)
		populateAllocationSheets(allocations)
		workbook
	}

	private def addAllocationSheets(workbook: XSSFWorkbook) = {
		val workflow = Option(assignment.markingWorkflow).getOrElse(throw new ItemNotFoundException(s"No workflow exists for ${assignment.name}"))
		val style = workbook.createCellStyle
		val format = workbook.createDataFormat
		// using an @ sets text format (from BuiltinFormats.class)
		style.setDataFormat(format.getFormat("@"))

		val firstMarkerAllocation = {
			val roleName = WorkbookUtil.createSafeSheetName(workflow.firstMarkerRoleName)
			AllocationInfo(
				workflow.firstMarkers.users,
				roleName,
				workbook.createSheet(roleName),
				(user: User) => workflow.getStudentsFirstMarker(assignment, user.getWarwickId).map(userLookup.getUserByUserId)
			)
		}
		val secondMarkerAllocation = workflow.secondMarkerRoleName.map( name => {
			val roleName = WorkbookUtil.createSafeSheetName(name)
			AllocationInfo(
				workflow.secondMarkers.users,
				roleName,
				workbook.createSheet(roleName),
				(user: User) => workflow.getStudentsSecondMarker(assignment, user.getWarwickId).map(userLookup.getUserByUserId)
			)
		})

		val allocations = firstMarkerAllocation :: secondMarkerAllocation.toList

		val sheets = allocations.map(_.sheet)
		for (sheet <- sheets) {
			val header = sheet.createRow(0)
			header.createCell(0).setCellValue("student_id")
			header.createCell(1).setCellValue("student_name")
			header.createCell(2).setCellValue(s"${sheet.getSheetName.replace(" ", "_")}_name")
			header.createCell(3).setCellValue("agent_id")

			// set style on all columns
			0 to 3 foreach  { col =>
				sheet.setDefaultColumnStyle(col, style)
				sheet.autoSizeColumn(col)
			}
			// set ID column to be wider
			sheet.setColumnWidth(3, 7000)
		}
		allocations
	}

	private def populateAllocationSheets(allocations: Seq[AllocationInfo]){
		for(allocation <- allocations; student <- students) {
			val markerLookupRange = allocation.lookupSheetName + "!$A2:$B" + (allocation.markers.length + 1)
			val row = allocation.sheet.createRow(allocation.sheet.getLastRowNum + 1)
			row.createCell(0).setCellValue(student.getWarwickId)
			row.createCell(1).setCellValue(student.getFullName)
			val marker = allocation.getStudentsMarker(student)
			row.createCell(2).setCellValue(marker.map(_.getFullName).getOrElse(""))
			row.createCell(3).setCellFormula(
				"IF(ISTEXT($C" + (row.getRowNum + 1) + "), VLOOKUP($C" + (row.getRowNum + 1) + ", " + markerLookupRange + ", 2, FALSE), \" \")"
			)
		}
	}

	private def generateMarkerLookupSheets(workbook: XSSFWorkbook, allocations: Seq[AllocationInfo]) {
		for (allocation <- allocations) {
			val sheet = workbook.createSheet(allocation.lookupSheetName)
			for (marker <- allocation.markers) {
				val row = sheet.createRow(sheet.getLastRowNum + 1)
				row.createCell(0).setCellValue(marker.getFullName)
				row.createCell(1).setCellValue(marker.getWarwickId)
			}
			val dropdownRange = new CellRangeAddressList(1, students.length, 2, 2)
			val dvHelper = new XSSFDataValidationHelper(allocation.sheet)
			val dvConstraint = dvHelper.createFormulaListConstraint(
				allocation.lookupSheetName + "!$A$2:$A$" + (allocation.markers.length + 1)
			).asInstanceOf[XSSFDataValidationConstraint]
			val validation = dvHelper.createValidation(dvConstraint, dropdownRange).asInstanceOf[XSSFDataValidation]
			validation.setShowErrorBox(true)
			allocation.sheet.addValidationData(validation)
		}
	}
}

trait AssignMarkersTemplateCommandState {
	val assignment: Assignment
}

trait AssignMarkersTemplateCommandPermissions extends RequiresPermissionsChecking with PermissionsCheckingMethods {
	self : AssignMarkersTemplateCommandState =>

	override def permissionsCheck(p: PermissionsChecking) {
		p.PermissionCheck(Permissions.Assignment.Update, assignment)
	}
}
