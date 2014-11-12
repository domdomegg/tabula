package uk.ac.warwick.tabula.scheduling.services

import java.sql.ResultSet
import java.sql.Types
import javax.sql.DataSource
import uk.ac.warwick.spring.Wire

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
	
	def getAllAssessmentComponents: Seq[AssessmentComponent]
}

@Profile(Array("dev", "test", "production")) @Service
class AssignmentImporterImpl extends AssignmentImporter with InitializingBean {
	import AssignmentImporter._

	var sits = Wire[DataSource]("sitsDataSource")

	var upstreamAssessmentGroupQuery: UpstreamAssessmentGroupQuery = _
	var assessmentComponentQuery: AssessmentComponentQuery = _
	var jdbc: NamedParameterJdbcTemplate = _

	override def afterPropertiesSet() {
		assessmentComponentQuery = new AssessmentComponentQuery(sits)
		upstreamAssessmentGroupQuery = new UpstreamAssessmentGroupQuery(sits)
		jdbc = new NamedParameterJdbcTemplate(sits)
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

	/** Convert incoming null assessment groups into the NONE value */
	private def convertAssessmentGroupFromSITS(string: String) =
		if (string == null) AssessmentComponent.NoneAssessmentGroup
		else string

	private def yearsToImport = AcademicYear.guessSITSAcademicYearByDate(DateTime.now).yearsSurrounding(0, 1)
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
				year = AcademicYear.guessSITSAcademicYearByDate(DateTime.now).toString,
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
			ag.academicYear = AcademicYear.guessSITSAcademicYearByDate(DateTime.now)
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
			a.assessmentType = AssessmentType.Assignment
			a
		}
	
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
	var sitsSchema: String = Wire.property("${schema.sits}")

	/** Get AssessmentComponents, and also some fake ones for linking to
		* the group of students with no selected assessment group.
		*/
	lazy val GetAssessmentsQuery = s"""
		select distinct
			sms.mod_code as module_code,
			'${AssessmentComponent.NoneAssessmentGroup}' as seq,
			'Students not registered for assessment' as name,
			'${AssessmentComponent.NoneAssessmentGroup}' as assessment_group,
			'X' as assessment_code
			from $sitsSchema.cam_sms sms
				join $sitsSchema.cam_ssn ssn
					on sms.spr_code = ssn.ssn_sprc and ssn.ssn_ayrc = sms.ayr_code and ssn.ssn_mrgs != 'CON'
			where
				sms.sms_agrp is null and
				sms.ayr_code in (:academic_year_code)
	union all
		select distinct
			smo.mod_code as module_code,
			'${AssessmentComponent.NoneAssessmentGroup}' as seq,
			'Students not registered for assessment' as name,
			'${AssessmentComponent.NoneAssessmentGroup}' as assessment_group,
			'X' as assessment_code
			from $sitsSchema.cam_smo smo
				left outer join $sitsSchema.cam_ssn ssn
					on smo.spr_code = ssn.ssn_sprc and ssn.ssn_ayrc = smo.ayr_code
			where
				(smo.smo_rtsc is null or (smo.smo_rtsc not like 'X%' and smo.smo_rtsc != 'Z')) and
				ssn.ssn_sprc is null and
				smo.smo_agrp is null and
				smo.ayr_code in (:academic_year_code)
	union all
		select mab.map_code as module_code, mab.mab_seq as seq, mab.mab_name as name, mab.mab_agrp as assessment_group, mab.ast_code as assessment_code
			from $sitsSchema.cam_mab mab
				join $sitsSchema.cam_mav mav
					on mab.map_code = mav.mod_code and
						 mav.psl_code = 'Y' and
						 mav.ayr_code in (:academic_year_code)
				join $sitsSchema.ins_mod mod
					on mav.mod_code = mod.mod_code
			where	mod.mod_iuse = 'Y' and
						mod.mot_code not in ('S-', 'D')"""

	lazy val GetAllAssessmentGroups = s"""
		select distinct
			mav.ayr_code as academic_year_code,
			mav.mod_code as module_code,
			'${AssessmentComponent.NoneAssessmentGroup}' as mav_occurrence,
			'${AssessmentComponent.NoneAssessmentGroup}' as assessment_group
			from $sitsSchema.cam_mab mab
				join $sitsSchema.cam_mav mav
					on mab.map_code = mav.mod_code
				join $sitsSchema.ins_mod mod
					on mav.mod_code = mod.mod_code
			where mod.mod_iuse = 'Y' and
						mod.mot_code not in ('S-', 'D') and
						mav.psl_code = 'Y' and
						mav.ayr_code in (:academic_year_code)
	union all
		select distinct
			mav.ayr_code as academic_year_code,
			mav.mod_code as module_code,
			mav.mav_occur as mav_occurrence,
			mab.mab_agrp as assessment_group
			from $sitsSchema.cam_mab mab
				join $sitsSchema.cam_mav mav
					on mab.map_code = mav.mod_code
				join $sitsSchema.ins_mod mod
					on mav.mod_code = mod.mod_code
			where mod.mod_iuse = 'Y' and
						mod.mot_code not in ('S-', 'D') and
						mav.psl_code = 'Y' and
						mav.ayr_code in (:academic_year_code)"""

	lazy val GetUnconfirmedModuleRegistrations = s"""
		select
			sms.ayr_code as academic_year_code,
			spr.spr_code as spr_code,
			sms.sms_occl as mav_occurrence,
			sms.mod_code as module_code,
			sms.sms_agrp as assessment_group
				from $sitsSchema.srs_scj scj
					join $sitsSchema.ins_spr spr
						on scj.scj_sprc = spr.spr_code and (spr.sts_code is null or spr.sts_code not like 'P%') -- no perm withdrawn students

					join $sitsSchema.cam_sms sms
						on sms.spr_code = scj.scj_sprc

					join $sitsSchema.srs_vco vco
						on vco.vco_crsc = scj.scj_crsc and vco.vco_rouc = spr.rou_code

					join $sitsSchema.cam_ssn ssn
						on sms.spr_code = ssn.ssn_sprc and ssn.ssn_ayrc = sms.ayr_code and ssn.ssn_mrgs != 'CON'
			where
				scj.scj_udfa in ('Y','y') and -- most significant courses only
				sms.ayr_code in (:academic_year_code)"""

	lazy val GetConfirmedModuleRegistrations = s"""
		select
			smo.ayr_code as academic_year_code,
			spr.spr_code as spr_code,
			smo.mav_occur as mav_occurrence,
			smo.mod_code as module_code,
			smo.smo_agrp as assessment_group
				from $sitsSchema.srs_scj scj
					join $sitsSchema.ins_spr spr
						on scj.scj_sprc = spr.spr_code and
							(spr.sts_code is null or spr.sts_code not like 'P%') -- no perm withdrawn students

					join $sitsSchema.cam_smo smo
						on smo.spr_code = spr.spr_code and
							(smo.smo_rtsc is null or (smo.smo_rtsc not like 'X%' and smo.smo_rtsc != 'Z')) -- no WMG cancelled

					join $sitsSchema.srs_vco vco
						on vco.vco_crsc = scj.scj_crsc and vco.vco_rouc = spr.rou_code

					join $sitsSchema.cam_ssn ssn
						on smo.spr_code = ssn.ssn_sprc and ssn.ssn_ayrc = smo.ayr_code and ssn.ssn_mrgs = 'CON'
			where
				scj.scj_udfa in ('Y','y') and -- most significant courses only
				smo.ayr_code in (:academic_year_code)"""

	lazy val GetAutoUploadedConfirmedModuleRegistrations = s"""
		select
			smo.ayr_code as academic_year_code,
			spr.spr_code as spr_code,
			smo.mav_occur as mav_occurrence,
			smo.mod_code as module_code,
			smo.smo_agrp as assessment_group
				from $sitsSchema.srs_scj scj
					join $sitsSchema.ins_spr spr
						on scj.scj_sprc = spr.spr_code and
							(spr.sts_code is null or spr.sts_code not like 'P%') -- no perm withdrawn students

					join $sitsSchema.cam_smo smo
						on smo.spr_code = spr.spr_code and
							(smo.smo_rtsc is null or (smo.smo_rtsc not like 'X%' and smo.smo_rtsc != 'Z')) -- no WMG cancelled

					join $sitsSchema.srs_vco vco
						on vco.vco_crsc = scj.scj_crsc and vco.vco_rouc = spr.rou_code

					left outer join $sitsSchema.cam_ssn ssn
						on smo.spr_code = ssn.ssn_sprc and ssn.ssn_ayrc = smo.ayr_code
			where
				scj.scj_udfa in ('Y','y') and -- most significant courses only
				smo.ayr_code in (:academic_year_code) and
				ssn.ssn_sprc is null -- no matching SSN"""

	lazy val GetAllAssessmentGroupMembers = s"""
			$GetUnconfirmedModuleRegistrations
				union all
			$GetConfirmedModuleRegistrations
				union all
			$GetAutoUploadedConfirmedModuleRegistrations
		order by academic_year_code, module_code, mav_occurrence, assessment_group, spr_code"""

	class AssessmentComponentQuery(ds: DataSource) extends MappingSqlQuery[AssessmentComponent](ds, GetAssessmentsQuery) {
		declareParameter(new SqlParameter("academic_year_code", Types.VARCHAR))
		compile()
		override def mapRow(rs: ResultSet, rowNumber: Int) = {
			val a = new AssessmentComponent
			a.moduleCode = rs.getString("module_code")
			a.sequence = rs.getString("seq")
			a.name = rs.getString("name")
			a.assessmentGroup = rs.getString("assessment_group")
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

	def mapRowToAssessmentGroup(rs: ResultSet) = {
		val ag = new UpstreamAssessmentGroup()
		ag.moduleCode = rs.getString("module_code")
		ag.academicYear = AcademicYear.parse(rs.getString("academic_year_code"))
		ag.assessmentGroup = rs.getString("assessment_group")
		ag.occurrence = rs.getString("mav_occurrence")
		ag
	}

}