package uk.ac.warwick.tabula.data.model

import uk.ac.warwick.tabula.AcademicYear
import uk.ac.warwick.tabula.helpers.Logging

import scala.annotation.tailrec
import scala.collection.JavaConverters._
import scala.xml.NodeSeq
import javax.persistence._
import org.hibernate.annotations.{Type, BatchSize, AccessType, ForeignKey}
import uk.ac.warwick.spring.Wire
import uk.ac.warwick.tabula.JavaImports._
import uk.ac.warwick.tabula.data.PostLoadBehaviour
import uk.ac.warwick.tabula.data.model.groups.{SmallGroupAllocationMethod, WeekRange}
import uk.ac.warwick.tabula.data.model.permissions.CustomRoleDefinition
import uk.ac.warwick.tabula.data.model.permissions.DepartmentGrantedRole
import uk.ac.warwick.tabula.permissions.PermissionsTarget
import uk.ac.warwick.tabula.roles.DepartmentalAdministratorRoleDefinition
import uk.ac.warwick.tabula.roles.ExtensionManagerRoleDefinition
import uk.ac.warwick.tabula.services.permissions.PermissionsService
import uk.ac.warwick.tabula.services.RelationshipService
import uk.ac.warwick.tabula.data.convert.ConvertibleConverter
import uk.ac.warwick.tabula.roles.RoleDefinition
import uk.ac.warwick.tabula.roles.SelectorBuiltInRoleDefinition
import uk.ac.warwick.tabula.helpers.StringUtils._

@Entity @AccessType("field")
class Department extends GeneratedId
	with PostLoadBehaviour with HasSettings with HasNotificationSettings with PermissionsTarget with Serializable with ToEntityReference with Logging{

	import Department._

	type Entity = Department

	@Column(unique = true)
	var code: String = null

	@Column(name = "name")
	var fullName: String = null

	def name = shortName.maybeText.getOrElse(fullName)

	var shortName: String = null

	@OneToMany(mappedBy="parent", fetch = FetchType.LAZY)
	@BatchSize(size=200)
	var children:JSet[Department] = JHashSet()

	@ManyToOne(fetch = FetchType.LAZY, optional=true)
	var parent: Department = null

	// No orphanRemoval as it makes it difficult to move modules between Departments.
	@OneToMany(mappedBy="adminDepartment", fetch = FetchType.LAZY, cascade = Array(CascadeType.ALL), orphanRemoval = false)
	@BatchSize(size=200)
	var modules: JList[Module] = JArrayList()

	@OneToMany(mappedBy="adminDepartment", fetch = FetchType.LAZY, cascade = Array(CascadeType.ALL), orphanRemoval = false)
	@BatchSize(size=200)
	var routes:JList[Route] = JArrayList()

	@OneToMany(mappedBy = "department", fetch = FetchType.LAZY, cascade = Array(CascadeType.ALL), orphanRemoval = true)
	@BatchSize(size=200)
	var moduleTeachingInfo: JSet[ModuleTeachingInformation] = JHashSet()

	@OneToMany(mappedBy = "department", fetch = FetchType.LAZY, cascade = Array(CascadeType.ALL), orphanRemoval = true)
	@BatchSize(size=200)
	var routeTeachingInfo: JSet[RouteTeachingInformation] = JHashSet()

	@OneToMany(mappedBy = "department", fetch = FetchType.LAZY, cascade = Array(CascadeType.ALL), orphanRemoval = true)
	@BatchSize(size=200)
	var feedbackTemplates:JList[FeedbackTemplate] = JArrayList()

	@OneToMany(mappedBy = "department", fetch = FetchType.LAZY, cascade = Array(CascadeType.ALL), orphanRemoval = true)
	@BatchSize(size=200)
	var markingWorkflows:JList[MarkingWorkflow] = JArrayList()

	// TAB-2388 Disable orphanRemoval as Module Managers were unintentionally being removed in certain circumstances
	@OneToMany(mappedBy="department", fetch = FetchType.LAZY, cascade = Array(CascadeType.ALL), orphanRemoval = false)
	@BatchSize(size=200)
	var customRoleDefinitions:JList[CustomRoleDefinition] = JArrayList()

	def collectFeedbackRatings = getBooleanSetting(Settings.CollectFeedbackRatings) getOrElse false
	def collectFeedbackRatings_= (collect: Boolean) = settings += (Settings.CollectFeedbackRatings -> collect)

	// settings for extension requests
	def allowExtensionRequests = getBooleanSetting(Settings.AllowExtensionRequests) getOrElse false
	def allowExtensionRequests_= (allow: Boolean) = settings += (Settings.AllowExtensionRequests -> allow)

	def extensionGuidelineSummary = getStringSetting(Settings.ExtensionGuidelineSummary).orNull
	def extensionGuidelineSummary_= (summary: String) = settings += (Settings.ExtensionGuidelineSummary -> summary)

	def extensionGuidelineLink = getStringSetting(Settings.ExtensionGuidelineLink).orNull
	def extensionGuidelineLink_= (link: String) = settings += (Settings.ExtensionGuidelineLink -> link)

	def showStudentName = getBooleanSetting(Settings.ShowStudentName) getOrElse false
	def showStudentName_= (showName: Boolean) = settings += (Settings.ShowStudentName -> showName)

	def plagiarismDetectionEnabled = getBooleanSetting(Settings.PlagiarismDetection, default = true)
	def plagiarismDetectionEnabled_= (enabled: Boolean) = settings += (Settings.PlagiarismDetection -> enabled)

	def turnitinExcludeBibliography = getBooleanSetting(Settings.TurnitinExcludeBibliography, default = true)
	def turnitinExcludeBibliography_= (exclude: Boolean) = settings += (Settings.TurnitinExcludeBibliography -> exclude)

	def turnitinExcludeQuotations = getBooleanSetting(Settings.TurnitinExcludeQuotations, default = true)
	def turnitinExcludeQuotations_= (exclude: Boolean) = settings += (Settings.TurnitinExcludeQuotations -> exclude)

	def turnitinSmallMatchWordLimit = getIntSetting(Settings.TurnitinSmallMatchWordLimit, 0)
	def turnitinSmallMatchWordLimit_= (limit: Int) = settings += (Settings.TurnitinSmallMatchWordLimit -> limit)

	def turnitinSmallMatchPercentageLimit = getIntSetting(Settings.TurnitinSmallMatchPercentageLimit, 0)
	def turnitinSmallMatchPercentageLimit_= (limit: Int) = settings += (Settings.TurnitinSmallMatchPercentageLimit -> limit)

	def assignmentInfoView = getStringSetting(Settings.AssignmentInfoView) getOrElse Assignment.Settings.InfoViewType.Default
	def assignmentInfoView_= (setting: String) = settings += (Settings.AssignmentInfoView -> setting)

	def autoGroupDeregistration = getBooleanSetting(Settings.AutoGroupDeregistration, default = true)
	def autoGroupDeregistration_=(dereg: Boolean) { settings += (Settings.AutoGroupDeregistration -> dereg) }

	def studentsCanScheduleMeetings = getBooleanSetting(Settings.StudentsCanScheduleMeetings, default = true)
	def studentsCanScheduleMeetings_=(canDo: Boolean) { settings += (Settings.StudentsCanScheduleMeetings -> canDo) }

	def canUploadMarksToSitsForYear(year: AcademicYear, module: Module): Boolean = {
		if (module.degreeType != DegreeType.Undergraduate && module.degreeType != DegreeType.Postgraduate) {
			logger.warn(s"Can't upload marks for module $module since degreeType ${module.degreeType} can't be identified as UG or PG")
			return false
		}
		canUploadMarksToSitsForYear(year, module.degreeType)
	}

	def canUploadMarksToSitsForYear(year: AcademicYear, degreeType: DegreeType): Boolean = {
		val markUploadMap: Option[Map[String, String]] = degreeType match {
			case DegreeType.Undergraduate => getStringMapSetting(Settings.CanUploadMarksToSitsForYearUg)
			case DegreeType.Postgraduate => getStringMapSetting(Settings.CanUploadMarksToSitsForYearPg)
			case _ => {
				logger.warn(s"Can't upload marks for degree type $degreeType since it can't be identified as UG or PG")
				return false
			}
		}
		// marks are uploadable until a department is explicitly closed for the year by the Exams Office
		markUploadMap match {
			case None => true // there isn't even a settings map at all for this so hasn't been closed yet for the year
			case Some(markMap: Map[String, String]) =>
				markMap.get(year.toString) match {
					case None => true // no setting for this year/ugpg combo for the department - so hasn't been closed yet for the year
					case Some(booleanStringValue: String) => booleanStringValue.toBoolean
				}
		}
	}

	def setUploadMarksToSitsForYear(year: AcademicYear, degreeType: DegreeType, canUpload: Boolean): Unit = {
		val (markUploadMap, mapKey) = degreeType match {
			case DegreeType.Undergraduate => (getStringMapSetting(Settings.CanUploadMarksToSitsForYearUg), Settings.CanUploadMarksToSitsForYearUg)
			case DegreeType.Postgraduate => (getStringMapSetting(Settings.CanUploadMarksToSitsForYearPg), Settings.CanUploadMarksToSitsForYearPg)
			case _ => throw new IllegalStateException("setUploadMarksToSitsForYear called with invalid degreeType")
		}

		val markMap = markUploadMap.getOrElse(new collection.mutable.HashMap[String, String]())

		settings += (mapKey -> (markMap + (year.toString -> canUpload.toString)))
	}

	def getStudentRelationshipSource(relationshipType: StudentRelationshipType) =
		getStringMapSetting(Settings.StudentRelationshipSource)
			.flatMap {
			_.get(relationshipType.id)
		}.fold(relationshipType.defaultSource)(StudentRelationshipSource.fromCode)

	def setStudentRelationshipSource (relationshipType: StudentRelationshipType, source: StudentRelationshipSource) = {
		val map = getStringMapSetting(Settings.StudentRelationshipSource, Map())
		val newMap = map + (relationshipType.id -> source.dbValue)

		settings += (Settings.StudentRelationshipSource -> newMap)
	}

	def studentRelationshipSource = getStringMapSetting(Settings.StudentRelationshipSource) getOrElse Map()
	def studentRelationshipSource_= (setting: Map[String, String]) = settings += (Settings.StudentRelationshipSource -> setting)

	def studentRelationshipDisplayed = getStringMapSetting(Settings.StudentRelationshipDisplayed) getOrElse Map()
	def studentRelationshipDisplayed_= (setting: Map[String, String]) = settings += (Settings.StudentRelationshipDisplayed -> setting)

	def getStudentRelationshipDisplayed(relationshipType: StudentRelationshipType): Boolean =
		studentRelationshipDisplayed
			.get(relationshipType.id).fold(relationshipType.defaultDisplay)(_.toBoolean)

	def setStudentRelationshipDisplayed(relationshipType: StudentRelationshipType, isDisplayed: Boolean) = {
		studentRelationshipDisplayed = studentRelationshipDisplayed + (relationshipType.id -> isDisplayed.toString)
	}

	def getStudentRelationshipExpected(relationshipType: StudentRelationshipType, courseType: CourseType): Option[Boolean] =
		getStringMapSetting(Settings.StudentRelationshipExpected).flatMap(_.get(s"${relationshipType.id}-${courseType.code}").map(_.toBoolean))
	def setStudentRelationshipExpected(relationshipType: StudentRelationshipType, courseType: CourseType, isExpected: Boolean): Unit =
		settings += (Settings.StudentRelationshipExpected ->
			(getStringMapSetting(Settings.StudentRelationshipExpected).getOrElse(Map()) + (s"${relationshipType.id}-${courseType.code}" -> isExpected.toString))
		)

	@transient
	var relationshipService = Wire[RelationshipService]

	def displayedStudentRelationshipTypes =
		relationshipService.allStudentRelationshipTypes.filter { getStudentRelationshipDisplayed }

	def isStudentRelationshipTypeForDisplay(relationshipType: StudentRelationshipType) = displayedStudentRelationshipTypes.contains(relationshipType)

	def weekNumberingSystem = getStringSetting(Settings.WeekNumberingSystem) getOrElse WeekRange.NumberingSystem.Default
	def weekNumberingSystem_= (wnSystem: String) = settings += (Settings.WeekNumberingSystem -> wnSystem)

  def defaultGroupAllocationMethod =
		getStringSetting(Settings.DefaultGroupAllocationMethod).map(SmallGroupAllocationMethod(_)).getOrElse(SmallGroupAllocationMethod.Default)
  def defaultGroupAllocationMethod_= (method:SmallGroupAllocationMethod) =  settings += (Settings.DefaultGroupAllocationMethod->method.dbValue)

	// FIXME belongs in Freemarker
	def formattedGuidelineSummary:String = Option(extensionGuidelineSummary).fold("")({ raw =>
		val Splitter = """\s*\n(\s*\n)+\s*""".r // two+ newlines, with whitespace
		val nodes = Splitter.split(raw).map { p => <p>{p}</p>	}
		(NodeSeq fromSeq nodes).toString()
	})

	@transient
	var permissionsService = Wire[PermissionsService]
	@transient
	lazy val owners = permissionsService.ensureUserGroupFor(this, DepartmentalAdministratorRoleDefinition)
	@transient
	lazy val extensionManagers = permissionsService.ensureUserGroupFor(this, ExtensionManagerRoleDefinition)

	def isOwnedBy(userId:String) = owners.knownType.includesUserId(userId)

	@deprecated("Use ModuleAndDepartmentService.addOwner", "35")
	def addOwner(owner:String) = owners.knownType.addUserId(owner)

	@deprecated("Use ModuleAndDepartmentService.removeOwner", "35")
	def removeOwner(owner:String) = owners.knownType.removeUserId(owner)

	def canRequestExtension = allowExtensionRequests
	def isExtensionManager(user:String) = extensionManagers!=null && extensionManagers.knownType.includesUserId(user)

	def addFeedbackForm(form:FeedbackTemplate) = feedbackTemplates.add(form)

	def copySettingsFrom(other: Department) = {
		ensureSettings
		settings ++= other.settings
	}

	def copyExtensionManagersFrom(other: Department) = {
		extensionManagers.copyFrom(other.extensionManagers)
	}

	override def postLoad {
		ensureSettings
	}

	@OneToMany(mappedBy="scope", fetch = FetchType.LAZY, cascade = Array(CascadeType.ALL))
	@ForeignKey(name="none")
	@BatchSize(size=200)
	var grantedRoles:JList[DepartmentGrantedRole] = JArrayList()

	@Type(`type` = "uk.ac.warwick.tabula.data.model.DepartmentFilterRuleUserType")
	@Column(name="FilterRuleName")
	var filterRule: FilterRule = AllMembersFilterRule

	def includesMember(m: Member, d: Option[Department]): Boolean = Option(parent) match {
		case None => filterRule.matches(m, d)
		case Some(p) => filterRule.matches(m, d) && p.includesMember(m, d)
	}


	def subDepartmentsContaining(member: Member): Stream[Department] = {
		if (!includesMember(member, Option(this))) {
			Stream.empty // no point looking further down the tree if this level doesn't contain the required member
		} else {
			this #:: children.asScala.flatMap(child => child.subDepartmentsContaining(member)).toStream
		}
	}

	def replacedRoleDefinitionFor(roleDefinition: RoleDefinition) = {
		def matches(customRoleDefinition: CustomRoleDefinition) = {
			roleDefinition match {
				case roleDefinition: SelectorBuiltInRoleDefinition[_] =>
					customRoleDefinition.baseRoleDefinition match {
						case customRoleDefinition: SelectorBuiltInRoleDefinition[_]
							if (customRoleDefinition.getClass == roleDefinition.getClass) &&
								(roleDefinition <= customRoleDefinition) => true
						case _ => false
					}
				case _ => roleDefinition == customRoleDefinition.baseRoleDefinition
			}
		}

		customRoleDefinitions.asScala
			.filter { _.replacesBaseDefinition }
			.find { matches }
	}

	def permissionsParents = Option(parent).toStream
	override def humanReadableId = name
	override def urlSlug = code

	/** The 'top' ancestor of this department, or itself if
	  * it has no parent.
	  */
	@tailrec
	final def rootDepartment: Department =
		if (parent == null) this
		else parent.rootDepartment

	def hasParent = parent != null

	def hasChildren = !children.isEmpty

	def isUpstream = !hasParent

	override def toString = "Department(" + code + ")"

	def toEntityReference = new DepartmentEntityReference().put(this)

}

object Department {

	object FilterRule {
		// Define a way to get from a String to a FilterRule, for use in a ConvertibleConverter
		implicit val factory = { name: String => withName(name) }

		val allFilterRules: Seq[FilterRule] = {
			val inYearRules = (1 until 9).map(InYearFilterRule)
			Seq(AllMembersFilterRule, UndergraduateFilterRule, PostgraduateFilterRule, DepartmentRoutesFilterRule) ++ inYearRules
		}

		def withName(name: String): FilterRule = {
			allFilterRules.find(_.name == name).get
		}
	}

	sealed trait FilterRule extends Convertible[String] {
		val name: String
		val courseTypes: Seq[CourseType]
		def matches(member: Member, department: Option[Department]): Boolean
		def getName = name // for Spring
		def value = name
	}

	case object UndergraduateFilterRule extends FilterRule {
		val name = "UG"
		val courseTypes = CourseType.ugCourseTypes
		def matches(member: Member, department: Option[Department]) = member match {
			case s: StudentMember => s.mostSignificantCourseDetails.flatMap { cd => Option(cd.route) }.flatMap { route => Option(route.degreeType) } match {
				case Some(DegreeType.Undergraduate) => true
				case _ => false
			}
			case _ => false
		}
	}

	case object PostgraduateFilterRule extends FilterRule {
		val name = "PG"
		val courseTypes = CourseType.pgCourseTypes
		def matches(member: Member, department: Option[Department]) = member match {
			case s: StudentMember => s.mostSignificantCourseDetails.flatMap { cd => Option(cd.route) }.flatMap { route => Option(route.degreeType) } match {
				case Some(DegreeType.Undergraduate) => false
				case _ => true
			}
			case _ => false
		}
	}

	case object AllMembersFilterRule extends FilterRule {
		val name = "All"
		val courseTypes = CourseType.all
		def matches(member: Member, department: Option[Department]) = true
	}


	case class InYearFilterRule(year:Int) extends FilterRule {
		val name=s"Y$year"
		val courseTypes = CourseType.all
		def matches(member: Member, department: Option[Department]) = member match{
			case s:StudentMember => s.mostSignificantCourseDetails.exists(_.latestStudentCourseYearDetails.yearOfStudy == year)
			case _=>false
		}
	}

	case object DepartmentRoutesFilterRule extends FilterRule {
		val name = "DepartmentRoutes"
		val courseTypes = CourseType.all
		def matches(member: Member, department: Option[Department]) = member match {
			case s: StudentMember => s.mostSignificantCourseDetails.flatMap { cd => Option(cd.route) }.exists{r => department.exists(_ == r.adminDepartment)}
			case _ => false
		}
	}

	case class CompositeFilterRule(rules:Seq[FilterRule]) extends FilterRule{
		val name = rules.map(_.name).mkString(",")
		val courseTypes = CourseType.all
		def matches(member:Member, department: Option[Department]) = rules.forall(_.matches(member, department))
	}


	object Settings {
		val CollectFeedbackRatings = "collectFeedbackRatings"

		val AllowExtensionRequests = "allowExtensionRequests"
		val ExtensionGuidelineSummary = "extensionGuidelineSummary"
		val ExtensionGuidelineLink = "extensionGuidelineLink"

		val ShowStudentName = "showStudentName"
		val AssignmentInfoView = "assignmentInfoView"

		val PlagiarismDetection = "plagiarismDetection"
		val TurnitinExcludeBibliography = "turnitinExcludeBibliography"
		val TurnitinExcludeQuotations = "turnitinExcludeQuotations"
		val TurnitinSmallMatchWordLimit = "turnitinSmallMatchWordLimit"
		val TurnitinSmallMatchPercentageLimit = "turnitinSmallMatchPercentageLimit"

		val StudentRelationshipSource = "studentRelationshipSource"
		val StudentRelationshipDisplayed = "studentRelationshipDisplayed"
		val StudentRelationshipExpected = "studentRelationshipExpected"

		val WeekNumberingSystem = "weekNumberSystem"

    val DefaultGroupAllocationMethod = "defaultGroupAllocationMethod"

    val AutoGroupDeregistration = "autoGroupDeregistration"

		val StudentsCanScheduleMeetings = "studentsCanScheduleMeetings"

		val CanUploadMarksToSitsForYearUg = "canUploadMarksToSitsForYearUG"

		val CanUploadMarksToSitsForYearPg = "canUploadMarksToSitsForYearPG"

	}
}

// converter for spring
class DepartmentFilterRuleConverter extends ConvertibleConverter[String, Department.FilterRule]