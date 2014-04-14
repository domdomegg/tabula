package uk.ac.warwick.tabula.scheduling.web.controllers.sysadmin

import uk.ac.warwick.tabula.web.controllers._
import org.springframework.stereotype._
import org.springframework.beans.factory.annotation.Autowired
import uk.ac.warwick.tabula.services.jobs.JobService
import uk.ac.warwick.tabula.helpers.DateTimeOrdering._
import org.springframework.web.bind.annotation._
import uk.ac.warwick.tabula.jobs.TestingJob
import uk.ac.warwick.tabula.web.Routes

class JobQuery {
	var page: Int = 0
}

@Controller
@RequestMapping(Array("/sysadmin/jobs"))
class JobController extends BaseController {

	@Autowired var jobService: JobService = _
	
	val pageSize = 100

	@RequestMapping(Array("/list"))
	def list(query: JobQuery) = {
		val unfinished = jobService.unfinishedInstances
		
		val page = query.page
		val start = (page * pageSize) + 1
		val max = pageSize
		val end = start + max - 1
		
		val recent = jobService.listRecent(page * pageSize, pageSize)
		
		Mav("sysadmin/jobs/list",
			"unfinished" -> unfinished,
			"finished" -> recent,
			"fromIndex" -> false,
			"page" -> page,
			"startIndex" -> start,
			"endIndex" -> end)
	}

	@RequestMapping(Array("/create-test"))
	def test = {
		val jobInstance = jobService.add(Some(user), TestingJob("sysadmin test", TestingJob.DefaultDelay))
		val id = jobInstance.id
		testStatus(id)
		Redirect(Routes.sysadmin.jobs.status(jobInstance))
	}

	@RequestMapping(Array("/job-status"))
	def testStatus(@RequestParam("id") id: String) = {
		val instance = jobService.getInstance(id)
		Mav("sysadmin/jobs/job-status",
			"jobId" -> id,
			"jobStatus" -> (instance map (_.status) getOrElse (""))).noLayoutIf(ajax)
	}
	
	@RequestMapping(Array("/kill"))
	def kill(@RequestParam("id") id: String) = {
		val instance = jobService.getInstance(id)
		jobService.kill(instance.get)
		Redirect(Routes.sysadmin.jobs.list)
	}
	
	@RequestMapping(Array("/run"))
	def run(@RequestParam("id") id: String) = {
		val instance = jobService.getInstance(id).get
		val job = jobService.findJob(instance.jobType).get
		
		jobService.processInstance(instance, job)
		Redirect(Routes.sysadmin.jobs.list)
	}

}