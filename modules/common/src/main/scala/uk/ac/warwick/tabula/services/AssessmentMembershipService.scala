package uk.ac.warwick.tabula.services

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import uk.ac.warwick.spring.Wire
import uk.ac.warwick.tabula.AcademicYear
import uk.ac.warwick.tabula.data.AssessmentMembershipDao
import uk.ac.warwick.tabula.data.model._
import uk.ac.warwick.tabula.helpers.{FoundUser, Logging}
import uk.ac.warwick.userlookup.{AnonymousUser, User}

trait AssessmentMembershipService {
	def assignmentManualMembershipHelper: UserGroupMembershipHelperMethods[Assignment]
	def examManualMembershipHelper: UserGroupMembershipHelperMethods[Exam]

	def find(assignment: AssessmentComponent): Option[AssessmentComponent]
	def find(group: UpstreamAssessmentGroup): Option[UpstreamAssessmentGroup]
	def find(group: AssessmentGroup): Option[AssessmentGroup]
	def save(group: AssessmentGroup): Unit
	def delete(group: AssessmentGroup): Unit
	def getAssessmentGroup(id: String): Option[AssessmentGroup]
	def getAssessmentGroup(template: AssessmentGroup): Option[AssessmentGroup]
	def getUpstreamAssessmentGroup(template: UpstreamAssessmentGroup): Option[UpstreamAssessmentGroup]
	def getUpstreamAssessmentGroup(id:String): Option[UpstreamAssessmentGroup]
	def getAssessmentComponent(id: String): Option[AssessmentComponent]
	def getAssessmentComponent(group: UpstreamAssessmentGroup): Option[AssessmentComponent]

	/**
	 * Get all AssessmentComponents that appear to belong to this module.
	 *
	 *  Typically used to provide possible candidates to link to an app assignment,
	 *  in conjunction with #getUpstreamAssessmentGroups.
	 */
	def getAssessmentComponents(module: Module): Seq[AssessmentComponent]
	def getAssessmentComponents(department: Department, includeSubDepartments: Boolean): Seq[AssessmentComponent]
	def getAssessmentComponents(moduleCode: String): Seq[AssessmentComponent]

	/**
	 * Get all assessment groups that can serve this assignment this year.
	 * Should return as many groups as there are distinct OCCURRENCE values for a given
	 * assessment group code, which most of the time is just 1.
	 */
	def getUpstreamAssessmentGroups(component: AssessmentComponent, academicYear: AcademicYear): Seq[UpstreamAssessmentGroup]

	def save(assignment: AssessmentComponent): AssessmentComponent
	def save(group: UpstreamAssessmentGroup): Unit
	// empty the usergroups for all assessmentgroups in the specified academic years. Skip any groups specified in ignore
	def emptyMembers(groupsToEmpty:Seq[String]): Int
	def replaceMembers(group: UpstreamAssessmentGroup, registrations: Seq[UpstreamModuleRegistration]): UpstreamAssessmentGroup
	def updateSeatNumbers(group: UpstreamAssessmentGroup, seatNumberMap: Map[String, Int]): Unit

	def getUpstreamAssessmentGroupsNotIn(ids: Seq[String], academicYears: Seq[AcademicYear]): Seq[String]

	def getEnrolledAssignments(user: User): Seq[Assignment]

	/**
	 * This will throw an exception if the others are usercode groups, use determineMembership instead in that situation
	 */
	def countMembershipWithUniversityIdGroup(upstream: Seq[UpstreamAssessmentGroup], others: Option[UnspecifiedTypeUserGroup]): Int

	def determineMembership(upstream: Seq[UpstreamAssessmentGroup], others: Option[UnspecifiedTypeUserGroup]): AssessmentMembershipInfo
	def determineMembership(assessment: Assessment): AssessmentMembershipInfo
	def determineMembershipUsers(upstream: Seq[UpstreamAssessmentGroup], others: Option[UnspecifiedTypeUserGroup]): Seq[User]
	def determineMembershipUsers(assessment: Assessment): Seq[User]
	def determineMembershipUsersWithOrder(exam: Exam): Seq[(User, Option[Int])]
	def determineMembershipUsersWithOrderForMarker(exam: Exam, marker: User): Seq[(User, Option[Int])]
	def determineMembershipIds(upstream: Seq[UpstreamAssessmentGroup], others: Option[UnspecifiedTypeUserGroup]): Seq[String]

	def isStudentMember(user: User, upstream: Seq[UpstreamAssessmentGroup], others: Option[UnspecifiedTypeUserGroup]): Boolean

	def save(gb: GradeBoundary): Unit
	def deleteGradeBoundaries(marksCode: String): Unit
	def gradesForMark(component: AssessmentComponent, mark: Int): Seq[GradeBoundary]
}



@Service(value = "assignmentMembershipService")
class AssessmentMembershipServiceImpl
	extends AssessmentMembershipService
	with AssessmentMembershipMethods
	with UserLookupComponent
	with Logging {

	@Autowired var userLookup: UserLookupService = _
	@Autowired var dao: AssessmentMembershipDao = _

	val assignmentManualMembershipHelper = new UserGroupMembershipHelper[Assignment]("_members")
	val examManualMembershipHelper = new UserGroupMembershipHelper[Exam]("_members")

	def getEnrolledAssignments(user: User): Seq[Assignment] = {
		val autoEnrolled =
			dao.getSITSEnrolledAssignments(user)
				.filterNot { _.members.excludesUser(user) }

		// TAB-1749 If we've been passed a non-primary usercode (e.g. WBS logins)
		// then also get registrations for the primary usercode
		val allManuallyEnrolled = {
			val ssoUser = if (user.getWarwickId != null) userLookup.getUserByWarwickUniId(user.getWarwickId) else userLookup.getUserByUserId(user.getUserId)
			ssoUser match {
				case FoundUser(primaryUser) if primaryUser.getUserId != user.getUserId =>
					assignmentManualMembershipHelper.findBy(primaryUser) ++ assignmentManualMembershipHelper.findBy(user)

				case _ => assignmentManualMembershipHelper.findBy(user)
			}
		}

		val manuallyEnrolled =
			allManuallyEnrolled
				.filterNot { assignment => assignment.deleted || assignment.archived }

		(autoEnrolled ++ manuallyEnrolled).distinct
	}

	def emptyMembers(groupsToEmpty:Seq[String]) =
		dao.emptyMembers(groupsToEmpty:Seq[String])

	def replaceMembers(template: UpstreamAssessmentGroup, registrations: Seq[UpstreamModuleRegistration]) = {
		if (debugEnabled) debugReplace(template, registrations.map(_.universityId))

		getUpstreamAssessmentGroup(template).map { group =>
			group.members.knownType.replaceStaticUsers(registrations)
			group
		} getOrElse {
			logger.warn("No such assessment group found: " + template.toString)
			template
		}
	}

	private def debugReplace(template: UpstreamAssessmentGroup, universityIds: Seq[String]) {
		logger.debug("Setting %d members in group %s" format (universityIds.size, template.toString))
	}

	def updateSeatNumbers(group: UpstreamAssessmentGroup, seatNumberMap: Map[String, Int]) =
		dao.updateSeatNumbers(group, seatNumberMap)

	/**
	 * Tries to find an identical AssessmentComponent in the database, based on the
	 * fact that moduleCode and sequence uniquely identify the assignment.
	 */
	def find(assignment: AssessmentComponent): Option[AssessmentComponent] = dao.find(assignment)
	def find(group: UpstreamAssessmentGroup): Option[UpstreamAssessmentGroup] = dao.find(group)
	def find(group: AssessmentGroup): Option[AssessmentGroup] = dao.find(group)
	def save(group:AssessmentGroup) = dao.save(group)
	def save(assignment: AssessmentComponent): AssessmentComponent = dao.save(assignment)
	def save(group: UpstreamAssessmentGroup) = dao.save(group)

	def getAssessmentGroup(id:String) = dao.getAssessmentGroup(id)
	def getAssessmentGroup(template: AssessmentGroup): Option[AssessmentGroup] = find(template)
	def getUpstreamAssessmentGroup(template: UpstreamAssessmentGroup): Option[UpstreamAssessmentGroup] = find(template)
	def getUpstreamAssessmentGroup(id:String) = dao.getUpstreamAssessmentGroup(id)

	def delete(group: AssessmentGroup) { dao.delete(group) }

	def getAssessmentComponent(id: String) = dao.getAssessmentComponent(id)

	def getAssessmentComponent(group: UpstreamAssessmentGroup) = dao.getAssessmentComponent(group)

	/**
	 * Gets assessment components for this module.
	 */
	def getAssessmentComponents(module: Module) = dao.getAssessmentComponents(module)

	/**
	 * Gets assessment components by SITS module code
	 */
	def getAssessmentComponents(moduleCode: String) = dao.getAssessmentComponents(moduleCode)

	/**
	 * Gets assessment components for this department.
	 */
	def getAssessmentComponents(department: Department, includeSubDepartments: Boolean) = dao.getAssessmentComponents(department, includeSubDepartments)

	def countPublishedFeedback(assignment: Assignment): Int = dao.countPublishedFeedback(assignment)

	def countFullFeedback(assignment: Assignment): Int = dao.countFullFeedback(assignment)

	def getUpstreamAssessmentGroups(component: AssessmentComponent, academicYear: AcademicYear): Seq[UpstreamAssessmentGroup] =
		dao.getUpstreamAssessmentGroups(component, academicYear)

	def getUpstreamAssessmentGroupsNotIn(ids: Seq[String], academicYears: Seq[AcademicYear]): Seq[String] =
		dao.getUpstreamAssessmentGroupsNotIn(ids, academicYears)

	def save(gb: GradeBoundary): Unit = {
		dao.save(gb)
	}

	def deleteGradeBoundaries(marksCode: String): Unit = {
		dao.deleteGradeBoundaries(marksCode)
	}

	def gradesForMark(component: AssessmentComponent, mark: Int): Seq[GradeBoundary] = {
		def gradeBoundaryMatchesMark(gb: GradeBoundary) = gb.minimumMark <= mark && gb.maximumMark >= mark

		component.marksCode match {
			case code: String => 
				dao.getGradeBoundaries(code).filter(gradeBoundaryMatchesMark)
			case _ =>
				Seq()
		}
	}

}

class AssessmentMembershipInfo(val items: Seq[MembershipItem]) {
	val	sitsCount = items.count(_.itemType == SitsType)
	val	totalCount = items.filterNot(_.itemType == ExcludeType).size
	val includeCount = items.count(_.itemType == IncludeType)
	val excludeCount = items.count(_.itemType == ExcludeType)
	val usedIncludeCount = items.count(i => i.itemType == IncludeType && !i.extraneous)
	val usedExcludeCount = items.count(i => i.itemType == ExcludeType && !i.extraneous)
}

trait AssessmentMembershipMethods extends Logging {

	self: AssessmentMembershipService with UserLookupComponent =>

	def determineMembership(upstream: Seq[UpstreamAssessmentGroup], others: Option[UnspecifiedTypeUserGroup]): AssessmentMembershipInfo = {
		for (group <- upstream) assert(group.members.universityIds)

		val sitsUsers =
			userLookup.getUsersByWarwickUniIds(upstream.flatMap { _.members.members }.distinct).toSeq

		val includes = others.map(_.users.map(u => u.getUserId -> u)).getOrElse(Nil)
		val excludes = others.map(_.excludes.map(u => u.getUserId -> u)).getOrElse(Nil)

		// convert lists of Users to lists of MembershipItems that we can render neatly together.

		val includeItems = makeIncludeItems(includes, sitsUsers)
		val excludeItems = makeExcludeItems(excludes, sitsUsers)
		val sitsItems = makeSitsItems(includes, excludes, sitsUsers)

		val sorted = (includeItems ++ excludeItems ++ sitsItems)
			.sortBy(membershipItem => (membershipItem.user.getLastName, membershipItem.user.getFirstName))

		new AssessmentMembershipInfo(sorted)
	}

	def determineMembership(assessment: Assessment) : AssessmentMembershipInfo = assessment match {
		case a: Assignment => determineMembership(a.upstreamAssessmentGroups, Option(a.members))
		case e: Exam => determineMembership(e.upstreamAssessmentGroups, None)
	}

	/**
	 * Returns just a list of User objects who are on this assessment group.
	 */
	def determineMembershipUsers(upstream: Seq[UpstreamAssessmentGroup], others: Option[UnspecifiedTypeUserGroup]): Seq[User] = {
		determineMembership(upstream, others).items filter notExclude map toUser filter notNull filter notAnonymous
	}

	/**
	 * Returns a simple list of User objects for students who are enrolled on this assessment. May be empty.
	 */
	def determineMembershipUsers(assessment: Assessment): Seq[User] = assessment match {
		case a: Assignment => determineMembershipUsers(a.upstreamAssessmentGroups, Option(a.members))
		case e: Exam => determineSitsMembership(e.upstreamAssessmentGroups).map(_.memberId).map(userLookup.getUserByWarwickUniId)
	}

	def determineMembershipUsersWithOrder(exam: Exam): Seq[(User, Option[Int])] =
		exam.upstreamAssessmentGroups.flatMap(_.sortedMembers).distinct.sortBy(_.position)
			.map(m => userLookup.getUserByWarwickUniId(m.memberId) -> m.position)

	def determineMembershipUsersWithOrderForMarker(exam: Exam, marker:User): Seq[(User, Option[Int])] =
		if (!exam.released || exam.markingWorkflow == null) Seq()
		else {
			val markersStudents = exam.markingWorkflow.getMarkersStudents(exam, marker)
			determineMembershipUsersWithOrder(exam).filter(s => markersStudents.contains(s._1))
		}

	private def determineSitsMembership(upstream: Seq[UpstreamAssessmentGroup]) =
		upstream.flatMap(_.sortedMembers).distinct.sortBy(_.position)


	def determineMembershipIds(upstream: Seq[UpstreamAssessmentGroup], others: Option[UnspecifiedTypeUserGroup]): Seq[String] = {
		others.foreach { g => assert(g.universityIds) }
		for (group <- upstream) assert(group.members.universityIds)

		val sitsUsers = upstream.flatMap { _.members.members }.distinct

		val includes = others.map(_.knownType.members).getOrElse(Nil)
		val excludes = others.map(_.knownType.excludedUserIds).getOrElse(Nil)

		(sitsUsers ++ includes).distinct diff excludes.distinct
	}

	def countMembershipWithUniversityIdGroup(upstream: Seq[UpstreamAssessmentGroup], others: Option[UnspecifiedTypeUserGroup]) = {
		others match {
			case Some(group) if !group.universityIds =>
				logger.warn("Attempted to use countMembershipWithUniversityIdGroup() with a usercode-type UserGroup. Falling back to determineMembership()")
				determineMembershipUsers(upstream, others).size
			case _ =>
				val sitsUsers = upstream.flatMap { _.members.members }

				val includes = others map { _.knownType.allIncludedIds } getOrElse Nil
				val excludes = others map { _.knownType.allExcludedIds } getOrElse Nil

				((sitsUsers ++ includes).distinct diff excludes).size
		}
	}

	def isStudentMember(user: User, upstream: Seq[UpstreamAssessmentGroup], others: Option[UnspecifiedTypeUserGroup]): Boolean = {
		if (others.exists(_.excludesUser(user))) false
		else if (others.exists(_.includesUser(user))) true
		else upstream.exists {
			_.members.staticUserIds.contains(user.getWarwickId) //Yes, definitely Uni ID when checking SITS group
		}
	}

	private def sameUserIdAs(user: User) = (other: (String, User)) => { user.getUserId == other._2.getUserId }
	private def in(seq: Seq[(String, User)]) = (other: (String, User)) => { seq exists sameUserIdAs(other._2) }

	private def makeIncludeItems(includes: Seq[(String, User)], sitsUsers: Seq[(String, User)]) =
		includes map {
			case (id, user) =>
				val extraneous = sitsUsers exists sameUserIdAs(user)
				MembershipItem(
					user = user,
					universityId = universityId(user, None),
					userId = userId(user, Some(id)),
					itemType = IncludeType,
					extraneous = extraneous)
		}

	private def makeExcludeItems(excludes: Seq[(String, User)], sitsUsers: Seq[(String, User)]) =
		excludes map {
			case (id, user) =>
				val extraneous = !(sitsUsers exists sameUserIdAs(user))
				MembershipItem(
					user = user,
					universityId = universityId(user, None),
					userId = userId(user, Some(id)),
					itemType = ExcludeType,
					extraneous = extraneous)
		}

	private def makeSitsItems(includes: Seq[(String, User)], excludes: Seq[(String, User)], sitsUsers: Seq[(String, User)]) =
		sitsUsers filterNot in(includes) filterNot in(excludes) map {
			case (id, user) =>
				MembershipItem(
					user = user,
					universityId = universityId(user, Some(id)),
					userId = userId(user, None),
					itemType = SitsType,
					extraneous = false)
		}

	private def universityId(user: User, fallback: Option[String]) = option(user) map { _.getWarwickId } orElse fallback
	private def userId(user: User, fallback: Option[String]) = option(user) map { _.getUserId } orElse fallback

	private def option(user: User): Option[User] = user match {
		case FoundUser(u) => Some(user)
		case _ => None
	}

	private def toUser(item: MembershipItem) = item.user
	private def notExclude(item: MembershipItem) = item.itemType != ExcludeType
	private def notNull[A](any: A) = { any != null }
	private def notAnonymous(user: User) = { !user.isInstanceOf[AnonymousUser] }
}

abstract class MembershipItemType(val value: String)
case object SitsType extends MembershipItemType("sits")
case object IncludeType extends MembershipItemType("include")
case object ExcludeType extends MembershipItemType("exclude")

/** Item in list of members for displaying in view.
	*/
case class MembershipItem(
	user: User,
	universityId: Option[String],
	userId: Option[String],
	itemType: MembershipItemType, // sits, include or exclude
	/**
	* If include type, this item adds a user who's already in SITS.
	* If exclude type, this item excludes a user who isn't in the list anyway.
	*/
	extraneous: Boolean) {

	def itemTypeString = itemType.value
}

trait AssessmentMembershipServiceComponent {
	def assessmentMembershipService: AssessmentMembershipService
}

trait AutowiringAssessmentMembershipServiceComponent extends AssessmentMembershipServiceComponent {
	var assessmentMembershipService = Wire[AssessmentMembershipService]
}

trait GeneratesGradesFromMarks {
	def applyForMarks(marks: Map[String, Int]): Map[String, Seq[GradeBoundary]]
}