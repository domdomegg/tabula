package uk.ac.warwick.tabula.scheduling.web.controllers.sysadmin

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype._
import org.springframework.web.bind.annotation._
import uk.ac.warwick.tabula.ItemNotFoundException
import uk.ac.warwick.tabula.jobs.TestingJob
import uk.ac.warwick.tabula.services.jobs.JobService
import uk.ac.warwick.tabula.web.Routes
import uk.ac.warwick.tabula.web.controllers._
import uk.ac.warwick.tabula.web.views.JSONView

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
		status(id)
		Redirect(Routes.scheduling.jobs.status(jobInstance))
	}

	@RequestMapping(Array("/job-status"))
	def status(@RequestParam("id") id: String) = {
		jobService.getInstance(id).map(instance =>
			if (ajax)
				Mav(new JSONView(Map(
					"status" -> instance.status,
					"progress" -> instance.progress,
					"succeeded" -> instance.succeeded
				))).noLayout()
			else
				Mav("sysadmin/jobs/job-status",
					"jobId" -> id,
					"jobStatus" -> instance.status
				)
		).getOrElse(throw new ItemNotFoundException())
	}

	@RequestMapping(Array("/kill"))
	def kill(@RequestParam("id") id: String) = {
		val instance = jobService.getInstance(id)
		jobService.kill(instance.get)
		Redirect(Routes.scheduling.jobs.list)
	}

	@RequestMapping(Array("/run"))
	def run(@RequestParam("id") id: String) = {
		val instance = jobService.getInstance(id).get
		val job = jobService.findJob(instance.jobType).get

		jobService.processInstance(instance, job)
		Redirect(Routes.scheduling.jobs.list)
	}

}