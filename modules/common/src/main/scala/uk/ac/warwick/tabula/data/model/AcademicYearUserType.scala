package uk.ac.warwick.tabula.data.model

import uk.ac.warwick.tabula.AcademicYear
import uk.ac.warwick.tabula.JavaImports._

/**
 * Stores an AcademicYear as an integer (which is the 4-digit start year)
 */
final class AcademicYearUserType extends AbstractIntegerUserType[AcademicYear] {
	def convertToObject(input: JInteger) = new AcademicYear(input)
	def convertToValue(year: AcademicYear) = year.startYear

	val nullValue = 0: JInteger
	val nullObject = null
}