package uk.ac.warwick.tabula.attendance.web

import uk.ac.warwick.tabula.web.BreadCrumb
import uk.ac.warwick.tabula.data.model
import uk.ac.warwick.tabula.data.model.StudentRelationshipType

trait AttendanceBreadcrumbs {
	val Breadcrumbs = AttendanceBreadcrumbs
}

object AttendanceBreadcrumbs {
	abstract class Abstract extends BreadCrumb
	case class Standard(title: String, url: Option[String], override val tooltip: String) extends Abstract

	object Old {

		/**
		 * Special case breadcrumb for the department view page.
		 */
		case class ViewDepartment(department: model.Department) extends Abstract {
			val title = department.name
			val url = Some(Routes.old.department.view(department))
		}

		/**
		 * Special case breadcrumb for the department view points page.
		 */
		case class ViewDepartmentPoints(department: model.Department) extends Abstract {
			val title = "Monitoring points"
			val url = Some(Routes.old.department.viewPoints(department))
		}

		/**
		 * Special case breadcrumb for the department view points page.
		 */
		case class ViewDepartmentStudents(department: model.Department) extends Abstract {
			val title = "Students"
			val url = Some(Routes.old.department.viewStudents(department))
		}

		/**
		 * Special case breadcrumb for the department view agents page.
		 */
		case class ViewDepartmentAgents(department: model.Department, relationshipType: StudentRelationshipType) extends Abstract {
			val title = relationshipType.agentRole.capitalize + "s"
			val url = Some(Routes.old.department.viewAgents(department, relationshipType))
		}

		/**
		 * Special case breadcrumb for the department admin page.
		 */
		case class ManagingDepartment(department: model.Department) extends Abstract {
			val title = "Manage monitoring schemes"
			val url = Some(Routes.old.department.manage(department))
		}

		/**
		 * Special case breadcrumb for agent relationship page.
		 */
		case class Agent(relationshipType: model.StudentRelationshipType) extends Abstract {
			val title = relationshipType.studentRole.capitalize + "s"
			val url = Some(Routes.old.agent.view(relationshipType))
		}

		/**
		 * Special case breadcrumb for agent student profile page.
		 */
		case class AgentStudent(student: model.StudentMember, relationshipType: model.StudentRelationshipType) extends Abstract {
			val title = student.fullName.getOrElse("")
			val url = Some(Routes.old.agent.student(student, relationshipType))
		}

	}

	object Manage {

		case object Home extends Abstract {
			val title = "Manage"
			val url = Some(Routes.Manage.home)
		}

		case class Department(department: model.Department) extends Abstract {
			val title = department.name
			val url = Some(Routes.Manage.department(department))
		}

	}
}