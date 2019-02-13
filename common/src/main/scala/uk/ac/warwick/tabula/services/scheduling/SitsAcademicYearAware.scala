package uk.ac.warwick.tabula.services.scheduling

import java.sql.ResultSet

import javax.sql.DataSource
import org.springframework.context.annotation.Profile
import org.springframework.core.env.Environment
import org.springframework.jdbc.`object`.MappingSqlQuery
import org.springframework.stereotype.Service
import uk.ac.warwick.spring.Wire
import uk.ac.warwick.tabula.AcademicYear

import scala.collection.JavaConverters._

trait SitsAcademicYearAware {
	var sitsAcademicYearService: SitsAcademicYearService = Wire[SitsAcademicYearService]

	def getCurrentSitsAcademicYearString: String = sitsAcademicYearService.getCurrentSitsAcademicYearString

	def getCurrentSitsAcademicYear: AcademicYear = {
		AcademicYear.parse(getCurrentSitsAcademicYearString)
	}
}

trait SitsAcademicYearService {
	def getCurrentSitsAcademicYearString: String
}

@Profile(Array("dev", "test", "production"))
@Service
class SitsAcademicYearServiceImpl extends SitsAcademicYearService {
	val env: Environment = Wire[Environment]
	var sits: DataSource = Wire[DataSource]("sitsDataSource")

	val GetCurrentAcademicYear = """
		select GET_AYR() ayr from dual
		"""

	def getCurrentSitsAcademicYearString: String =
		Option(new GetCurrentAcademicYearQuery(sits).execute().asScala.head)
  		.getOrElse {
				if (env.acceptsProfiles("dev")) // Fall back for when SITS clone is being refreshed
					AcademicYear.now().toString()
				else
					throw new IllegalArgumentException("No current SITS academic year was available")
			}

	class GetCurrentAcademicYearQuery(ds: DataSource) extends MappingSqlQuery[String](ds, GetCurrentAcademicYear) {
		compile()
		override def mapRow(rs: ResultSet, rowNumber: Int): String = rs.getString("ayr")
	}
}

@Profile(Array("sandbox"))
@Service
class SandboxSitsAcademicYearService extends SitsAcademicYearService {

	def getCurrentSitsAcademicYearString: String =
		AcademicYear.now().toString()
}