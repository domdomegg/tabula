package uk.ac.warwick.tabula.data.model

import org.hibernate.annotations.AccessType
import javax.persistence._
import scala.collection.JavaConversions._
import uk.ac.warwick.userlookup.User
import org.springframework.core.convert.converter.Converter
import uk.ac.warwick.spring.Wire
import uk.ac.warwick.tabula.services.UserLookupService
import uk.ac.warwick.tabula.permissions.PermissionsTarget

/** A MarkingWorkflow defines how an assignment will be marked, including who
  * will be the markers and what rules should be used to decide how submissions
  * are distributed.
  *
  * A MarkingWorkflow is created against a Department and can then be reused by
  * many Assignments within that Department.
  */
@Entity
@Table(name="MarkScheme")
@Inheritance(strategy=InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name="MarkingMethod", discriminatorType = DiscriminatorType.STRING, length=255)
@AccessType("field")
abstract class MarkingWorkflow extends GeneratedId with PermissionsTarget {

	type Usercode = String
	type UniversityId = String

	@transient
	var userLookup = Wire[UserLookupService]("userLookup")

	/** A descriptive name for the users' reference. */
	@Basic(optional = false)
	var name: String = null

	@ManyToOne(optional = false, fetch = FetchType.LAZY)
	@JoinColumn(name = "department_id")
	var department: Department = null

	def permissionsParents = Option(department).toStream

	def onlineMarkingUrl(assignment:Assignment, marker: User) : String

	/** The group of first markers. */
	@OneToOne(cascade = Array(CascadeType.ALL), fetch = FetchType.LAZY)
	@JoinColumn(name = "firstmarkers_id")
	var firstMarkers = UserGroup.ofUsercodes

	/** The second group of markers. May be unused if the marking workflow
	  * only has one marking stage.
	  */
	@OneToOne(cascade = Array(CascadeType.ALL), fetch = FetchType.LAZY)
	@JoinColumn(name = "secondmarkers_id")
	var secondMarkers = UserGroup.ofUsercodes

	def thirdMarkers: UserGroup

	def markingMethod: MarkingMethod

	/** If true, the submitter chooses their first marker from a dropdown */
	def studentsChooseMarker = false

	def firstMarkerRoleName: String = "Marker"
	def firstMarkerVerb: String = "mark"

	// True if this marking workflow uses a second marker
	def hasSecondMarker: Boolean
	def secondMarkerRoleName: Option[String]
	def secondMarkerVerb: Option[String]

	def hasThirdMarker: Boolean
	def thirdMarkerRoleName: Option[String]
	def thirdMarkerVerb: Option[String]

	def studentHasMarker(assignment:Assignment, universityId: String): Boolean =
		getStudentsFirstMarker(assignment, universityId).isDefined || getStudentsSecondMarker(assignment, universityId).isDefined

	def getStudentsFirstMarker(assignment:Assignment, universityId: UniversityId): Option[Usercode]

	def getStudentsSecondMarker(assignment:Assignment, universityId: UniversityId): Option[Usercode]

	def getSubmissions(assignment: Assignment, user: User): Seq[Submission]

	override def toString = "MarkingWorkflow(" + id + ")"

}

trait AssignmentMarkerMap {

	this : MarkingWorkflow =>

	// gets the usercode of the students current marker from the given markers UserGroup
	private def getMarkerFromAssignmentMap(universityId: UniversityId, markerMap: Map[String, UserGroup]): Option[String] = {
		val student = userLookup.getUserByWarwickUniId(universityId)
		markerMap.find{case(markerUserId, group) => group.includesUser(student)}.map{ case (markerUserId, _) => markerUserId }
	}

	def getStudentsFirstMarker(assignment: Assignment, universityId: UniversityId): Option[String] =
		getMarkerFromAssignmentMap(universityId, assignment.firstMarkerMap)

	def getStudentsSecondMarker(assignment: Assignment, universityId: UniversityId): Option[String] =
		getMarkerFromAssignmentMap(universityId, assignment.secondMarkerMap)

	def getSubmissions(assignment: Assignment, marker: User) = {
		val allSubmissions = getSubmissionsFromMap(assignment, marker)

		val isFirstMarker = assignment.isFirstMarker(marker)
		val isSecondMarker = assignment.markingWorkflow.hasSecondMarker && assignment.isSecondMarker(marker)
		val isThirdMarker = assignment.markingWorkflow.hasThirdMarker && assignment.isThirdMarker(marker)

		if(isFirstMarker)
			allSubmissions.filter(_.isReleasedForMarking)
		else if(isSecondMarker)
			allSubmissions.filter(_.isReleasedToSecondMarker)
		else if(isThirdMarker)
			allSubmissions.filter(_.isReleasedToThirdMarker)
		else Seq()
	}

	// returns all submissions made by students assigned to this marker
	private def getSubmissionsFromMap(assignment: Assignment, marker: User): Seq[Submission] = {
		val studentIds =
			assignment.firstMarkerMap.get(marker.getUserId).map{_.knownType.allIncludedIds}.getOrElse(Seq()) ++
				assignment.secondMarkerMap.get(marker.getUserId).map{_.knownType.allIncludedIds}.getOrElse(Seq())

		assignment.submissions.filter(s => studentIds.exists(_ == s.userId))
	}


}

trait NoThirdMarker {
		def hasThirdMarker = false
		def thirdMarkerRoleName = None
		def thirdMarkerVerb = None
		def thirdMarkers: UserGroup = UserGroup.ofUsercodes
}

trait NoSecondMarker extends NoThirdMarker {
		def hasSecondMarker = false
		def secondMarkerRoleName = None
		def secondMarkerVerb = None
}


/**
 * Available marking methods and code to persist them
 */
sealed abstract class MarkingMethod(val name: String){
	override def toString = name
}

object MarkingMethod {
	case object StudentsChooseMarker extends MarkingMethod("StudentsChooseMarker")
	case object SeenSecondMarkingLegacy extends MarkingMethod("SeenSecondMarkingLegacy")
	case object SeenSecondMarking extends MarkingMethod("SeenSecondMarking")
	case object ModeratedMarking extends MarkingMethod("ModeratedMarking")
	case object FirstMarkerOnly extends MarkingMethod("FirstMarkerOnly")

	val values: Set[MarkingMethod] = Set(
		StudentsChooseMarker,
		SeenSecondMarkingLegacy,
		SeenSecondMarking,
		ModeratedMarking,
		FirstMarkerOnly
	)

	def fromCode(code: String): MarkingMethod =
		if (code == null) null
		else values.find{_.name == code} match {
			case Some(method) => method
			case None => throw new IllegalArgumentException()
		}
}

class MarkingMethodUserType extends AbstractStringUserType[MarkingMethod]{
	override def convertToObject(string: String) = MarkingMethod.fromCode(string)
	override def convertToValue(state: MarkingMethod) = state.name
}

class StringToMarkingMethod extends Converter[String, MarkingMethod]{
	def convert(string:String):MarkingMethod = MarkingMethod.fromCode(string)
}