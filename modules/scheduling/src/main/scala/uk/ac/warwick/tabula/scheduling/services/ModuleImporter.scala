package uk.ac.warwick.tabula.scheduling.services

import org.springframework.stereotype.Service
import uk.ac.warwick.tabula.helpers.Logging
import uk.ac.warwick.tabula.helpers.StringUtils._
import javax.sql.DataSource
import javax.annotation.Resource
import org.springframework.jdbc.`object`.MappingSqlQuery
import java.sql.ResultSet
import collection.JavaConverters._
import org.springframework.jdbc.core.SqlParameter
import java.sql.Types
import org.springframework.jdbc.`object`.MappingSqlQueryWithParameters
import uk.ac.warwick.tabula.data.model.DegreeType
import javax.annotation.Resource
import org.springframework.stereotype.Service
import uk.ac.warwick.spring.Wire
import uk.ac.warwick.tabula.JavaImports._
import org.springframework.context.annotation.Profile
import uk.ac.warwick.tabula.sandbox.SandboxData

case class DepartmentInfo(val name: String, val code: String, val faculty: String, val parentCode:Option[String] = None, val filterName:Option[String] = None)
case class ModuleInfo(val name: String, val code: String, val group: String)
case class RouteInfo(val name: String, val code: String, val degreeType: DegreeType)

/**
 * Retrieves department and module information from an external location.
 */
trait ModuleImporter {
	def getModules(deptCode: String): Seq[ModuleInfo]
	def getRoutes(deptCode: String): Seq[RouteInfo]
	def getDepartments(): Seq[DepartmentInfo]
}

/**
 * Retrieves department and module information from Webgroups.
 */
@Profile(Array("dev", "test", "production")) @Service
class ModuleImporterImpl extends ModuleImporter with Logging {
	import ModuleImporter._

	var sits = Wire[DataSource]("sitsDataSource")
	var membership = Wire[DataSource]("membershipDataSource")

	lazy val departmentInfoMappingQuery = new DepartmentInfoMappingQuery(membership)
	lazy val moduleInfoMappingQuery = new ModuleInfoMappingQuery(sits)
	lazy val routeInfoMappingQuery = new RouteInfoMappingQuery(sits)

	def getDepartments(): Seq[DepartmentInfo] = departmentInfoMappingQuery.execute.asScala
	
	def getModules(deptCode: String): Seq[ModuleInfo] = moduleInfoMappingQuery.executeByNamedParam(JMap(
		"department_code" -> deptCode.toUpperCase
	)).asScala
	
	def getRoutes(deptCode: String): Seq[RouteInfo] = routeInfoMappingQuery.execute(deptCode.toUpperCase).asScala
}

@Profile(Array("sandbox")) @Service
class SandboxModuleImporter extends ModuleImporter {
	
	def getDepartments: Seq[DepartmentInfo] = 
		SandboxData.Departments.toSeq map { case (code, d) => DepartmentInfo(d.name, d.code, d.facultyCode) }
	
	def getModules(deptCode: String): Seq[ModuleInfo] = 
		SandboxData.Departments(deptCode).modules.toSeq map { case (code, m) => ModuleInfo(m.name, m.code, deptCode + "-" + m.code) }
	
	def getRoutes(deptCode: String): Seq[RouteInfo] = 
		SandboxData.Departments(deptCode).routes.toSeq map { case (code, r) => RouteInfo(r.name, r.code, r.degreeType) }
	
}

object ModuleImporter {

	final val GetDepartmentsSql = """
		select 
			d.department_name name, 
			d.department_code code,
			f.faculty_name faculty
  
		from cmsowner.uow_departments d 
			join cmsowner.uow_faculties f
				on d.faculty_code = f.faculty_code
      
		where d.department_code is not null
		"""
		
	final val GetModulesSql = """
		select substr(mod.mod_code,0,5) as code, max(mod.mod_name) as name
		  from ins_mod mod
		    left outer join cam_top top on mod.mod_code = top.mod_code
		  where
		    mod.mod_code like '_____-%' and
		    (
		      (top.dpt_code is not null and top.top_perc = 100 and top.dpt_code = :department_code) or
		      (top.sub_code is not null and top.top_perc <> 100 and top.sub_code = :department_code) or
		      (top.dpt_code is null and mod.dpt_code = :department_code)
		    ) and 
		    mod.mod_iuse = 'Y' and
		    mod.mot_code not in ('S-', 'D')
		  group by substr(mod.mod_code,0,5)
		"""
		
	final val GetRoutesSql = """
		select 
		  pwy.pwy_code as code,
		  pwy.pwy_name as name,
		  pwy.pwy_pwtc as degree_type
		from ins_pwy pwy
		where
		  pwy.pwy_pwgc = :department_code and
		  pwy.pwy_iuse = 'Y' and
		  pwy.pwy_pwtc in ('UG', 'PG', 'PGCE', 'IS')
		"""

	class DepartmentInfoMappingQuery(ds: DataSource) extends MappingSqlQuery[DepartmentInfo](ds, GetDepartmentsSql) {
		compile()
		override def mapRow(rs: ResultSet, rowNumber: Int) =
			DepartmentInfo(
				rs.getString("name").safeTrim,
				rs.getString("code").toLowerCase,
				rs.getString("faculty").safeTrim)
	}

	class ModuleInfoMappingQuery(ds: DataSource) extends MappingSqlQueryWithParameters[ModuleInfo](ds, GetModulesSql) {
		declareParameter(new SqlParameter("department_code", Types.VARCHAR))
		compile()
		override def mapRow(rs: ResultSet, rowNumber: Int, params: Array[java.lang.Object], context: JMap[_, _]) = {
			val moduleCode = rs.getString("code").toLowerCase.safeTrim
			val deptCode = params(0).toString.toLowerCase
			ModuleInfo(
				rs.getString("name").safeTrim,
				moduleCode,
				deptCode + "-" + moduleCode)
		}
	}
	
	class RouteInfoMappingQuery(ds: DataSource) extends MappingSqlQuery[RouteInfo](ds, GetRoutesSql) {
		declareParameter(new SqlParameter("department_code", Types.VARCHAR))
		compile()
		override def mapRow(rs: ResultSet, rowNumber: Int) = {
			val routeCode = rs.getString("code").toLowerCase.safeTrim
			RouteInfo(
				rs.getString("name").safeTrim,
				routeCode,
				DegreeType.fromCode(rs.getString("degree_type").safeTrim))
		}
	}

}

trait ModuleImporterComponent {
	def moduleImporter: ModuleImporter
}

trait AutowiringModuleImporterComponent extends ModuleImporterComponent {
	var moduleImporter = Wire[ModuleImporter]
}
