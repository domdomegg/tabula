package uk.ac.warwick.tabula.attendance.web

import uk.ac.warwick.tabula.data.model.{StudentRelationshipType, StudentMember, Department}
import uk.ac.warwick.tabula.web.RoutesUtils

/**
 * Generates URLs to various locations, to reduce the number of places where URLs
 * are hardcoded and repeated.
 *
 * For methods called "apply", you can leave out the "apply" and treat the object like a function.
 */
object Routes {
	import RoutesUtils._
	private val context = "/attendance"
	def home = context + "/"

	object department {
		def view(department: Department) = context + "/%s" format encoded(department.code)
		def viewPoints(department: Department) = context + "/view/%s/2013/points" format encoded(department.code)
		def viewStudents(department: Department) = context + "/view/%s/2013/students" format encoded(department.code)
		def viewStudent(department: Department, student: StudentMember) =
			context + "/view/%s/2013/students/%s" format(encoded(department.code), encoded(student.universityId))
		def viewAgents(department: Department, relationshipType: StudentRelationshipType) =
			context + "/view/%s/2013/agents/%s" format(encoded(department.code), encoded(relationshipType.urlPart))
		def manage(department: Department) = context + "/manage/%s/2013" format encoded(department.code)
	}

	object admin {
		def departmentPermissions(department: Department) = context + "/admin/department/%s/permissions" format encoded(department.code)
	}

	object profile {
		def apply() = context + "/profile"

		def apply(student: StudentMember) =
			context + "/profile/%s/2013" format encoded(student.universityId)
	}

	object agent {
		def view(relationshipType: StudentRelationshipType) = context + "/agent/%s/2013" format encoded(relationshipType.urlPart)
		def student(student: StudentMember, relationshipType: StudentRelationshipType) =
			context + "/agent/%s/2013/%s" format(encoded(relationshipType.urlPart), encoded(student.universityId))
	}
}
