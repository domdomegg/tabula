package uk.ac.warwick.tabula.scheduling.services

import java.sql.ResultSet
import scala.collection.JavaConversions._
import org.springframework.jdbc.`object`.MappingSqlQuery
import org.springframework.stereotype.Service
import javax.sql.DataSource
import uk.ac.warwick.spring.Wire
import uk.ac.warwick.tabula.data.SitsStatusDao
import uk.ac.warwick.tabula.helpers.Logging
import uk.ac.warwick.tabula.scheduling.commands.imports.ImportSingleSitsStatusCommand
import org.apache.log4j.Logger
import uk.ac.warwick.tabula.data.model.SitsStatus
import uk.ac.warwick.tabula.data.Transactions._
import uk.ac.warwick.tabula.data.model.ModeOfAttendance
import uk.ac.warwick.tabula.data.ModeOfAttendanceDao
import uk.ac.warwick.tabula.scheduling.commands.imports.ImportSingleModeOfAttendanceCommand
import org.springframework.context.annotation.Profile

trait ModeOfAttendanceImporter extends Logging {
	var modeOfAttendanceDao = Wire.auto[ModeOfAttendanceDao]
	
	var modeOfAttendanceMap = slurpModeOfAttendances()
	
	def getModeOfAttendances: Seq[ImportSingleModeOfAttendanceCommand]
	
	def slurpModeOfAttendances(): Map[String, ModeOfAttendance] = {
		transactional(readOnly = true) {
			logger.debug("refreshing SITS mode of attendance map")

			(for (modeOfAttendanceCode <- modeOfAttendanceDao.getAllStatusCodes; status <- modeOfAttendanceDao.getByCode(modeOfAttendanceCode)) yield {
				(modeOfAttendanceCode, status)
			}).toMap
		}
	}
}

@Profile(Array("dev", "test", "production")) @Service
class ModeOfAttendanceImporterImpl extends ModeOfAttendanceImporter {
	import ModeOfAttendanceImporter._
	
	var sits = Wire[DataSource]("sitsDataSource")
	
	lazy val modeOfAttendanceQuery = new ModeOfAttendanceQuery(sits)

	def getModeOfAttendances: Seq[ImportSingleModeOfAttendanceCommand] = {
		val modeOfAttendances = modeOfAttendanceQuery.execute.toSeq
		modeOfAttendanceMap = slurpModeOfAttendances()
		modeOfAttendances
	}
}

@Profile(Array("sandbox")) @Service
class SandboxModeOfAttendanceImporter extends ModeOfAttendanceImporter {
	def getModeOfAttendances: Seq[ImportSingleModeOfAttendanceCommand] =
		Seq(
			new ImportSingleModeOfAttendanceCommand(ModeOfAttendanceInfo("F", "FULL-TIME", "Full-time according to Funding Council definitions")),
			new ImportSingleModeOfAttendanceCommand(ModeOfAttendanceInfo("P", "PART-TIME", "Part-time"))
		)
}

case class ModeOfAttendanceInfo(code: String, shortName: String, fullName: String)

object ModeOfAttendanceImporter {
		
	val GetModeOfAttendance = """
		select moa_code, moa_snam, moa_name from intuit.ins_moa
		"""
	
	class ModeOfAttendanceQuery(ds: DataSource) extends MappingSqlQuery[ImportSingleModeOfAttendanceCommand](ds, GetModeOfAttendance) {
		compile()
		override def mapRow(resultSet: ResultSet, rowNumber: Int) = 
			new ImportSingleModeOfAttendanceCommand(
				ModeOfAttendanceInfo(resultSet.getString("moa_code"), resultSet.getString("moa_snam"), resultSet.getString("moa_name"))
			)
	}
	
}
