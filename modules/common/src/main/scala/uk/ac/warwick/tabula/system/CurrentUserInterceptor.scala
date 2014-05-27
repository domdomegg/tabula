package uk.ac.warwick.tabula.system

import org.springframework.web.servlet.handler.HandlerInterceptorAdapter
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import uk.ac.warwick.spring.Wire
import uk.ac.warwick.sso.client.SSOClientFilter
import uk.ac.warwick.tabula.CurrentUser
import uk.ac.warwick.tabula.NoCurrentUser
import uk.ac.warwick.tabula.helpers.FoundUser
import uk.ac.warwick.tabula.roles.Sysadmin
import uk.ac.warwick.tabula.services.permissions.RoleService
import uk.ac.warwick.tabula.web.Cookies._
import uk.ac.warwick.userlookup.User
import uk.ac.warwick.userlookup.UserLookupInterface
import uk.ac.warwick.tabula.roles.Masquerader
import uk.ac.warwick.tabula.services.{ModuleAndDepartmentService, ProfileService}
import uk.ac.warwick.tabula.permissions.Permissions
import uk.ac.warwick.tabula.data.Transactions._

class CurrentUserInterceptor extends HandlerInterceptorAdapter {
	var roleService = Wire[RoleService]
	var userLookup = Wire[UserLookupInterface]
	var profileService = Wire[ProfileService]
	var departmentService = Wire[ModuleAndDepartmentService]

	type MasqueradeUserCheck = (User, Boolean) => User

	def resolveCurrentUser(user: User, masqueradeUser: MasqueradeUserCheck, godModeEnabled: => Boolean) = transactional(readOnly = true) {
		val sysadmin = roleService.hasRole(new CurrentUser(user, user), Sysadmin())
		val god = sysadmin && godModeEnabled
		val masquerader =
			sysadmin ||
			roleService.hasRole(new CurrentUser(user, user), Masquerader()) ||
			!departmentService.departmentsWithPermission(new CurrentUser(user, user), Permissions.Masquerade).isEmpty
		val canMasquerade =  sysadmin || masquerader
		val apparentUser = masqueradeUser(user, canMasquerade)

		new CurrentUser(
			realUser = user,
			apparentUser = apparentUser,
			profile = profileService.getMemberByUser(user = apparentUser, disableFilter = true, eagerLoad = true),
			sysadmin = sysadmin,
			masquerader = masquerader,
			god = god)
	}

	override def preHandle(request: HttpServletRequest, response: HttpServletResponse, obj: Any) = {
		val currentUser: CurrentUser = request.getAttribute(SSOClientFilter.USER_KEY) match {
			case FoundUser(user) => resolveCurrentUser(user, apparentUser(request), godCookieExists(request))
			case _ => NoCurrentUser()
		}
		request.setAttribute(CurrentUser.keyName, currentUser)
		true //allow request to continue
	}

	private def godCookieExists(request: HttpServletRequest): Boolean =
		request.getCookies().getBoolean(CurrentUser.godModeCookie, false)

	// masquerade support
	private def apparentUser(request: HttpServletRequest)(realUser: User, canMasquerade: Boolean): User =
		if (canMasquerade) {
			request.getCookies.getString(CurrentUser.masqueradeCookie) match {
				case Some(userid) => userLookup.getUserByUserId(userid) match {
					case user: User if user.isFoundUser() => user
					case _ => realUser
				}
				case None => realUser
			}
		} else {
			realUser
		}

}