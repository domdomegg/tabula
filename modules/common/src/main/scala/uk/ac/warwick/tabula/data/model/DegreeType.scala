package uk.ac.warwick.tabula.data.model


sealed abstract class DegreeType(val dbValue: String, val description: String, val sortOrder: Int)

object DegreeType {
	case object Undergraduate extends DegreeType("UG", "Undergraduate", 1)
	case object Postgraduate extends DegreeType("PG", "Postgraduate", 2)
	case object PGCE extends DegreeType("PGCE", "PGCE", 3)
	case object InService extends DegreeType("IS", "In-Service", 4)

	def fromCode(code: String) = code match {
	  	case Undergraduate.dbValue => Undergraduate
	  	case Postgraduate.dbValue => Postgraduate
	  	case InService.dbValue => InService
	  	case PGCE.dbValue => PGCE
	  	case null => null
	  	case _ => throw new IllegalArgumentException()
	}

	def getFromSchemeCode(schemeCode: String): DegreeType = schemeCode match {
		case "UW UG" => Undergraduate
		case "UW PG" => Postgraduate
		case _ => null
	}

	val SortOrdering: Ordering[DegreeType] = Ordering.by[DegreeType, Int] ( _.sortOrder )

	// Companion object is one of the places searched for an implicit Ordering, so
	// this will be the default when ordering a list of degree types.
	implicit val defaultOrdering = SortOrdering
}

class DegreeTypeUserType extends AbstractStringUserType[DegreeType] {
	override def convertToObject(string: String) = DegreeType.fromCode(string)
	override def convertToValue(degreeType: DegreeType) = degreeType.dbValue
}

