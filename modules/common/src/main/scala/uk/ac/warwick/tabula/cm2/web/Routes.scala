package uk.ac.warwick.tabula.cm2.web

import uk.ac.warwick.spring.Wire
import uk.ac.warwick.tabula.data.model._
import uk.ac.warwick.tabula.web.RoutesUtils

/**
 * Generates URLs to various locations, to reduce the number of places where URLs
 * are hardcoded and repeated.
 *
 * For methods called "apply", you can leave out the "apply" and treat the object like a function.
 */
object Routes {
	import RoutesUtils._

	// FIXME this isn't really an optional property, but testing is a pain unless it's made so
	var _cm2Prefix = Wire.optionProperty("${cm2.prefix}")
	def cm2Prefix = _cm2Prefix.orNull

	private lazy val context = s"/$cm2Prefix"
	def home = context + "/"

	object assignment {
		def apply(assignment: Assignment) = context + "/submission/%s/" format encoded(assignment.id)
	}

}