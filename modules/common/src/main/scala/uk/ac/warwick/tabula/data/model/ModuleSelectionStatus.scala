package uk.ac.warwick.tabula.data.model

import org.hibernate.`type`.StandardBasicTypes
import java.sql.Types

sealed abstract class ModuleSelectionStatus(val dbValue: String, val description: String)

object ModuleSelectionStatus {
	case object Core extends ModuleSelectionStatus("C", "Core")
	case object Option extends ModuleSelectionStatus("O", "Option")
	case object OptionalCore extends ModuleSelectionStatus("CO", "Opt. Core") // core that can be taken in different years

	def fromCode(code: String) = code match {
	  	case Core.dbValue => Core
	  	case Option.dbValue => Option
	  	case OptionalCore.dbValue => OptionalCore
	  	case null => null
	  	case _ => throw new IllegalArgumentException()
	}
}

class ModuleSelectionStatusUserType extends AbstractBasicUserType[ModuleSelectionStatus, String] {

	val basicType = StandardBasicTypes.STRING
	override def sqlTypes = Array(Types.VARCHAR)

	val nullValue = null
	val nullObject = null

	override def convertToObject(string: String) = ModuleSelectionStatus.fromCode(string)

	override def convertToValue(selectionStatus: ModuleSelectionStatus) = selectionStatus.dbValue

}
