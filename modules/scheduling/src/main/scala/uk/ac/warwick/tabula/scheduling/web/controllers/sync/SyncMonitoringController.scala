package uk.ac.warwick.tabula.scheduling.web.controllers.sync

import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.stereotype.Controller
import uk.ac.warwick.tabula.web.controllers.BaseController
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import uk.ac.warwick.tabula.scheduling.commands.SyncReplicaFilesystemCommand
import uk.ac.warwick.spring.Wire
import java.io.File
import org.springframework.util.FileCopyUtils
import java.io.FileReader
import org.joda.time.DateTime
import uk.ac.warwick.tabula.scheduling.commands.CleanupUnreferencedFilesCommand
import uk.ac.warwick.tabula.scheduling.commands.SanityCheckFilesystemCommand

@Controller
@RequestMapping(Array("/sync/nagios"))
class SyncMonitoringController extends BaseController {
	import SyncReplicaFilesystemCommand._
	import CleanupUnreferencedFilesCommand._
	import SanityCheckFilesystemCommand._
	import SyncMonitoringController._

	var dataDir = Wire[String]("${base.data.dir}")

	lazy val lastSyncJobDetailsFile = new File(new File(dataDir), LastSyncJobDetailsFilename)

	@RequestMapping(Array("/status"))
	def lastrun(implicit request: HttpServletRequest, response: HttpServletResponse): Unit = {
		val lastSyncJobDetails = FileCopyUtils.copyToString(new FileReader(lastSyncJobDetailsFile)).trim
		val lastRun = lastSyncJobDetails.substring(lastSyncJobDetails.indexOf(LastRunDelimiter) + LastRunDelimiter.length())
		val minutesSinceLastRun = (new DateTime().getMillis - lastRun.toLong) / MillisInAMinute

		val allDetails = lastSyncJobDetails.concat(",minutesSinceLastRun," + minutesSinceLastRun)

		response.addHeader("Content-Type", "text/plain")
		response.addHeader("Content-Length", allDetails.length.toString)
		response.getWriter().write(allDetails)
	}

	lazy val lastSanityCheckJobDetailsFile = new File(new File(dataDir), LastSanityCheckJobDetailsFilename)

	@RequestMapping(Array("/sanity"))
	def lastSanityCheck(implicit request: HttpServletRequest, response: HttpServletResponse): Unit = {
		val lastJobDetails = FileCopyUtils.copyToString(new FileReader(lastSanityCheckJobDetailsFile)).trim
		val lastRun = lastJobDetails.substring(lastJobDetails.indexOf(CheckLastRunDelimiter) + CheckLastRunDelimiter.length())
		val minutesSinceLastRun = (new DateTime().getMillis - lastRun.toLong) / MillisInAMinute

		val allDetails = lastJobDetails.concat(",minutesSinceLastRun," + minutesSinceLastRun)

		response.addHeader("Content-Type", "text/plain")
		response.addHeader("Content-Length", allDetails.length.toString)
		response.getWriter().write(allDetails)
	}

	lazy val lastCleanupJobDetailsFile = new File(new File(dataDir), LastCleanupJobDetailsFilename)

	@RequestMapping(Array("/cleanup"))
	def lastCleanup(implicit request: HttpServletRequest, response: HttpServletResponse): Unit = {
		val lastJobDetails = FileCopyUtils.copyToString(new FileReader(lastCleanupJobDetailsFile)).trim
		val lastRun = lastJobDetails.substring(lastJobDetails.indexOf(CheckLastRunDelimiter) + CheckLastRunDelimiter.length())
		val minutesSinceLastRun = (new DateTime().getMillis - lastRun.toLong) / MillisInAMinute

		val allDetails = lastJobDetails.concat(",minutesSinceLastRun," + minutesSinceLastRun)

		response.addHeader("Content-Type", "text/plain")
		response.addHeader("Content-Length", allDetails.length.toString)
		response.getWriter().write(allDetails)
	}

}

object SyncMonitoringController {
	val LastRunDelimiter = "lastRun,"
	val CheckLastRunDelimiter = "lastSuccessfulRun,"
	val MillisInASecond = 1000
	val MillisInAMinute = MillisInASecond * 60
}