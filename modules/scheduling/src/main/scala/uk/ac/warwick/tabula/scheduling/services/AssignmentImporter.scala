package uk.ac.warwick.tabula.scheduling.services

import java.sql.ResultSet
import java.sql.Types
import javax.sql.DataSource
import javax.annotation.Resource
import scala.collection.JavaConverters._
import org.joda.time.DateTime
import org.springframework.beans.factory.InitializingBean
import org.springframework.stereotype.Service
import org.springframework.jdbc.core.RowCallbackHandler
import org.springframework.jdbc.core.SqlParameter
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.jdbc.`object`.MappingSqlQuery
import org.springframework.jdbc.`object`.MappingSqlQueryWithParameters
import uk.ac.warwick.tabula.JavaImports._
import uk.ac.warwick.tabula.data.model.{AssessmentType, UpstreamAssessmentGroup, AssessmentComponent}
import uk.ac.warwick.tabula.AcademicYear
import org.springframework.context.annotation.Profile
import uk.ac.warwick.tabula.sandbox.SandboxData

trait AssignmentImporter {
	/**
	 * Iterates through ALL module registration elements,
	 * passing each ModuleRegistration item to the given callback for it to process.
	 */
	def allMembers(callback: UpstreamModuleRegistration => Unit): Unit
	
	def getAllAssessmentGroups: Seq[UpstreamAssessmentGroup]

	/**
	 * The UpstreamAssessmentGroups that don't have any module registrations
	 * against them.
	 *
	 * Also includes fake "NONE" groups for the cases where everybody has been
	 * allocated an assessment group (TAB-2416 remove stale old NONE groups)
	 */
	def getEmptyAssessmentGroups: Seq[UpstreamAssessmentGroup]
	
	def getAllAssessmentComponents: Seq[AssessmentComponent]
}

@Profile(Array("dev", "test", "production")) @Service
class AssignmentImporterImpl extends AssignmentImporter with InitializingBean {
	import AssignmentImporter._

	@Resource(name = "academicDataStore") var ads: DataSource = _
	var upstreamAssessmentGroupQuery: UpstreamAssessmentGroupQuery = _
	var assessmentComponentQuery: AssessmentComponentQuery = _
	var emptyAssessmentGroupsQuery: EmptyUpstreamAssessmentGroupQuery = _
	var jdbc: NamedParameterJdbcTemplate = _

	override def afterPropertiesSet() {
		upstreamAssessmentGroupQuery = new UpstreamAssessmentGroupQuery(ads)
		assessmentComponentQuery = new AssessmentComponentQuery(ads)
		emptyAssessmentGroupsQuery = new EmptyUpstreamAssessmentGroupQuery(ads)
		jdbc = new NamedParameterJdbcTemplate(ads)
	}

	def getAllAssessmentComponents: Seq[AssessmentComponent] = assessmentComponentQuery.executeByNamedParam(JMap(
		"academic_year_code" -> yearsToImportArray)).asScala

	private def yearsToImportArray = yearsToImport.map(_.toString).asJava: JList[String]

	// This will be quite a few thousand records, but not more than
	// 20k. Shouldn't cause any memory problems, so no point complicating
	// it by trying to stream or batch the data.
	def getAllAssessmentGroups: Seq[UpstreamAssessmentGroup] = upstreamAssessmentGroupQuery.executeByNamedParam(JMap(
		"academic_year_code" -> yearsToImportArray)).asScala

	/**
	 * Iterates through ALL module registration elements in ADS (that's many),
	 * passing each ModuleRegistration item to the given callback for it to process.
	 */
	def allMembers(callback: UpstreamModuleRegistration => Unit) {
		val params: JMap[String, Object] = JMap(
			"academic_year_code" -> yearsToImportArray)
		jdbc.query(AssignmentImporter.GetAllAssessmentGroupMembers, params, new RowCallbackHandler {
			override def processRow(rs: ResultSet) {
				callback(UpstreamModuleRegistration(
					year = rs.getString("academic_year_code"),
					sprCode = rs.getString("spr_code"),
					occurrence = rs.getString("mav_occurrence"),
					moduleCode = rs.getString("module_code"),
					assessmentGroup = convertAssessmentGroupFromSITS(rs.getString("assessment_group"))))
			}
		})
	}

	def getEmptyAssessmentGroups: Seq[UpstreamAssessmentGroup] =
		emptyAssessmentGroupsQuery.executeByNamedParam(JMap("academic_year_code" -> yearsToImportArray)).asScala


	/** Convert incoming null assessment groups into the NONE value */
	private def convertAssessmentGroupFromSITS(string: String) =
		if (string == null) AssessmentComponent.NoneAssessmentGroup
		else string

	private def yearsToImport = AcademicYear.guessByDate(DateTime.now).yearsSurrounding(0, 1)
}

@Profile(Array("sandbox")) @Service
class SandboxAssignmentImporter extends AssignmentImporter {
	
	def allMembers(callback: UpstreamModuleRegistration => Unit) = {
		var moduleCodesToIds = Map[String, Seq[Range]]()
		 
		for {
			(code, d) <- SandboxData.Departments
			route <- d.routes.values.toSeq
			moduleCode <- route.moduleCodes
		} {
			val range = route.studentsStartId to route.studentsEndId
			
			moduleCodesToIds = moduleCodesToIds + (
				moduleCode -> (moduleCodesToIds.getOrElse(moduleCode, Seq()) :+ range)
			)
		}

		for {
			(moduleCode, ranges) <- moduleCodesToIds
			range <- ranges
			uniId <- range
		} callback(
			UpstreamModuleRegistration(
				year = AcademicYear.guessByDate(DateTime.now).toString,
				sprCode = "%d/1".format(uniId),
				occurrence = "A",
				moduleCode = "%s-15".format(moduleCode.toUpperCase),
				assessmentGroup = "A"
			)
		)

	}
	
	def getAllAssessmentGroups: Seq[UpstreamAssessmentGroup] =
		for {
			(code, d) <- SandboxData.Departments.toSeq
			route <- d.routes.values.toSeq
			moduleCode <- route.moduleCodes
		} yield {
			val ag = new UpstreamAssessmentGroup()
			ag.moduleCode = "%s-15".format(moduleCode.toUpperCase)
			ag.academicYear = AcademicYear.guessByDate(DateTime.now)
			ag.assessmentGroup = "A"
			ag.occurrence = "A"
			ag
		}
	
	def getAllAssessmentComponents: Seq[AssessmentComponent] =
		for {
			(code, d) <- SandboxData.Departments.toSeq
			route <- d.routes.values.toSeq
			moduleCode <- route.moduleCodes
		} yield {
			val a = new AssessmentComponent
			a.moduleCode = "%s-15".format(moduleCode.toUpperCase)
			a.sequence = "A01"
			a.name = "Coursework"
			a.assessmentGroup = "A"
			a.departmentCode = d.code.toUpperCase
			a.assessmentType = AssessmentType.Assignment
			a
		}

	def getEmptyAssessmentGroups: Seq[UpstreamAssessmentGroup] = Nil
	
}

/**
 * Holds data about an individual student's registration on a single module.
 */
case class UpstreamModuleRegistration(year: String, sprCode: String, occurrence: String, moduleCode: String, assessmentGroup: String) {
	def differentGroup(other: UpstreamModuleRegistration) =
		year != other.year ||
			occurrence != other.occurrence ||
			moduleCode != other.moduleCode ||
			assessmentGroup != other.assessmentGroup

	/**
	 * Returns an UpstreamAssessmentGroup matching the group attributes.
	 */
	def toUpstreamAssignmentGroup = {
		val g = new UpstreamAssessmentGroup
		g.academicYear = AcademicYear.parse(year)
		g.moduleCode = moduleCode
		g.assessmentGroup = assessmentGroup
		// for the NONE group, override occurrence to also be NONE, because we create a single UpstreamAssessmentGroup
		// for each module with group=NONE and occurrence=NONE, and all unallocated students go in there together.
		g.occurrence =
			if (assessmentGroup == AssessmentComponent.NoneAssessmentGroup)
				AssessmentComponent.NoneAssessmentGroup
			else
				occurrence
		g
	}
}

object AssignmentImporter {

	/** Get AssessmentComponents, and also some fake ones for linking to
		* the group of students with no selected assessment group.
		*/
	val GetAssessmentsQuery = s"""
		select distinct
		mr.module_code,
		'${AssessmentComponent.NoneAssessmentGroup}' as seq,
		'Students not registered for assessment' as name,
		'${AssessmentComponent.NoneAssessmentGroup}' as assessment_group,
		m.department_code,
		'X' as assessment_code
		from module_registration mr
		join module m on m.module_code = mr.module_code
		where academic_year_code in (:academic_year_code) and mr.assessment_group is null
	union
		select mad.module_code, seq, mad.name, mad.assessment_group, m.department_code, assessment_code
		from module_assessment_details mad
		join module m on (m.module_code = mad.module_code and m.in_use = 'Y')
		where m.department_code is not null"""
	// Department code should be set for any modules since 10/11

	val GetAllAssessmentGroups = s"""
		select distinct
			mav.academic_year_code,
			mav.module_code,
			'${AssessmentComponent.NoneAssessmentGroup}' as mav_occurrence,
			'${AssessmentComponent.NoneAssessmentGroup}' as assessment_group
		from module_availability mav
		join module_assessment_details mad on mad.module_code = mav.module_code
		join module m on (m.module_code = mad.module_code and m.in_use = 'Y')
		where academic_year_code in (:academic_year_code)
	union
		select distinct mav.academic_year_code, mav.module_code, mav_occurrence, mad.assessment_group
		from module_availability mav
		join module_assessment_details mad on mad.module_code = mav.module_code
		join module m on (m.module_code = mad.module_code and m.in_use = 'Y')
		where academic_year_code in (:academic_year_code)"""

	val GetAllAssessmentGroupMembers = """
		select 
			mr.academic_year_code,
			mr.spr_code,
			mr.mav_occurrence,
			mr.module_code,
			mr.assessment_group
		from module_registration mr
    	left outer join student_current_study_details scd
    		on mr.spr_code = scd.spr_code
		where
			mr.academic_year_code in (:academic_year_code) and
      (
				scd.student_status is null or
        scd.student_status not like 'P%'
      )
		order by mr.academic_year_code, mr.module_code, mr.mav_occurrence, mr.assessment_group
																		 """

	/** AssessmentGroups with no registrations, and virtual NONE groups that should be empty */
	val GetEmptyAssessmentGroups = s"""
		(select distinct
		  mav.module_code,
		  mav.academic_year_code,
		  mav.mav_occurrence,
		  mad.assessment_group
		  from module_availability mav
		  join module_assessment_details mad
		    on mad.module_code = mav.module_code
		  join module m
		    on mav.module_code = m.module_code
		    and m.in_use = 'Y'
		  left join module_registration mr
		    on mav.module_code = mr.module_code
		    and mav.academic_year_code = mr.academic_year_code
		    and mav.mav_occurrence = mr.mav_occurrence
		where mav.academic_year_code in (:academic_year_code)
		  and mr.module_code is null
		  order by mav.module_code)
		union
		(select distinct
			  mav.module_code,
			  mav.academic_year_code,
			  '${AssessmentComponent.NoneAssessmentGroup}' as mav_occurrence,
			  '${AssessmentComponent.NoneAssessmentGroup}' as assessment_group
			  from module_availability mav
			  join module_assessment_details mad
			    on mad.module_code = mav.module_code
			  join module m
			    on mav.module_code = m.module_code
			    and m.in_use = 'Y'
			  left join module_registration mr
			    on mav.module_code = mr.module_code
			    and mav.academic_year_code = mr.academic_year_code
			    and mav.mav_occurrence = mr.mav_occurrence
			where mav.academic_year_code in (:academic_year_code)
			  and mad.assessment_group is not null
			  and mr.module_code is not null
			  order by mav.module_code)"""

	class AssessmentComponentQuery(ds: DataSource) extends MappingSqlQuery[AssessmentComponent](ds, GetAssessmentsQuery) {
		declareParameter(new SqlParameter("academic_year_code", Types.VARCHAR))
		compile()
		override def mapRow(rs: ResultSet, rowNumber: Int) = {
			val a = new AssessmentComponent
			a.moduleCode = rs.getString("module_code")
			a.sequence = rs.getString("seq")
			a.name = rs.getString("name")
			a.assessmentGroup = rs.getString("assessment_group")
			a.departmentCode = rs.getString("department_code")
			a.assessmentType = AssessmentType(rs.getString("assessment_code"))
			a
		}
	}

	class UpstreamAssessmentGroupQuery(ds: DataSource) extends MappingSqlQueryWithParameters[UpstreamAssessmentGroup](ds, GetAllAssessmentGroups) {
		declareParameter(new SqlParameter("academic_year_code", Types.VARCHAR))
		this.compile()
		override def mapRow(rs: ResultSet, rowNumber: Int, params: Array[java.lang.Object], context: JMap[_, _]) =
			mapRowToAssessmentGroup(rs)
	}

	class EmptyUpstreamAssessmentGroupQuery(ds: DataSource) extends MappingSqlQueryWithParameters[UpstreamAssessmentGroup](ds, GetEmptyAssessmentGroups) {
		declareParameter(new SqlParameter("academic_year_code", Types.VARCHAR))
		this.compile()
		override def mapRow(rs: ResultSet, rowNumber: Int, params: Array[java.lang.Object], context: JMap[_, _]) =
			mapRowToAssessmentGroup(rs)
	}

	def mapRowToAssessmentGroup(rs: ResultSet) = {
		val ag = new UpstreamAssessmentGroup()
		ag.moduleCode = rs.getString("module_code")
		ag.academicYear = AcademicYear.parse(rs.getString("academic_year_code"))
		ag.assessmentGroup = rs.getString("assessment_group")
		ag.occurrence = rs.getString("mav_occurrence")
		ag
	}

}