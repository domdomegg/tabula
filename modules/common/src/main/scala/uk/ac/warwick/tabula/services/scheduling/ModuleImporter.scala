package uk.ac.warwick.tabula.services.scheduling

import java.sql.{ResultSet, Types}
import javax.sql.DataSource

import org.springframework.context.annotation.Profile
import org.springframework.jdbc.`object`.{MappingSqlQuery, MappingSqlQueryWithParameters}
import org.springframework.jdbc.core.SqlParameter
import org.springframework.stereotype.Service
import uk.ac.warwick.spring.Wire
import uk.ac.warwick.tabula.JavaImports._
import uk.ac.warwick.tabula.data.model.DegreeType
import uk.ac.warwick.tabula.helpers.Logging
import uk.ac.warwick.tabula.helpers.StringUtils._
import uk.ac.warwick.tabula.sandbox.SandboxData

import scala.collection.JavaConverters._

case class DepartmentInfo(val name: String, val code: String, val faculty: String, val parentCode:Option[String] = None, val filterName:Option[String] = None)
case class ModuleInfo(val name: String, val code: String, val group: String, val degreeType: DegreeType)
case class ModuleTeachingDepartmentInfo(val code: String, val departmentCode: String, val percentage: JBigDecimal)
case class RouteInfo(val name: String, val code: String, val degreeType: DegreeType)
case class RouteTeachingDepartmentInfo(val code: String, val departmentCode: String, val percentage: JBigDecimal)

/**
 * Retrieves department and module information from an external location.
 */
trait ModuleImporter {
	def getModules(deptCode: String): Seq[ModuleInfo]
	def getModuleTeachingDepartments(moduleCode: String): Seq[ModuleTeachingDepartmentInfo]
	def getRoutes(deptCode: String): Seq[RouteInfo]
	def getRouteTeachingDepartments(routeCode: String): Seq[RouteTeachingDepartmentInfo]
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
	lazy val moduleTeachingDepartmentMappingQuery = new ModuleTeachingDepartmentInfoMappingQuery(sits)
	lazy val routeInfoMappingQuery = new RouteInfoMappingQuery(sits)
	lazy val routeTeachingDepartmentMappingQuery = new RouteTeachingDepartmentInfoMappingQuery(sits)

	def getDepartments(): Seq[DepartmentInfo] = departmentInfoMappingQuery.execute.asScala

	def getModules(deptCode: String): Seq[ModuleInfo] = moduleInfoMappingQuery.executeByNamedParam(JMap(
		"department_code" -> deptCode.toUpperCase
	)).asScala

	def getModuleTeachingDepartments(moduleCode: String): Seq[ModuleTeachingDepartmentInfo] = moduleTeachingDepartmentMappingQuery.executeByNamedParam(JMap(
		"module_code" -> moduleCode.toUpperCase
	)).asScala

	def getRoutes(deptCode: String): Seq[RouteInfo] = routeInfoMappingQuery.execute(deptCode.toUpperCase).asScala

	def getRouteTeachingDepartments(routeCode: String): Seq[RouteTeachingDepartmentInfo] = routeTeachingDepartmentMappingQuery.executeByNamedParam(JMap(
		"route_code" -> routeCode.toUpperCase
	)).asScala
}

@Profile(Array("sandbox")) @Service
class SandboxModuleImporter extends ModuleImporter {

	def getDepartments: Seq[DepartmentInfo] =
		SandboxData.Departments.toSeq map { case (code, d) => DepartmentInfo(d.name, d.code, d.facultyCode) }

	def getModules(deptCode: String): Seq[ModuleInfo] =
		SandboxData.Departments.get(deptCode).map(_.modules.toSeq.map{ case (code, m) => ModuleInfo(m.name, m.code, deptCode + "-" + m.code, DegreeType.fromCode("UG")) }).getOrElse(Seq())

	def getModuleTeachingDepartments(moduleCode: String): Seq[ModuleTeachingDepartmentInfo] =
		SandboxData.Departments.values
			.find { department =>
				department.modules.keys.toSeq.contains(moduleCode)
			}
			.toSeq
			.flatMap { department =>
				department.modules
					.find { case (code, _) => code == moduleCode }
					.toSeq
					.map { case (code, m) => ModuleTeachingDepartmentInfo(code, department.code, JBigDecimal(Some(100))) }
			}

	def getRoutes(deptCode: String): Seq[RouteInfo] =
		SandboxData.Departments.get(deptCode).map(_.routes.toSeq map { case (code, r) => RouteInfo(r.name, r.code, r.degreeType) }).getOrElse(Seq())

	def getRouteTeachingDepartments(routeCode: String): Seq[RouteTeachingDepartmentInfo] =
		SandboxData.Departments.values
			.find { department =>
				department.routes.keys.toSeq.contains(routeCode)
			}
			.toSeq
			.flatMap { department =>
				department.routes
					.find { case (code, _) => code == routeCode }
					.toSeq
					.map { case (code, r) => RouteTeachingDepartmentInfo(code, department.code, JBigDecimal(Some(100))) }
			}

}

object ModuleImporter {
	var sitsSchema: String = Wire.property("${schema.sits}")

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
		select substr(mod.mod_code,0,5) as code, max(mod.mod_name) as name, max(mod.sch_code) as scheme_code
		  from ins_mod mod
		    left outer join cam_top top on mod.mod_code = top.mod_code
		  where
		    mod.mod_code like '_____-%' and
		    (
		      (top.dpt_code is not null and top.top_perc = 100 and top.dpt_code = :department_code) or
		      (top.sub_code is not null and top.top_perc <> 100 and substr(top.sub_code, 0, length(mod.dpt_code)) = :department_code) or
		      (top.dpt_code is null and mod.dpt_code = :department_code)
		    ) and
		    mod.mod_iuse = 'Y' and
		    mod.mot_code not in ('S-', 'D')
		  group by substr(mod.mod_code,0,5)
		"""

	final def GetModuleTeachingDepartmentsSql =	f"""
		select substr(top.top_code, 0, 5) as code, top.dpt_code as department_code, min(top.top_perc) as percentage
			from $sitsSchema.cam_top top
				join $sitsSchema.ins_mod mod
					on mod.mod_code = top.mod_code and
						 mod.mod_iuse = 'Y' and
						 mod.mot_code not in ('S-', 'D')

			where
				substr(top.top_code, 0, 5) = :module_code and
				top.top_iuse = 'Y'
			group by
				substr(top.top_code, 0, 5), top.dpt_code
		"""

	final def GetRoutesSql = """
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

	final val GetRouteTeachingDepartmentsSql = """
		select
			psd.psd_pwyc as code,
			psd.psd_dptc as department_code,
			psd.psd_perc as percentage
		from ins_psd psd
		where
			psd.psd_pwyc = :route_code
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
			val degreeType: DegreeType = DegreeType.getFromSchemeCode(rs.getString("scheme_code"))
			ModuleInfo(
				rs.getString("name").safeTrim,
				moduleCode,
				deptCode + "-" + moduleCode,
				degreeType)
		}
	}

	class ModuleTeachingDepartmentInfoMappingQuery(ds: DataSource) extends MappingSqlQueryWithParameters[ModuleTeachingDepartmentInfo](ds, GetModuleTeachingDepartmentsSql) {
		declareParameter(new SqlParameter("module_code", Types.VARCHAR))
		compile()
		override def mapRow(rs: ResultSet, rowNumber: Int, params: Array[java.lang.Object], context: JMap[_, _]) = {
			ModuleTeachingDepartmentInfo(
				rs.getString("code").toLowerCase.safeTrim,
				rs.getString("department_code").toLowerCase.safeTrim,
				rs.getBigDecimal("percentage")
			)
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

	class RouteTeachingDepartmentInfoMappingQuery(ds: DataSource) extends MappingSqlQueryWithParameters[RouteTeachingDepartmentInfo](ds, GetRouteTeachingDepartmentsSql) {
		declareParameter(new SqlParameter("route_code", Types.VARCHAR))
		compile()
		override def mapRow(rs: ResultSet, rowNumber: Int, params: Array[java.lang.Object], context: JMap[_, _]) = {
			RouteTeachingDepartmentInfo(
				rs.getString("code").toLowerCase.safeTrim,
				rs.getString("department_code").toLowerCase.safeTrim,
				rs.getBigDecimal("percentage")
			)
		}
	}

}

trait ModuleImporterComponent {
	def moduleImporter: ModuleImporter
}

trait AutowiringModuleImporterComponent extends ModuleImporterComponent {
	var moduleImporter = Wire[ModuleImporter]
}
