package uk.ac.warwick.tabula.data

import org.hibernate.FetchMode
import org.hibernate.criterion.{Order, Projections, Restrictions}
import org.hibernate.sql.JoinType
import org.joda.time.DateTime
import org.springframework.stereotype.Repository
import uk.ac.warwick.spring.Wire
import uk.ac.warwick.tabula.commands.{StudentAssociationEntityData, StudentAssociationData}
import uk.ac.warwick.tabula.data.model.{MemberStudentRelationship, _}
import uk.ac.warwick.tabula.helpers.Logging

trait RelationshipDaoComponent {
	val relationshipDao: RelationshipDao
}

trait AutowiringRelationshipDaoComponent extends RelationshipDaoComponent {
	val relationshipDao = Wire[RelationshipDao]
}

trait RelationshipDao {
	def allStudentRelationshipTypes: Seq[StudentRelationshipType]
	def getStudentRelationshipTypeById(id: String): Option[StudentRelationshipType]
	def getStudentRelationshipTypeByUrlPart(urlPart: String): Option[StudentRelationshipType]
	def saveOrUpdate(relationshipType: StudentRelationshipType)
	def delete(relationshipType: StudentRelationshipType)
	def getStudentRelationshipById(id: String): Option[StudentRelationship]

	def saveOrUpdate(rel: StudentRelationship)

	def getAllCurrentRelationships(student: StudentMember): Seq[StudentRelationship]
	def getAllPastAndPresentRelationships(student: StudentMember): Seq[StudentRelationship]
	def getCurrentRelationships(relationshipType: StudentRelationshipType, scd: StudentCourseDetails): Seq[StudentRelationship]
	def getCurrentRelationships(relationshipType: StudentRelationshipType, student: StudentMember): Seq[StudentRelationship]
	def getCurrentRelationship(relationshipType: StudentRelationshipType, student: StudentMember, agent: Member): Option[StudentRelationship]
	def getCurrentRelationships(student: StudentMember, agentId: String): Seq[StudentRelationship]
	def getRelationshipsByTarget(relationshipType: StudentRelationshipType, student: StudentMember): Seq[StudentRelationship]
	def getRelationshipsByDepartment(relationshipType: StudentRelationshipType, department: Department): Seq[StudentRelationship]
	def getRelationshipsByStaffDepartment(relationshipType: StudentRelationshipType, department: Department): Seq[StudentRelationship]
	def getAllRelationshipsByAgent(agentId: String): Seq[MemberStudentRelationship]
	def getAllRelationshipTypesByStudent(student: StudentMember): Seq[StudentRelationshipType]
	def getAllRelationshipTypesByAgent(agentId: String): Seq[StudentRelationshipType]
	def getRelationshipsByAgent(relationshipType: StudentRelationshipType, agentId: String): Seq[MemberStudentRelationship]
	def getStudentsWithoutRelationshipByDepartment(relationshipType: StudentRelationshipType, department: Department): Seq[StudentMember]
	def getStudentsByRelationshipAndDepartment(relationshipType: StudentRelationshipType, department: Department): Seq[StudentMember]
	def countStudentsByRelationship(relationshipType: StudentRelationshipType): Number
	def getAllPastAndPresentRelationships(relationshipType: StudentRelationshipType, scd: StudentCourseDetails): Seq[StudentRelationship]
	def isAgent(usercode:String): Boolean
	def getStudentAssociationData(restrictions: Iterable[ScalaRestriction]): Seq[StudentAssociationData]
	def getStudentAssociationEntityData(
		department: Department,
		relationshipType: StudentRelationshipType,
		studentData: Seq[StudentAssociationData],
		additionalEntityIds: Seq[String]
	): Seq[StudentAssociationEntityData]
	def listCurrentRelationshipsWithAgent(relationshipType: StudentRelationshipType, agentId: String): Seq[StudentRelationship]
	def coursesForStudentCourseDetails(scds: Seq[StudentCourseDetails]): Map[StudentCourseDetails, Course]
	def latestYearsOfStudyForStudentCourseDetails(scds: Seq[StudentCourseDetails]): Map[StudentCourseDetails, Int]
}

@Repository
class RelationshipDaoImpl extends RelationshipDao with Daoisms with Logging {
	import Order._
	import Projections._
	import Restrictions._

	def allStudentRelationshipTypes: Seq[StudentRelationshipType] =
		session.newCriteria[StudentRelationshipType]
			.addOrder(Order.asc("sortOrder"))
			.addOrder(Order.asc("id"))
			.seq

	def getStudentRelationshipTypeById(id: String) = getById[StudentRelationshipType](id)

	def getStudentRelationshipTypeByUrlPart(urlPart: String) =
		session.newCriteria[StudentRelationshipType]
			.add(is("urlPart", urlPart))
			.uniqueResult

	def saveOrUpdate(relationshipType: StudentRelationshipType) = session.saveOrUpdate(relationshipType)
	def delete(relationshipType: StudentRelationshipType) = session.delete(relationshipType)

	def getStudentRelationshipById(id: String) = getById[StudentRelationship](id)

	def saveOrUpdate(rel: StudentRelationship) = session.saveOrUpdate(rel)

	def getAllCurrentRelationships(student: StudentMember): Seq[StudentRelationship] = {
		session.newCriteria[StudentRelationship]
			.createAlias("studentCourseDetails", "scd")
			.add(is("scd.student", student))
			.add( Restrictions.or(
			Restrictions.isNull("endDate"),
			Restrictions.ge("endDate", new DateTime())
		))
			.seq
	}

	def getAllPastAndPresentRelationships(student: StudentMember): Seq[StudentRelationship] = {
		session.newCriteria[StudentRelationship]
			.createAlias("studentCourseDetails", "scd")
			.add(is("scd.student", student))
			.seq
	}

	def getAllPastAndPresentRelationships(relationshipType: StudentRelationshipType, scd: StudentCourseDetails): Seq[StudentRelationship] = {
		session.newCriteria[StudentRelationship]
			.add(is("studentCourseDetails", scd))
			.add(is("relationshipType", relationshipType))
			.seq
	}

	def getCurrentRelationships(relationshipType: StudentRelationshipType, student: StudentMember): Seq[StudentRelationship] = {
		session.newCriteria[StudentRelationship]
			.createAlias("studentCourseDetails", "scd")
			.add(is("scd.student", student))
			.add(is("relationshipType", relationshipType))
			.add( Restrictions.or(
			Restrictions.isNull("endDate"),
			Restrictions.ge("endDate", new DateTime())
		))
			.seq
	}

	private def currentRelationsipBaseCriteria(student: StudentMember, agentId: String) = {
		session.newCriteria[MemberStudentRelationship]
			.createAlias("studentCourseDetails", "scd")
			.add(is("scd.student", student))
			.add(is("_agentMember.universityId", agentId))
			.add( Restrictions.or(
			Restrictions.isNull("endDate"),
			Restrictions.ge("endDate", new DateTime())
		))
	}

	def getCurrentRelationship(relationshipType: StudentRelationshipType, student: StudentMember, agent: Member): Option[StudentRelationship] = {
		currentRelationsipBaseCriteria(student, agent.universityId)
			.add(is("relationshipType", relationshipType))
			.uniqueResult
	}

	def getCurrentRelationships(student: StudentMember, agentId: String): Seq[StudentRelationship] = {
		currentRelationsipBaseCriteria(student, agentId).seq
	}

	def getCurrentRelationships(relationshipType: StudentRelationshipType, scd: StudentCourseDetails): Seq[StudentRelationship] = {
		session.newCriteria[StudentRelationship]
			.add(is("studentCourseDetails", scd))
			.add(is("relationshipType", relationshipType))
			.add( Restrictions.or(
			Restrictions.isNull("endDate"),
			Restrictions.ge("endDate", new DateTime())
		))
			.seq
	}

	def getRelationshipsByTarget(relationshipType: StudentRelationshipType, student: StudentMember): Seq[StudentRelationship] = {
		session.newCriteria[StudentRelationship]
			.createAlias("studentCourseDetails", "scd")
			.add(is("scd.student", student))
			.add(is("relationshipType", relationshipType))
			.seq
	}

	def getRelationshipsByDepartment(relationshipType: StudentRelationshipType, department: Department): Seq[StudentRelationship] = {
		// This eagerly fetches a few associations for the view-students-by-tutor page in Attendance Monitoring
		// (/attendance/view/[dept]/agents/[reltype]) - TAB-1868

		session.newCriteria[StudentRelationship]
			.createAlias("studentCourseDetails", "studentCourseDetails")
			.createAlias("studentCourseDetails.student", "student")
			.add(is("relationshipType", relationshipType))
			.add(is("studentCourseDetails.department", department))
			.add(is("studentCourseDetails.mostSignificant", true))
			.add(not(like("studentCourseDetails.statusOnRoute.code", "P%")))
			.add(or(isNull("endDate"), ge("endDate", DateTime.now)))
			.addOrder(asc("_agentMember"))
			.addOrder(asc("studentCourseDetails"))
			.setFetchMode("studentCourseDetails.student", FetchMode.JOIN)
			.setFetchMode("studentCourseDetails.route", FetchMode.JOIN)
			.setFetchMode("studentCourseDetails.latestYearDetails", FetchMode.JOIN)
			.setFetchMode("studentCourseDetails.latestYearDetails.enrolmentStatus", FetchMode.JOIN)
			.setFetchMode("_agentMember", FetchMode.JOIN)
			.seq
	}

	def getRelationshipsByStaffDepartment(relationshipType: StudentRelationshipType, department: Department): Seq[StudentRelationship] = {
		session.newQuery[StudentRelationship]("""
			select
				distinct sr
			from
				StudentRelationship sr,
				StudentCourseDetails scd,
				Member staff
			where
				sr.studentCourseDetails = scd
      and
        staff = sr._agentMember
			and
				sr.relationshipType = :relationshipType
			and
				staff.homeDepartment = :department
			and
				scd.statusOnRoute.code not like 'P%'
			and
				(sr.endDate is null or sr.endDate >= SYSDATE)
			order by
				sr._agentMember, sr.studentCourseDetails
																					""")
			.setEntity("department", department)
			.setEntity("relationshipType", relationshipType)
			.seq
	}


	def getAllRelationshipsByAgent(agentId: String): Seq[MemberStudentRelationship] =
		session.newCriteria[MemberStudentRelationship]
			.add(is("_agentMember.universityId", agentId))
			.add( Restrictions.or(
			Restrictions.isNull("endDate"),
			Restrictions.ge("endDate", new DateTime())
		))
			.seq

	def getAllRelationshipTypesByStudent(student: StudentMember): Seq[StudentRelationshipType] =
		session.newCriteria[StudentRelationship]
			.createAlias("studentCourseDetails", "scd")
			.add(is("scd.student", student))
			.add( Restrictions.or(
			Restrictions.isNull("endDate"),
			Restrictions.ge("endDate", new DateTime())
		))
			.project[StudentRelationshipType](distinct(property("relationshipType")))
			.seq


	def getAllRelationshipTypesByAgent(agentId: String): Seq[StudentRelationshipType] =
		session.newCriteria[MemberStudentRelationship]
			.add(is("_agentMember.universityId", agentId))
			.add( Restrictions.or(
			Restrictions.isNull("endDate"),
			Restrictions.ge("endDate", new DateTime())
		))
			.project[StudentRelationshipType](distinct(property("relationshipType")))
			.seq

	def getRelationshipsByAgent(relationshipType: StudentRelationshipType, agentId: String): Seq[MemberStudentRelationship] =
		session.newCriteria[MemberStudentRelationship]
			.add(is("_agentMember.universityId", agentId))
			.add(is("relationshipType", relationshipType))
			.add( Restrictions.or(
			Restrictions.isNull("endDate"),
			Restrictions.ge("endDate", new DateTime())
		))
			.seq

	def getStudentsWithoutRelationshipByDepartment(relationshipType: StudentRelationshipType, department: Department): Seq[StudentMember] =
		if (relationshipType == null) Seq()
		else session.newQuery[StudentMember]("""
			select
				sm
			from
				StudentMember sm
				inner join sm.studentCourseDetails as scd
			where
				scd.department = :department
			and
				scd.mostSignificant = true
			and
				scd.statusOnRoute.code not like 'P%'
			and
				scd not in (
					select
						sr.studentCourseDetails
					from
						StudentRelationship sr
					where
						sr.relationshipType = :relationshipType
					and
						(sr.endDate is null or sr.endDate >= SYSDATE)
				)
																				 """)
			.setEntity("department", department)
			.setEntity("relationshipType", relationshipType)
			.seq.distinct

	/**
	 * n.b. this will only return students with a direct relationship to a department. For sub-department memberships,
	 * see ProfileService/RelationshipService
	 */
	def getStudentsByRelationshipAndDepartment(relationshipType: StudentRelationshipType, department: Department): Seq[StudentMember]=
		if (relationshipType == null) Nil
		else session.newQuery[StudentMember]("""
			select student
			from
				StudentCourseDetails scd
			where
				scd.department = :department
			and
				scd.mostSignificant = true
			and
				scd.statusOnRoute.code not like 'P%'
			and
				scd in (select sr.studentCourseDetails from StudentRelationship sr where sr.relationshipType = :relationshipType)
																				 """)
			.setEntity("department", department)
			.setEntity("relationshipType", relationshipType)
			.seq.distinct

	def countStudentsByRelationship(relationshipType: StudentRelationshipType): Number =
		if (relationshipType == null) 0
		else session.newQuery[Number]("""
			select
				count(distinct student)
			from
				StudentCourseDetails scd
			where
				scd in (select sr.studentCourseDetails from StudentRelationship sr where sr.relationshipType = :relationshipType)
																	""")
			.setEntity("relationshipType", relationshipType)
			.uniqueResult.getOrElse(0)

	def isAgent(universityId:String) : Boolean =
		session.newCriteria[MemberStudentRelationship]
			.add(is("_agentMember.universityId", universityId))
			.add( Restrictions.or(
			Restrictions.isNull("endDate"),
			Restrictions.ge("endDate", new DateTime())
		)).project[Number](rowCount()).uniqueResult.get.intValue() > 0

	def getStudentAssociationData(restrictions: Iterable[ScalaRestriction]): Seq[StudentAssociationData] = {
		val criteria = session.newCriteria[StudentMember]
		restrictions.foreach { _.apply(criteria) }
		criteria.createAlias("mostSignificantCourse", "mostSignificantCourse")
			.createAlias("mostSignificantCourse.course", "course")
			.createAlias("mostSignificantCourse.route", "route", JoinType.LEFT_OUTER_JOIN)
			.createAlias("mostSignificantCourse.studentCourseYearDetails", "scyd", JoinType.LEFT_OUTER_JOIN)
			.addOrder(Order.asc("lastName"))
			.addOrder(Order.asc("firstName"))
			.project[Array[java.lang.Object]](Projections.projectionList()
				.add(groupProperty("universityId"))
				.add(groupProperty("firstName"))
				.add(groupProperty("lastName"))
				.add(groupProperty("course.code"))
				.add(groupProperty("route.code"))
				.add(max("scyd.yearOfStudy"))
			)
			.seq
			.map(r => StudentAssociationData(
				r(0).asInstanceOf[String],
				Option(r(1)).map(_.asInstanceOf[String]).getOrElse(""),
				Option(r(2)).map(_.asInstanceOf[String]).getOrElse(""),
				CourseType.fromCourseCode(r(3).asInstanceOf[String]),
				Option(r(4)).map(_.asInstanceOf[String]).getOrElse(""),
				Option(r(5)).map(_.asInstanceOf[Int]).getOrElse(0)
			))
	}

	def getStudentAssociationEntityData(
		department: Department,
		relationshipType: StudentRelationshipType,
		studentData: Seq[StudentAssociationData],
		additionalEntityIds: Seq[String]
	): Seq[StudentAssociationEntityData] = {

		case class StudentAssociationEntityDataSingle(
			entityId: String,
			displayName: String,
			sortName: String,
			isHomeDepartment: Option[Boolean],
			capacity: Option[Int],
			student: StudentAssociationData
		)

		val memberAssociations = safeInSeq[Array[java.lang.Object]](
			session.newCriteria[MemberStudentRelationship]
				.createAlias("_agentMember", "member")
				.createAlias("studentCourseDetails", "course")
				.add(Restrictions.and(
					Restrictions.eq("relationshipType", relationshipType),
					Restrictions.or(
						Restrictions.isNull("endDate"),
						Restrictions.gt("endDate", DateTime.now)
					)
				)),
			Projections.projectionList()
				.add(property("member.universityId"))
				.add(property("member.firstName"))
				.add(property("member.lastName"))
				.add(property("member.homeDepartment.id"))
				.add(property("course.student.universityId")),
			"course.student.universityId",
			studentData.map(_.universityId)
		).map(r => StudentAssociationEntityDataSingle(
			r(0).asInstanceOf[String],
			Seq(r(1).asInstanceOf[String], r(2).asInstanceOf[String]).mkString(" "),
			Seq(r(2).asInstanceOf[String], r(1).asInstanceOf[String]).mkString(" "),
			Option(r(3)).map(_.asInstanceOf[String] == department.id),
			None,
			studentData.find(_.universityId == r(4).asInstanceOf[String]).get
		))

		val externalAssociations: Seq[StudentAssociationEntityDataSingle] = safeInSeq[Array[java.lang.Object]](
			session.newCriteria[ExternalStudentRelationship]
				.createAlias("studentCourseDetails", "course")
				.add(Restrictions.and(
					Restrictions.eq("relationshipType", relationshipType),
					Restrictions.or(
						Restrictions.isNull("endDate"),
						Restrictions.gt("endDate", DateTime.now)
					)
				)),
			Projections.projectionList()
				.add(property("_agentName"))
				.add(property("course.student.universityId")),
			"course.student.universityId",
			studentData.map(_.universityId)
		).map(r => StudentAssociationEntityDataSingle(
			r(0).asInstanceOf[String],
			r(0).asInstanceOf[String],
			r(0).asInstanceOf[String],
			None,
			None,
			studentData.find(_.universityId == r(4).asInstanceOf[String]).get
		))

		val dbEntities = (memberAssociations ++ externalAssociations).groupBy(_.entityId).values.map(entityList => StudentAssociationEntityData(
			entityList.head.entityId,
			entityList.head.displayName,
			entityList.head.sortName,
			entityList.head.isHomeDepartment,
			entityList.head.capacity,
			entityList.map(_.student)
		)).toSeq

		val additionalEntities = if (additionalEntityIds.isEmpty) {
			Seq()
		} else {
			safeInSeq[Array[java.lang.Object]](
				session.newCriteria[Member],
				Projections.projectionList()
					.add(property("universityId"))
					.add(property("firstName"))
					.add(property("lastName"))
					.add(property("homeDepartment.id")),
				"universityId",
				additionalEntityIds
			).map(r => StudentAssociationEntityData(
				r(0).asInstanceOf[String],
				Seq(r(1).asInstanceOf[String], r(2).asInstanceOf[String]).mkString(" "),
				Seq(r(2).asInstanceOf[String], r(1).asInstanceOf[String]).mkString(" "),
				Option(r(3)).map(_.asInstanceOf[String] == department.id),
				None,
				Nil
			))
		}

		(dbEntities ++ additionalEntities).sortBy(_.sortName)
	}

	def listCurrentRelationshipsWithAgent(relationshipType: StudentRelationshipType, agentId: String): Seq[StudentRelationship] = {
		val memberRelationships = session.newCriteria[MemberStudentRelationship]
			.createAlias("studentCourseDetails", "studentCourseDetails")
			.createAlias("studentCourseDetails.student", "student")
			.setFetchMode("studentCourseDetails.student", FetchMode.JOIN)
			.add(is("_agentMember.universityId", agentId))
			.add(is("relationshipType", relationshipType))
			.add(Restrictions.or(
				Restrictions.isNull("endDate"),
				Restrictions.ge("endDate", new DateTime())
			)).seq

		val externalRelationships = session.newCriteria[ExternalStudentRelationship]
			.createAlias("studentCourseDetails", "studentCourseDetails")
			.createAlias("studentCourseDetails.student", "student")
			.setFetchMode("studentCourseDetails.student", FetchMode.JOIN)
			.add(is("_agentName", agentId))
			.add(is("relationshipType", relationshipType))
			.add(Restrictions.or(
				Restrictions.isNull("endDate"),
				Restrictions.ge("endDate", new DateTime())
			)).seq

		memberRelationships ++ externalRelationships
	}

	def coursesForStudentCourseDetails(scds: Seq[StudentCourseDetails]): Map[StudentCourseDetails, Course] = {
		if (scds.isEmpty) {
			Map()
		} else {
			val scjCodes = scds.map(_.scjCode).grouped(Daoisms.MaxInClauseCount).zipWithIndex.toSeq
			val queryString =
			"""
				select studentCourseDetails.scjCode, course
				from StudentCourseDetails studentCourseDetails, Course course
				where studentCourseDetails.course = course
				and (
			""" + scjCodes.map{
				case (ids, index) => "studentCourseDetails.scjCode in (:scjCodes" + index.toString + ") "
			}.mkString(" or ")	+ ")"
			val query = session.newQuery[Array[java.lang.Object]](queryString)
			scjCodes.foreach {
				case (ids, index) =>
					query.setParameterList("scjCodes" + index.toString, ids)
			}
			query.seq.distinct.map{ objArray =>
				scds.find(_.scjCode == objArray(0).asInstanceOf[String]).get -> objArray(1).asInstanceOf[Course]
			}.toMap
		}
	}

	def latestYearsOfStudyForStudentCourseDetails(scds: Seq[StudentCourseDetails]): Map[StudentCourseDetails, Int] = {
		if (scds.isEmpty) {
			Map()
		} else {
			val scjCodes = scds.map(_.scjCode).grouped(Daoisms.MaxInClauseCount).zipWithIndex.toSeq
			val queryString =
				"""
				select studentCourseDetails.scjCode, studentCourseYearDetails.yearOfStudy
				from StudentCourseDetails studentCourseDetails, StudentCourseYearDetails studentCourseYearDetails
				where studentCourseDetails.latestStudentCourseYearDetails = studentCourseYearDetails
				and (
				""" + scjCodes.map{
					case (ids, index) => "studentCourseDetails.scjCode in (:scjCodes" + index.toString + ") "
				}.mkString(" or ")	+ ")"
			val query = session.newQuery[Array[java.lang.Object]](queryString)
			scjCodes.foreach {
				case (ids, index) =>
					query.setParameterList("scjCodes" + index.toString, ids)
			}
			query.seq.distinct.map{ objArray =>
				scds.find(_.scjCode == objArray(0).asInstanceOf[String]).get -> objArray(1).asInstanceOf[Int]
			}.toMap
		}
	}
}