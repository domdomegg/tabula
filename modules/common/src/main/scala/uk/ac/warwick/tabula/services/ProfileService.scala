package uk.ac.warwick.tabula.services

import org.joda.time.DateTime
import org.springframework.stereotype.Service
import uk.ac.warwick.spring.Wire
import uk.ac.warwick.tabula.AcademicYear
import uk.ac.warwick.tabula.data._
import uk.ac.warwick.tabula.data.Transactions._
import uk.ac.warwick.tabula.data.model._
import uk.ac.warwick.tabula.helpers.Logging
import uk.ac.warwick.tabula.commands.FiltersStudents
import uk.ac.warwick.userlookup.User
import uk.ac.warwick.tabula.helpers.StringUtils._
import java.util.UUID
import uk.ac.warwick.tabula.JavaImports._

/**
 * Service providing access to members and profiles.
 */
trait ProfileService {
	def save(member: Member)
	def regenerateTimetableHash(member: Member)
	def getMemberByUniversityId(universityId: String, disableFilter: Boolean = false, eagerLoad: Boolean = false): Option[Member]
	def getMemberByUniversityIdStaleOrFresh(universityId: String): Option[Member]
	def getAllMembersWithUniversityIds(universityIds: Seq[String]): Seq[Member]
	def getAllMembersWithUniversityIdsStaleOrFresh(universityIds: Seq[String]): Seq[Member]
	def getAllMembersWithUserId(userId: String, disableFilter: Boolean = false, eagerLoad: Boolean = false): Seq[Member]
	def getMemberByUser(user: User, disableFilter: Boolean = false, eagerLoad: Boolean = false): Option[Member]
	def getStudentBySprCode(sprCode: String): Option[StudentMember]
	def getMemberByTimetableHash(timetableHash: String): Option[Member]
	def findMembersByQuery(query: String, departments: Seq[Department], userTypes: Set[MemberUserType], isGod: Boolean): Seq[Member]
	def findMembersByDepartment(department: Department, includeTouched: Boolean, userTypes: Set[MemberUserType]): Seq[Member]
	def listMembersUpdatedSince(startDate: DateTime, max: Int): Seq[Member]
	def countStudentsByDepartment(department: Department): Int
	def getStudentsByRoute(route: Route): Seq[StudentMember]
	def getStudentsByRoute(route: Route, academicYear: AcademicYear): Seq[StudentMember]
	def getStudentCourseDetailsByScjCode(scjCode: String): Option[StudentCourseDetails]
	def getStudentCourseDetailsBySprCode(sprCode: String): Seq[StudentCourseDetails]
	def countStudentsByRestrictions(department: Department, restrictions: Seq[ScalaRestriction]): Int
	def countStudentsByRestrictionsInAffiliatedDepartments(department: Department, restrictions: Seq[ScalaRestriction]): Int
	def findStudentsByRestrictions(
		department: Department,
		restrictions: Seq[ScalaRestriction],
		orders: Seq[ScalaOrder] = Seq(),
		maxResults: Int = 50,
		startResult: Int = 0
	): (Int, Seq[StudentMember])
	def findStudentsByRestrictionsInAffiliatedDepartments(
		department: Department,
		restrictions: Seq[ScalaRestriction],
		orders: Seq[ScalaOrder] = Seq(),
		maxResults: Int = 50,
		startResult: Int = 0
	): (Int, Seq[StudentMember])
	def findAllStudentsByRestrictions(department: Department, restrictions: Seq[ScalaRestriction], orders: Seq[ScalaOrder] = Seq()): Seq[StudentMember]
	def findAllUniversityIdsByRestrictionsInAffiliatedDepartments(department: Department, restrictions: Seq[ScalaRestriction], orders: Seq[ScalaOrder] = Seq()): Seq[String]
	def findAllStudentDataByRestrictionsInAffiliatedDepartments(department: Department, restrictions: Seq[ScalaRestriction], academicYear: AcademicYear): Seq[AttendanceMonitoringStudentData]
	def getSCDsByAgentRelationshipAndRestrictions(
		relationshipType: StudentRelationshipType,
		agent: Member,
		restrictions: Seq[ScalaRestriction]
	): Seq[StudentCourseDetails]
	def findAllUniversityIdsByRestrictions(department: Department, restrictions: Seq[ScalaRestriction]): Seq[String]
	def findStaffMembersWithAssistant(user: User): Seq[StaffMember]
	def allModesOfAttendance(department: Department): Seq[ModeOfAttendance]
	def allSprStatuses(department: Department): Seq[SitsStatus]
	def getDisability(code: String): Option[Disability]
}

abstract class AbstractProfileService extends ProfileService with Logging {

	self: MemberDaoComponent
		with StudentCourseDetailsDaoComponent
		with StaffAssistantsHelpers =>

	var profileIndexService = Wire.auto[ProfileIndexService]

	def getMemberByUniversityId(universityId: String, disableFilter: Boolean = false, eagerLoad: Boolean = false) = transactional(readOnly = true) {
		memberDao.getByUniversityId(universityId, disableFilter, eagerLoad)
	}

	def getMemberByUniversityIdStaleOrFresh(universityId: String) = transactional(readOnly = true) {
		memberDao.getByUniversityIdStaleOrFresh(universityId)
	}

	def getAllMembersWithUniversityIds(universityIds: Seq[String]) = transactional(readOnly = true) {
		memberDao.getAllWithUniversityIds(universityIds)
	}

	def getAllMembersWithUniversityIdsStaleOrFresh(universityIds: Seq[String]) = transactional(readOnly = true) {
		memberDao.getAllWithUniversityIdsStaleOrFresh(universityIds)
	}

	def getAllMembersWithUserId(userId: String, disableFilter: Boolean = false, eagerLoad: Boolean = false) = transactional(readOnly = true) {
		memberDao.getAllByUserId(userId, disableFilter, eagerLoad)
	}

	def getMemberByUser(user: User, disableFilter: Boolean = false, eagerLoad: Boolean = false) = {
		val allMembers = getAllMembersWithUserId(user.getUserId, disableFilter, eagerLoad)
		val usercodeMatch =
			allMembers.find(_.universityId == user.getWarwickId)
								.orElse(allMembers.headOption) // TAB-1716

		if (usercodeMatch.isDefined || !user.getWarwickId.hasText) {
			usercodeMatch
		} else {
			// TAB-2014 look for a universityId match, but only return it if the email address matches
			getMemberByUniversityId(user.getWarwickId, disableFilter, eagerLoad)
				.filter(_.email.safeTrim.safeLowercase == user.getEmail.safeTrim.safeLowercase)
		}
	}

	def getStudentBySprCode(sprCode: String) = transactional(readOnly = true) {
		studentCourseDetailsDao.getStudentBySprCode(sprCode)
	}

	def getMemberByTimetableHash(timetableHash: String): Option[Member] = {
		memberDao.getMemberByTimetableHash(timetableHash)
	}

	def regenerateTimetableHash(member: Member) = memberDao.setTimetableHash(member, UUID.randomUUID.toString)

	def findMembersByQuery(query: String, departments: Seq[Department], userTypes: Set[MemberUserType], isGod: Boolean) = transactional(readOnly = true) {
		profileIndexService.find(query, departments, userTypes, isGod)
	}

	def findMembersByDepartment(department: Department, includeTouched: Boolean, userTypes: Set[MemberUserType]) = transactional(readOnly = true) {
		profileIndexService.find(department, includeTouched, userTypes)
	}

	def listMembersUpdatedSince(startDate: DateTime, max: Int) = transactional(readOnly = true) {
		memberDao.listUpdatedSince(startDate, max)
	}

	def save(member: Member) = memberDao.saveOrUpdate(member)

  def countStudentsByDepartment(department: Department): Int = transactional(readOnly = true) {
			memberDao.getStudentsByDepartment(department.rootDepartment).count(s => department.filterRule.matches(s, Option(department)))
	}

	def getStudentsByRoute(route: Route): Seq[StudentMember] = transactional(readOnly = true) {
		studentCourseDetailsDao.getByRoute(route)
			.filter{s => s.statusOnRoute!= null && !s.statusOnRoute.code.startsWith("P")}
			.filter(s => s.mostSignificant == JBoolean(Option(true)))
			.map(_.student)
	}

	def getStudentsByRoute(route: Route, academicYear: AcademicYear): Seq[StudentMember] = transactional(readOnly = true) {
		studentCourseDetailsDao.getByRoute(route)
			.filter{s => s.statusOnRoute!= null && !s.statusOnRoute.code.startsWith("P")}
			.filter(s => s.mostSignificant == JBoolean(Option(true)))
			.filter(_.freshStudentCourseYearDetails.exists(s => s.academicYear == academicYear))
			.map(_.student)
	}

	def getStudentCourseDetailsByScjCode(scjCode: String): Option[StudentCourseDetails] =
		studentCourseDetailsDao.getByScjCode(scjCode)

	def getStudentCourseDetailsBySprCode(sprCode: String): Seq[StudentCourseDetails] =
		studentCourseDetailsDao.getBySprCode(sprCode)

	private def studentDepartmentFilterMatches(department: Department)(member: StudentMember) = department.filterRule.matches(member, Option(department))

	/**
	 * this returns a tuple of the startResult (offset into query) actually returned, with the resultset itself
	 */
	def findStudentsByRestrictions(
		department: Department,
		restrictions: Seq[ScalaRestriction],
		orders: Seq[ScalaOrder] = Seq(),
		maxResults: Int = 50,
		startResult: Int = 0
	): (Int, Seq[StudentMember]) = transactional(readOnly = true) {
		// If we're a sub/parent department then we have to fetch everyone, boo! Otherwise, we can use nice things
		if (department.hasParent || department.hasChildren) {
			val allRestrictions = ScalaRestriction.is(
				"studentCourseYearDetails.enrolmentDepartment", department.rootDepartment,
				FiltersStudents.AliasPaths("studentCourseYearDetails") : _*
			) ++ restrictions

			val filteredStudents = memberDao.findStudentsByRestrictions(allRestrictions, orders, Int.MaxValue, 0)
				.filter(studentDepartmentFilterMatches(department))

			if (filteredStudents.isEmpty)
				(0, Seq())
			else if (startResult > 0 && filteredStudents.size > maxResults) {
				(startResult, filteredStudents.slice(startResult, startResult + maxResults))
			} else {
				// return the first page of results, notifying zero offset
				(0, filteredStudents.take(maxResults))
			}
		}	else {
			val allRestrictions = ScalaRestriction.is(
				"studentCourseYearDetails.enrolmentDepartment", department,
				FiltersStudents.AliasPaths("studentCourseYearDetails") : _*
			) ++ restrictions

			val offsetStudents = memberDao.findStudentsByRestrictions(allRestrictions, orders, maxResults, startResult)

			if (offsetStudents.nonEmpty) {
				(startResult, offsetStudents)
			} else {
				// meh, have to hit DAO twice if no results for this offset, but at least this should be a rare occurrence
				val unoffsetStudents = memberDao.findStudentsByRestrictions(allRestrictions, orders, maxResults, 0)
				if (unoffsetStudents.isEmpty) {
					(0, Seq())
				} else {
					(0, unoffsetStudents)
				}
			}
		}
	}

	/**
	 * this returns a tuple of the startResult (offset into query) actually returned, with the resultset itself
	 */
	def findStudentsByRestrictionsInAffiliatedDepartments(
		department: Department,
		restrictions: Seq[ScalaRestriction],
		orders: Seq[ScalaOrder] = Seq(),
		maxResults: Int = 50,
		startResult: Int = 0
	): (Int, Seq[StudentMember]) = transactional(readOnly = true) {
		val allRestrictions = affiliatedDepartmentsRestriction(department, restrictions)

		// If we're a sub/parent department then we have to fetch everyone, boo! Otherwise, we can use nice things
		if (department.hasParent || department.hasChildren) {
			val filteredStudents = memberDao.findStudentsByRestrictions(allRestrictions, orders, Int.MaxValue, 0)
				.filter(studentDepartmentFilterMatches(department))

			if (filteredStudents.isEmpty)
				(0, Seq())
			else if (startResult > 0 && filteredStudents.size > maxResults) {
				(startResult, filteredStudents.slice(startResult, startResult + maxResults))
			} else {
				// return the first page of results, notifying zero offset
				(0, filteredStudents.take(maxResults))
			}
		}	else {
			val offsetStudents = memberDao.findStudentsByRestrictions(allRestrictions, orders, maxResults, startResult)

			if (offsetStudents.nonEmpty) {
				(startResult, offsetStudents)
			} else {
				// meh, have to hit DAO twice if no results for this offset, but at least this should be a rare occurrence
				val unoffsetStudents = memberDao.findStudentsByRestrictions(allRestrictions, orders, maxResults, 0)
				if (unoffsetStudents.isEmpty) {
					(0, Seq())
				} else {
					(0, unoffsetStudents)
				}
			}
		}
	}

	def findAllStudentsByRestrictions(
		department: Department,
		restrictions: Seq[ScalaRestriction],
		orders: Seq[ScalaOrder] = Seq()
	) = transactional(readOnly = true) {
		if (department.hasParent) {
			val allRestrictions = ScalaRestriction.is(
				"studentCourseYearDetails.enrolmentDepartment", department.rootDepartment,
				FiltersStudents.AliasPaths("studentCourseYearDetails") : _*
			) ++ restrictions

			memberDao.findStudentsByRestrictions(allRestrictions, orders, Int.MaxValue, 0)
				.filter(studentDepartmentFilterMatches(department))
		}	else {
			val allRestrictions = ScalaRestriction.is(
				"studentCourseYearDetails.enrolmentDepartment", department,
				FiltersStudents.AliasPaths("studentCourseYearDetails") : _*
			) ++ restrictions

			memberDao.findStudentsByRestrictions(allRestrictions, orders, Int.MaxValue, 0)
		}
	}

	def getSCDsByAgentRelationshipAndRestrictions(
		relationshipType: StudentRelationshipType,
		agent: Member,
		restrictions: Seq[ScalaRestriction]
	): Seq[StudentCourseDetails] = transactional(readOnly = true) {
		memberDao.getSCDsByAgentRelationshipAndRestrictions(relationshipType, agent.id, restrictions)
	}

	private def affiliatedDepartmentsRestriction(department: Department, restrictions: Seq[ScalaRestriction]) = {
		val queryDepartment = {
			if (department.hasParent)
				department.rootDepartment
			else
				department
		}

		val departmentRestriction = Aliasable.addAliases(
			new ScalaRestriction(
				org.hibernate.criterion.Restrictions.or(
					Daoisms.is("studentCourseYearDetails.enrolmentDepartment", queryDepartment),
					Daoisms.is("route.adminDepartment", queryDepartment),
					Daoisms.is("homeDepartment", queryDepartment),
					org.hibernate.criterion.Restrictions.and(
						Daoisms.is("route.teachingDepartmentsActive", true),
						Daoisms.is("teachingInfo.department", queryDepartment)
					)
				)
			),
			Seq(
				FiltersStudents.AliasPaths("studentCourseYearDetails"),
				FiltersStudents.AliasPaths("route"),
				FiltersStudents.AliasPaths("teachingInfo")
			).flatten : _*
		)

		Seq(departmentRestriction) ++ restrictions
	}

	def findAllUniversityIdsByRestrictions(department: Department, restrictions: Seq[ScalaRestriction]) = transactional(readOnly = true) {
		val allRestrictions = {
			if (department.hasParent) {
				ScalaRestriction.is(
					"studentCourseYearDetails.enrolmentDepartment", department.rootDepartment,
					FiltersStudents.AliasPaths("studentCourseYearDetails") : _*
				) ++ restrictions
			}	else {
				ScalaRestriction.is(
					"studentCourseYearDetails.enrolmentDepartment", department,
					FiltersStudents.AliasPaths("studentCourseYearDetails") : _*
				) ++ restrictions
			}
		}
		memberDao.findUniversityIdsByRestrictions(allRestrictions)
	}

	def findAllUniversityIdsByRestrictionsInAffiliatedDepartments(
		department: Department,
		restrictions: Seq[ScalaRestriction],
		orders: Seq[ScalaOrder] = Seq()
	) = transactional(readOnly = true) {

		val allRestrictions = affiliatedDepartmentsRestriction(department, restrictions)

		if (department.hasParent) {
			// TODO this sucks. Would be better if you could get ScalaRestrictions from a filter rule and add them to allRestrictions
			memberDao.findStudentsByRestrictions(allRestrictions, orders, Int.MaxValue, 0)
				.filter(studentDepartmentFilterMatches(department)).map(_.universityId)
		}	else {
			memberDao.findUniversityIdsByRestrictions(allRestrictions, orders)
		}
	}

	def findAllStudentDataByRestrictionsInAffiliatedDepartments(department: Department, restrictions: Seq[ScalaRestriction], academicYear: AcademicYear): Seq[AttendanceMonitoringStudentData] = {
		val allRestrictions = affiliatedDepartmentsRestriction(department, restrictions)

		if (department.hasParent) {
			// TODO this sucks. Would be better if you could get ScalaRestrictions from a filter rule and add them to allRestrictions
			memberDao.findStudentsByRestrictions(allRestrictions, Seq(), Int.MaxValue, 0)
				.filter(studentDepartmentFilterMatches(department))
				.flatMap(student => {
					val beginDates = student.freshStudentCourseDetails.filter(_.freshStudentCourseYearDetails.exists(_.academicYear == academicYear)).map(_.beginDate)
					if (beginDates.nonEmpty) {
						// do not remove; import needed for sorting
						// should be: import uk.ac.warwick.tabula.helpers.DateTimeOrdering._
						import uk.ac.warwick.tabula.helpers.DateTimeOrdering._
						Option(AttendanceMonitoringStudentData(student.firstName, student.lastName, student.universityId, student.userId, beginDates.min))
					} else {
						None
					}
				})
		}	else {
			memberDao.findAllStudentDataByRestrictions(allRestrictions, academicYear: AcademicYear)
		}
	}

	def countStudentsByRestrictions(department: Department, restrictions: Seq[ScalaRestriction]): Int = transactional(readOnly = true) {
		// Because of the implementation of sub-departments, unfortunately we can't get optimisations here.
		if (department.hasParent) findAllStudentsByRestrictions(department, restrictions).size
		else {
			val allRestrictions = ScalaRestriction.is(
				"studentCourseYearDetails.enrolmentDepartment", department,
				FiltersStudents.AliasPaths("studentCourseYearDetails") : _*
			) ++ restrictions

			memberDao.countStudentsByRestrictions(allRestrictions)
		}
	}

	def countStudentsByRestrictionsInAffiliatedDepartments(department: Department, restrictions: Seq[ScalaRestriction]): Int = transactional(readOnly = true) {
		val allRestrictions = affiliatedDepartmentsRestriction(department, restrictions)

		if (department.hasParent) {
			// TODO this sucks. Would be better if you could get ScalaRestrictions from a filter rule and add them to allRestrictions
			memberDao.findStudentsByRestrictions(allRestrictions, Seq(), Int.MaxValue, 0)
				.filter(studentDepartmentFilterMatches(department)).map(_.universityId)
				.size
		}	else {
			memberDao.countStudentsByRestrictions(allRestrictions)
		}
	}

	def findStaffMembersWithAssistant(user: User) = staffAssistantsHelper.findBy(user)

	def allModesOfAttendance(department: Department): Seq[ModeOfAttendance] = transactional(readOnly = true) {
		memberDao.getAllModesOfAttendance(department).filter(_ != null)
	}

	def allSprStatuses(department: Department): Seq[SitsStatus] = transactional(readOnly = true) {
		memberDao.getAllSprStatuses(department).filter(_ != null)
	}

	def getDisability(code: String): Option[Disability] = transactional(readOnly=true) {
		// lookup disability iff a non-null code is passed, otherwise fallback to None - I <3 scala options and flatMap
		Option(code).flatMap(memberDao.getDisability)
	}
}

trait StaffAssistantsHelpers {
	val staffAssistantsHelper: UserGroupMembershipHelperMethods[StaffMember]
}

trait StaffAssistantsHelpersImpl extends StaffAssistantsHelpers {
	lazy val staffAssistantsHelper = new UserGroupMembershipHelper[StaffMember]("_assistantsGroup")
}

trait ProfileServiceComponent {
	def profileService: ProfileService
}

trait AutowiringProfileServiceComponent extends ProfileServiceComponent {
	var profileService = Wire[ProfileService]
}

@Service("profileService")
class ProfileServiceImpl
	extends AbstractProfileService
	with AutowiringMemberDaoComponent
	with AutowiringStudentCourseDetailsDaoComponent
	with StaffAssistantsHelpersImpl
