package uk.ac.warwick.tabula.groups.web

import java.net.URLEncoder
import uk.ac.warwick.userlookup.User
import uk.ac.warwick.tabula.data.model.{Module, Department}
import uk.ac.warwick.tabula.data.model.groups.SmallGroupSet
import uk.ac.warwick.tabula.web.RoutesUtils

/**
 * Generates URLs to various locations, to reduce the number of places where URLs
 * are hardcoded and repeated.
 *
 * For methods called "apply", you can leave out the "apply" and treat the object like a function.
 */
object Routes {
	import RoutesUtils._
	private val context = "/groups"
	def home = context + "/"

	object tutor {
		def mygroups = context + "/tutor"
	}

	// These are relative to the /profiles app, not the /groups app.
	object profile {
		def view(member: User) = context + "/view/%s" format (encoded(member.getWarwickId))
		def mine = context + "/view/me"
	}

	object admin {
		def apply(department: Department) = context + "/admin/department/%s" format (encoded(department.code))

		object module {
			def apply(module: Module) = admin(module.department) + "#module-" + encoded(module.code)
		}

		def allocate(set: SmallGroupSet) = context + "/admin/module/%s/groups/%s/allocate" format (encoded(set.module.code), encoded(set.id))

	}
}
