package uk.ac.warwick.tabula.system.exceptions

import scala.collection.JavaConversions._
import scala.collection.JavaConverters._
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Required
import org.springframework.web.servlet.HandlerExceptionResolver
import org.springframework.web.servlet.ModelAndView
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import javax.servlet.ServletException
import uk.ac.warwick.tabula.JavaImports._
import uk.ac.warwick.tabula.helpers.Logging
import uk.ac.warwick.tabula.helpers.Ordered
import uk.ac.warwick.tabula.web.Mav
import uk.ac.warwick.tabula.web.controllers.ControllerViews
import uk.ac.warwick.tabula.RequestInfo
import uk.ac.warwick.util.core.ExceptionUtils
import org.springframework.beans.TypeMismatchException
import uk.ac.warwick.tabula.ItemNotFoundException
import uk.ac.warwick.tabula.system.{CurrentUserInterceptor, RequestInfoInterceptor}
import uk.ac.warwick.tabula.PermissionsError
import org.springframework.web.multipart.MultipartException
import org.apache.http.HttpStatus
import org.springframework.web.bind.MissingServletRequestParameterException
import uk.ac.warwick.tabula.ParameterMissingException
import org.springframework.web.HttpRequestMethodNotSupportedException
import org.springframework.web.servlet.mvc.condition.{ConsumesRequestCondition, ProducesRequestCondition}
import org.springframework.http.MediaType
import uk.ac.warwick.tabula.web.views.JSONView

/**
 * Implements the Spring HandlerExceptionResolver SPI to catch all errors.
 *
 * Errors not caught by Spring will be forwarded by the web.xml error handler to
 * ErrorController which delegates to ExceptionResolver.doResolve(e), so all errors
 * should come here eventually.
 */
class ExceptionResolver extends HandlerExceptionResolver with Logging with Ordered with ControllerViews {

	@Required var defaultView: String = _

	@Autowired var exceptionHandler: ExceptionHandler = _

	@Autowired var userInterceptor: CurrentUserInterceptor = _
	@Autowired var infoInterceptor: RequestInfoInterceptor = _

	/**
	 * If the interesting exception matches one of these exceptions then
	 * the given view name will be used instead of defaultView.
	 *
	 * Doesn't check subclasses, the exception class has to match exactly.
	 */
	@Required var viewMappings: JMap[String, String] = Map[String, String]()

	override def resolveException(request: HttpServletRequest, response: HttpServletResponse, obj: Any, e: Exception): ModelAndView = {
		val interceptors = List(userInterceptor, infoInterceptor)
		for (interceptor <- interceptors) interceptor.preHandle(request, response, obj)

		doResolve(e, Some(request), Some(response)).noLayoutIf(ajax).toModelAndView
	}

	override def requestInfo = RequestInfo.fromThread

	private def ajax = requestInfo.map { _.ajax }.getOrElse(false)

	/**
	 * Resolve an exception outside of a request. Doesn't return a model/view.
	 */
	def resolveException(e: Exception) { doResolve(e) }

	/**
	 * Simpler interface for ErrorController to delegate to, which is called when an exception
	 * happens beyond Spring's grasp.
	 */
	def doResolve(e: Throwable, request: Option[HttpServletRequest] = None, response: Option[HttpServletResponse] = None): Mav = {
		def loggedIn = requestInfo.map { _.user.loggedIn }.getOrElse(false)
		def isAjaxRequest = request.isDefined && ("XMLHttpRequest" == request.get.getHeader("X-Requested-With"))

		e match {
			// Handle unresolvable @PathVariables as a page not found (404). HFC-408
			case typeMismatch: TypeMismatchException => handle(new ItemNotFoundException(typeMismatch), request, response)

			// Handle request method not supported as a 404
			case methodNotSupported: HttpRequestMethodNotSupportedException => handle(new ItemNotFoundException(methodNotSupported), request, response)

			// Handle missing servlet param exceptions as 400
			case missingParam: MissingServletRequestParameterException => handle(new ParameterMissingException(missingParam), request, response)

			// TAB-411 also redirect to signin for submit permission denied if not logged in (and not ajax request)
			case permDenied: PermissionsError if (!loggedIn && !isAjaxRequest) => RedirectToSignin()

			// TAB-567 wrap MultipartException in UserError so it doesn't get logged as an error
			case uploadError: MultipartException => handle(new FileUploadException(uploadError), request, response)

			case exception: Throwable => handle(exception, request, response)
			case _ => handleNull
		}
	}

	/**
	 * Catch any exception in the given callback. Useful for wrapping some
	 * work that's done outside of a request, such as a scheduled task, because
	 * otherwise the exception will be only minimally logged by the scheduler.
	 */
	def reportExceptions[A](fn: => A) =
		try fn
		catch { case throwable: Throwable => handle(throwable, None, None); throw throwable }

	private def handle(exception: Throwable, request: Option[HttpServletRequest], response: Option[HttpServletResponse]) = {
		val token = ExceptionTokens.newToken

		val interestingException = ExceptionUtils.getInterestingThrowable(exception, Array(classOf[ServletException]))

		val mav = Mav(defaultView,
			"originalException" -> exception,
			"exception" -> interestingException,
			"token" -> token,
			"stackTrace" -> ExceptionHandler.renderStackTrace(interestingException))

		// handler will do logging, emailing
		try {
			exceptionHandler.exception(ExceptionContext(token, interestingException, request))
		} catch {
			// This is very bad and should never happen - but still try to avoid showing
			// a plain exception to the user.
			case e: Exception => logger.error("Exception handling exception!", e)
		}

		viewMappings.get(interestingException.getClass.getName) match {
			case view: String => { mav.viewName = view }
			case null => //keep defaultView
		}

		val statusCode = interestingException match {
			case error: UserError => error.statusCode
			case _ => HttpStatus.SC_INTERNAL_SERVER_ERROR
		}

		response.foreach { _.setStatus(statusCode) }

		request.foreach { request =>
			def convertToJSON() {
				mav.viewName == null
				mav.view = new JSONView(Map(
					"success" -> false,
					"status" -> statusCode,
					"errors" -> Array(interestingException.getMessage)
				))
			}

			if (new ConsumesRequestCondition("application/json").getMatchingCondition(request) != null) convertToJSON()
			else new ProducesRequestCondition("text/html", "application/json", "text/json").getMatchingCondition(request) match {
				case null => // None matching
				case condition if condition.getExpressions.exists(_.getMediaType == MediaType.TEXT_HTML) => // Want HTML
				case _ => convertToJSON()
			}
		}

		mav
	}

	private def handleNull = {
		logger.error("Unexpectedly tried to resolve a null exception!")
		Mav(defaultView)
	}

}
