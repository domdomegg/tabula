package uk.ac.warwick.tabula.data.model.groups

import scala.collection.JavaConverters._
import javax.persistence._
import javax.persistence.CascadeType._
import javax.validation.constraints.NotNull
import org.joda.time.DateTime
import org.hibernate.annotations.{Type, Filter, FilterDef, AccessType, BatchSize}
import uk.ac.warwick.spring.Wire
import uk.ac.warwick.tabula.AcademicYear
import uk.ac.warwick.tabula.ToString
import uk.ac.warwick.tabula.data.model._
import uk.ac.warwick.tabula.services._
import uk.ac.warwick.tabula.JavaImports._
import uk.ac.warwick.tabula.permissions.PermissionsTarget
import uk.ac.warwick.tabula.services.permissions.PermissionsService
import uk.ac.warwick.tabula.data.PostLoadBehaviour
import uk.ac.warwick.userlookup.User

object SmallGroupSet {
	final val NotDeletedFilter = "notDeleted"
	object Settings {
		val StudentsCanSeeTutorNames = "StudentsCanSeeTutorNames"
		val StudentsCanSeeOtherMembers = "StudentsCanSeeOtherMembers"
		val DefaultMaxGroupSizeEnabled = "DefaultMaxGroupSizeEnabled"
		val DefaultMaxGroupSize = "DefaultMaxGroupSize"
	}
	
	// For sorting a collection by set name. Either pass to the sort function,
	// or expose as an implicit val.
	val NameOrdering = Ordering.by { set: SmallGroupSet => (set.name, set.id) }

	// Companion object is one of the places searched for an implicit Ordering, so
	// this will be the default when ordering a list of small group sets.
	implicit val defaultOrdering = NameOrdering
}

/**
 * Represents a set of small groups, within an instance of a module.
 */
@FilterDef(name = SmallGroupSet.NotDeletedFilter, defaultCondition = "deleted = 0")
@Filter(name = SmallGroupSet.NotDeletedFilter)
@Entity
@AccessType("field")
class SmallGroupSet
		extends GeneratedId
		with CanBeDeleted
		with ToString
		with PermissionsTarget
		with HasSettings
		with Serializable
		with PostLoadBehaviour
		with ToEntityReference {
	type Entity = SmallGroupSet

	import SmallGroupSet.Settings
	import SmallGroup._

	@transient var permissionsService = Wire[PermissionsService]
	@transient var membershipService = Wire[AssignmentMembershipService]

	def this(_module: Module) {
		this()
		this.module = _module
	}

	@Basic
	@Type(`type` = "uk.ac.warwick.tabula.data.model.AcademicYearUserType")
	@Column(nullable = false)
	var academicYear: AcademicYear = AcademicYear.guessByDate(new DateTime())

	@NotNull
	var name: String = _

	var archived: JBoolean = false

  @Column(name="released_to_students")
	var releasedToStudents: JBoolean = false
  @Column(name="released_to_tutors")
  var releasedToTutors: JBoolean = false
  
  def visibleToStudents = releasedToStudents || allocationMethod == SmallGroupAllocationMethod.StudentSignUp

  def fullyReleased= releasedToStudents && releasedToTutors

	@Column(name="group_format")
	@Type(`type` = "uk.ac.warwick.tabula.data.model.groups.SmallGroupFormatUserType")
	@NotNull
	var format: SmallGroupFormat = _
	
	@Column(name="allocation_method")
	@Type(`type` = "uk.ac.warwick.tabula.data.model.groups.SmallGroupAllocationMethodUserType")
	var allocationMethod: SmallGroupAllocationMethod = _

	@Column(name="self_group_switching")
	var allowSelfGroupSwitching: Boolean = true

	// TODO consider changing this to be a string, and setting it to the name of the SmallGroupSetSelfSignUpState
	// to allow for more states than just "open" and "closed"
	@Column(name="open_for_signups")
	var openForSignups: Boolean = false

  def openState:SmallGroupSetSelfSignUpState = if (openForSignups) SmallGroupSetSelfSignUpState.Open else SmallGroupSetSelfSignUpState.Closed 
  
 def openState_= (value:SmallGroupSetSelfSignUpState):Unit =  value match {
	  case SmallGroupSetSelfSignUpState.Open => openForSignups = true
	  case SmallGroupSetSelfSignUpState.Closed => openForSignups = false
  }
	
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "module_id")
	var module: Module = _
	
	@OneToMany(fetch = FetchType.LAZY, cascade = Array(CascadeType.ALL), orphanRemoval=true)
	@JoinColumn(name = "set_id")
	@BatchSize(size=200)
	var groups: JList[SmallGroup] = JArrayList()

	// only students manually added or excluded. use allStudents to get all students in the group set
	@OneToOne(cascade = Array(ALL), fetch = FetchType.LAZY)
	@JoinColumn(name = "membersgroup_id")
	var _membersGroup = UserGroup.ofUniversityIds
	def members: UnspecifiedTypeUserGroup= _membersGroup

	// Cannot link directly to upstream assessment groups data model in sits is silly ...
	@OneToMany(mappedBy = "smallGroupSet", fetch = FetchType.LAZY, cascade = Array(CascadeType.ALL), orphanRemoval = true)
	@BatchSize(size=200)
	var assessmentGroups: JList[AssessmentGroup] = JArrayList()
	
	@Column(name="collect_attendance")
	var collectAttendance: Boolean = true
	
	def showAttendanceReports = !archived && !deleted && collectAttendance

	// converts the assessmentGroups to upstream assessment groups
	def upstreamAssessmentGroups: Seq[UpstreamAssessmentGroup] = assessmentGroups.asScala.flatMap { _.toUpstreamAssessmentGroup(academicYear) }
	
	def isStudentMember(user: User): Boolean = {
		groups.asScala.exists(_.students.includesUser(user)) ||
		membershipService.isStudentMember(user, upstreamAssessmentGroups, Option(members))
	}

	def allStudents = membershipService.determineMembershipUsers(upstreamAssessmentGroups, Some(members))
	def allStudentsCount = membershipService.countMembershipWithUniversityIdGroup(upstreamAssessmentGroups, Some(members))
	
	def unallocatedStudents = {
		val allocatedStudents = groups.asScala flatMap { _.students.users }

		allStudents diff allocatedStudents
	}
	
	def unallocatedStudentsCount = {
		val allocatedStudentsCount = groups.asScala.foldLeft(0) { (acc, grp) => acc + grp.students.size }
		
		allStudentsCount - allocatedStudentsCount
	}
	
	def hasAllocated = groups.asScala exists { !_.students.isEmpty }
	
	def permissionsParents = Option(module).toStream

	def studentsCanSeeTutorName = getBooleanSetting(Settings.StudentsCanSeeTutorNames).getOrElse(false)
	def studentsCanSeeTutorName_=(canSee:Boolean) = settings += (Settings.StudentsCanSeeTutorNames -> canSee)

	def studentsCanSeeOtherMembers = getBooleanSetting(Settings.StudentsCanSeeOtherMembers).getOrElse(false)
	def studentsCanSeeOtherMembers_=(canSee:Boolean) = settings += (Settings.StudentsCanSeeOtherMembers -> canSee)

	def defaultMaxGroupSizeEnabled = getBooleanSetting(Settings.DefaultMaxGroupSizeEnabled).getOrElse(false)
	def defaultMaxGroupSizeEnabled_=(isEnabled:Boolean) = settings += (Settings.DefaultMaxGroupSizeEnabled -> isEnabled)

	def defaultMaxGroupSize = getIntSetting(Settings.DefaultMaxGroupSize).getOrElse(DefaultGroupSize)
	def defaultMaxGroupSize_=(defaultSize:Int) = settings += (Settings.DefaultMaxGroupSize -> defaultSize)


	def toStringProps = Seq(
		"id" -> id,
		"name" -> name,
		"module" -> module)

  def duplicateTo( module:Module, assessmentGroups:JList[AssessmentGroup] = JArrayList()):SmallGroupSet = {
    val newSet = new SmallGroupSet()
    newSet.id = id
    newSet.academicYear = academicYear
    newSet.allocationMethod = allocationMethod
    newSet.allowSelfGroupSwitching = allowSelfGroupSwitching
    newSet.archived = archived
    newSet.assessmentGroups = assessmentGroups
    newSet.collectAttendance = collectAttendance
    newSet.format = format
    newSet.groups = groups.asScala.map(_.duplicateTo(newSet)).asJava
    newSet._membersGroup = _membersGroup.duplicate()
    newSet.membershipService= membershipService
    newSet.module = module
    newSet.name = name
    newSet.permissionsService = permissionsService
    newSet.releasedToStudents = releasedToStudents
    newSet.releasedToTutors = releasedToTutors
		newSet.settings = Map() ++ settings
    newSet
  }

	def postLoad {
		ensureSettings
	}

	override def toEntityReference = new SmallGroupSetEntityReference().put(this)
}

