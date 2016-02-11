package uk.ac.warwick.tabula.web.controllers.coursework

import uk.ac.warwick.tabula.web.controllers.BaseController
import uk.ac.warwick.tabula.data.model.{Member, RuntimeMember}

abstract class CourseworkController extends BaseController with CourseworkBreadcrumbs with CurrentMemberComponent {
	final def optionalCurrentMember = user.profile
	final def currentMember = optionalCurrentMember getOrElse(new RuntimeMember(user))
}

trait CurrentMemberComponent {
	def optionalCurrentMember: Option[Member]
	def currentMember: Member
}