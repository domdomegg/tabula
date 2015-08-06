package uk.ac.warwick.tabula.web.views

import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import freemarker.template.TemplateModel
import freemarker.template.Template
import freemarker.ext.servlet.FreemarkerServlet
import uk.ac.warwick.sso.client.{SSOConfiguration, SSOClientFilter}
import freemarker.template.SimpleHash
import freemarker.template.Configuration
import javax.servlet.ServletConfig
import uk.ac.warwick.tabula.helpers.Logging
import uk.ac.warwick.tabula.RequestInfo
import freemarker.template.utility.DeepUnwrap
import uk.ac.warwick.tabula.commands.TaskBenchmarking
import java.io.FileNotFoundException

/**
 * FreemarkerServlet which adds some useful model stuff to every request.
 */
class CustomFreemarkerServlet extends FreemarkerServlet() with Logging with TaskBenchmarking {

	var config: Configuration = _

	logger.info("Creating custom freemarker servlet")

	val MISSING_CONFIG_MESSAGE =
		"Couldn't find config in servlet attribute 'freemarkerConfiguration' - " +
		"should have been exported by ServletContextAttributeExporter"

	def templateName(request: HttpServletRequest, response: HttpServletResponse) = {
		val path = requestUrlToTemplatePath(request)

		try {
			Some(config.getTemplate(path, deduceLocale(path, request, response)).getName)
		} catch {
			case e: FileNotFoundException => None
		}
	}

	override def doGet(request: HttpServletRequest, response: HttpServletResponse) =
		benchmarkTask(templateName(request, response).getOrElse("[not found template]")) { super.doGet(request, response) }

	override def doPost(request: HttpServletRequest, response: HttpServletResponse) =
		benchmarkTask(templateName(request, response).getOrElse("[not found template]")) { super.doPost(request, response) }

	/**
	 * Add items to the model that should be available to every Freemarker view.
	 *
	 * Items that don't rely on the current request state should be set in the configuration
	 * as a shared variable.
	 */
	override def preTemplateProcess(req: HttpServletRequest, res: HttpServletResponse, template: Template, data: TemplateModel) = {
		val model = data.asInstanceOf[SimpleHash]
		RequestInfo.fromThread.map { info =>
			model.put("info", info)
			model.put("user", info.user)
		}

		// if we set contentType on the Mav, drag it out and set it on the response
		DeepUnwrap.permissiveUnwrap(model.get("contentType")) match {
			case t: String => res.setContentType(t)
			case _ =>
		}

		model.put("IS_SSO_PROTECTED", Option(SSOConfiguration.getConfig).nonEmpty)

		true
	}

	/* FreemarkerServlet tries to create an object wrapper and set it on the config,
   * but we like the custom one that's already set so return that.
   */
	override def createObjectWrapper = config.getObjectWrapper

	/**
	 * Spring config has a Configuration bean which we then export to the
	 * servlet context. This then grabs that, so that we're using one shared
	 * config (important because we set up a bunch of Scala conversions)
	 */
	override def init {
		config = getServletConfig().getServletContext().getAttribute("freemarkerConfiguration") match {
			case c: Configuration => c
			case _ => throw new IllegalStateException(MISSING_CONFIG_MESSAGE)
		}
		super.init
	}

	// Use the configuration that was wired into the constructor
	override def createConfiguration = config

}