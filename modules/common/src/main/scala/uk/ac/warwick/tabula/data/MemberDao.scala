package uk.ac.warwick.tabula.data

import scala.collection.JavaConversions._
import org.hibernate.annotations.AccessType
import org.hibernate.annotations.FilterDefs
import org.hibernate.annotations.Filters
import org.hibernate.criterion._
import org.joda.time.DateTime
import org.springframework.stereotype.Repository
import javax.persistence.Entity
import uk.ac.warwick.tabula.JavaImports.JList
import uk.ac.warwick.tabula.data.model._
import uk.ac.warwick.tabula.helpers.Logging
import scala.collection.JavaConverters._

trait MemberDao {
	def allStudentRelationshipTypes: Seq[StudentRelationshipType]
	def getStudentRelationshipTypeById(id: String): Option[StudentRelationshipType]
	def getStudentRelationshipTypeByUrlPart(urlPart: String): Option[StudentRelationshipType]
	def saveOrUpdate(relationshipType: StudentRelationshipType)
	def delete(relationshipType: StudentRelationshipType)
	
	def saveOrUpdate(member: Member)
	def delete(member: Member)
	def saveOrUpdate(rel: StudentRelationship)
	def getByUniversityId(universityId: String): Option[Member]
	def getAllWithUniversityIds(universityIds: Seq[String]): Seq[Member]
	def getAllByUserId(userId: String, disableFilter: Boolean = false): Seq[Member]
	def getByUserId(userId: String, disableFilter: Boolean = false): Option[Member]
	def listUpdatedSince(startDate: DateTime, max: Int): Seq[Member]
	def listUpdatedSince(startDate: DateTime, department: Department, max: Int): Seq[Member]
	def getRegisteredModules(universityId: String): Seq[Module]
	def getCurrentRelationships(relationshipType: StudentRelationshipType, targetSprCode: String): Seq[StudentRelationship]
	def getRelationshipsByTarget(relationshipType: StudentRelationshipType, targetSprCode: String): Seq[StudentRelationship]
	def getRelationshipsByDepartment(relationshipType: StudentRelationshipType, department: Department): Seq[StudentRelationship]
	def getAllRelationshipsByAgent(agentId: String): Seq[StudentRelationship]
	def getRelationshipsByAgent(relationshipType: StudentRelationshipType, agentId: String): Seq[StudentRelationship]
	def getStudentsWithoutRelationshipByDepartment(relationshipType: StudentRelationshipType, department: Department): Seq[Member]
	def countStudentsByDepartment(department: Department): Number
	def countStudentsByRelationshipAndDepartment(relationshipType: StudentRelationshipType, department: Department): Number
	def countStudentsByRelationship(relationshipType: StudentRelationshipType): Number
}

@Repository
class MemberDaoImpl extends MemberDao with Daoisms with Logging {
	import Restrictions._
	import Order._

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
	
	def saveOrUpdate(member: Member) = member match {
		case ignore: RuntimeMember => // shouldn't ever get here, but making sure
		case _ => session.saveOrUpdate(member)
	}

	def delete(member: Member) = member match {
		case ignore: RuntimeMember => // shouldn't ever get here, but making sure
		case _ => {
			session.delete(member)
			// Immediately flush delete
			session.flush()
		}
	}

	def saveOrUpdate(rel: StudentRelationship) = session.saveOrUpdate(rel)

	def getByUniversityId(universityId: String) =
		session.newCriteria[Member]
			.add(is("universityId", universityId.trim))
			.uniqueResult

	def getAllWithUniversityIds(universityIds: Seq[String]) =
		if (universityIds.isEmpty) Seq.empty
		else session.newCriteria[Member]
			.add(in("universityId", universityIds map { _.trim }))
			.seq

	def getAllByUserId(userId: String, disableFilter: Boolean = false) = {
		val filterEnabled = Option(session.getEnabledFilter(Member.StudentsOnlyFilter)).isDefined
		try {
			if (disableFilter)
				session.disableFilter(Member.StudentsOnlyFilter)

			session.newCriteria[Member]
					.add(is("userId", userId.trim.toLowerCase))
					.add(disjunction()
						.add(is("inUseFlag", "Active"))
						.add(like("inUseFlag", "Inactive - Starts %"))
					)
					.addOrder(asc("universityId"))
					.seq
		} finally {
			if (disableFilter && filterEnabled)
				session.enableFilter(Member.StudentsOnlyFilter)
		}
	}

	def getByUserId(userId: String, disableFilter: Boolean = false) = getAllByUserId(userId, disableFilter).headOption

	def listUpdatedSince(startDate: DateTime, department: Department, max: Int) =
		session.newCriteria[Member]
				.add(gt("lastUpdatedDate", startDate))
				.add(is("homeDepartment", department))
				.setMaxResults(max)
				.addOrder(asc("lastUpdatedDate"))
				.list

	def listUpdatedSince(startDate: DateTime, max: Int) =
		session.newCriteria[Member].add(gt("lastUpdatedDate", startDate)).setMaxResults(max).addOrder(asc("lastUpdatedDate")).list

	def getRegisteredModules(universityId: String): Seq[Module] =
		session.newQuery[Module]("""
				 select distinct m from Module m where code in
				(select distinct substring(lower(uag.moduleCode),1,5)
					from UpstreamAssessmentGroup uag
				  where :universityId in elements(uag.members.staticIncludeUsers))
				""")
					.setString("universityId", universityId)
					.seq

	def getCurrentRelationships(relationshipType: StudentRelationshipType, targetSprCode: String): Seq[StudentRelationship] = {
			session.newCriteria[StudentRelationship]
					.add(is("targetSprCode", targetSprCode))
					.add(is("relationshipType", relationshipType))
					.add( Restrictions.or(
							Restrictions.isNull("endDate"),
							Restrictions.ge("endDate", new DateTime())
							))
					.seq
	}

	def getRelationshipsByTarget(relationshipType: StudentRelationshipType, targetSprCode: String): Seq[StudentRelationship] = {
			session.newCriteria[StudentRelationship]
					.add(is("targetSprCode", targetSprCode))
					.add(is("relationshipType", relationshipType))
					.seq
	}

	def getRelationshipsByDepartment(relationshipType: StudentRelationshipType, department: Department): Seq[StudentRelationship] = {
		// order by agent to separate any named (external) from numeric (member) agents
		// then by student properties
		session.newQuery[StudentRelationship]("""
			select
				distinct sr
			from
				StudentRelationship sr,
				StudentCourseDetails scd
			where
				sr.targetSprCode = scd.sprCode
			and
				sr.relationshipType = :relationshipType
			and
				scd.department = :department
			and
				scd.sprStatus.code not like 'P%'
			and
				(sr.endDate is null or sr.endDate >= SYSDATE)
			order by
				sr.agent, sr.targetSprCode
		""")
			.setEntity("department", department)
			.setEntity("relationshipType", relationshipType)
			.seq
	}

	def getAllRelationshipsByAgent(agentId: String): Seq[StudentRelationship] =
		session.newCriteria[StudentRelationship]
			.add(is("agent", agentId))
			.add( Restrictions.or(
				Restrictions.isNull("endDate"),
				Restrictions.ge("endDate", new DateTime())
			))
			.seq

	def getRelationshipsByAgent(relationshipType: StudentRelationshipType, agentId: String): Seq[StudentRelationship] =
		session.newCriteria[StudentRelationship]
			.add(is("agent", agentId))
			.add(is("relationshipType", relationshipType))
			.add( Restrictions.or(
				Restrictions.isNull("endDate"),
				Restrictions.ge("endDate", new DateTime())
			))
			.seq

	def getStudentsWithoutRelationshipByDepartment(relationshipType: StudentRelationshipType, department: Department): Seq[Member] =
		if (relationshipType == null) Seq()
		else session.newQuery[Member]("""
			select
				distinct sm
			from
				StudentMember sm
				inner join sm.studentCourseDetails as scd
			where
				sm.homeDepartment = :department
			and
				scd.sprStatus.code not like 'P%'
			and
				scd.sprCode not in (
					select 
						sr.targetSprCode 
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
			.seq

	def countStudentsByDepartment(department: Department): Number =
		if (department == null) 0
		else session.newQuery[Number]("""
			select
				count(distinct student)
			from
				StudentCourseDetails scd
			where
				scd.department = :department
			and
				scd.sprStatus.code not like 'P%'
			""")
			.setEntity("department", department)
			.uniqueResult.getOrElse(0)

	def countStudentsByRelationshipAndDepartment(relationshipType: StudentRelationshipType, department: Department): Number =
		if (relationshipType == null) 0
		else session.newQuery[Number]("""
			select
				count(distinct student)
			from
				StudentCourseDetails scd
			where
				scd.department = :department
			and
				scd.sprStatus.code not like 'P%'
			and
				scd.sprCode in (select sr.targetSprCode from StudentRelationship sr where sr.relationshipType = :relationshipType)
			""")
			.setEntity("department", department)
			.setEntity("relationshipType", relationshipType)
			.uniqueResult.getOrElse(0)

	def countStudentsByRelationship(relationshipType: StudentRelationshipType): Number =
		if (relationshipType == null) 0
		else session.newQuery[Number]("""
			select
				count(distinct student)
			from
				StudentCourseDetails scd
			where
				scd.sprCode in (select sr.targetSprCode from StudentRelationship sr where sr.relationshipType = :relationshipType)
			""")
			.setEntity("relationshipType", relationshipType)
			.uniqueResult.getOrElse(0)

}

