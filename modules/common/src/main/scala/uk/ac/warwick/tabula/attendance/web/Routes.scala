package uk.ac.warwick.tabula.attendance.web

import uk.ac.warwick.tabula.data.model.{StudentRelationshipType, StudentMember, Department}
import uk.ac.warwick.tabula.web.RoutesUtils
import uk.ac.warwick.tabula.AcademicYear
import uk.ac.warwick.tabula.data.model.attendance.{AttendanceMonitoringPoint, AttendanceMonitoringScheme}

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

	object old {

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

	object Manage {
		def home = context + "/manage"
		def department(department: Department) = context + "/manage/%s" format encoded(department.code)
		def departmentForYear(department: Department, academicYear: AcademicYear) =
			context + "/manage/%s/%s" format(encoded(department.code), encoded(academicYear.startYear.toString))

		def addStudentsToScheme(scheme: AttendanceMonitoringScheme) =
			context + "/manage/%s/%s/new/%s/students" format(
				encoded(scheme.department.code), encoded(scheme.academicYear.startYear.toString), encoded(scheme.id)
			)

		def addPointsToNewScheme(scheme: AttendanceMonitoringScheme) =
			context + "/manage/%s/%s/new/%s/points" format(
				encoded(scheme.department.code), encoded(scheme.academicYear.startYear.toString), encoded(scheme.id)
			)

		def editScheme(scheme: AttendanceMonitoringScheme) =
			context + "/manage/%s/%s/%s/edit" format(
				encoded(scheme.department.code), encoded(scheme.academicYear.startYear.toString), encoded(scheme.id)
		)

		def editSchemeStudents(scheme: AttendanceMonitoringScheme) =
			context + "/manage/%s/%s/%s/edit/students" format(
				encoded(scheme.department.code), encoded(scheme.academicYear.startYear.toString), encoded(scheme.id)
		)

		def editSchemePoints(scheme: AttendanceMonitoringScheme) =
			context + "/manage/%s/%s/%s/edit/points" format(
				encoded(scheme.department.code), encoded(scheme.academicYear.startYear.toString), encoded(scheme.id)
		)

		def editPoints(department: Department, academicYear: AcademicYear) =
			context + "/manage/%s/%s/editpoints" format(
				encoded(department.code), encoded(academicYear.startYear.toString)
			)

		def addPointsToExistingSchemes(department: Department, academicYear: AcademicYear) =
			context + "/manage/%s/%s/addpoints" format(
				encoded(department.code), encoded(academicYear.startYear.toString)
			)

	}

	object Note {
		def view(academicYear: AcademicYear, student: StudentMember, point: AttendanceMonitoringPoint) =
			context + "/note/%s/%s/%s" format(encoded(academicYear.startYear.toString), encoded(student.universityId), encoded(point.id))

	}

	object View {
		def home = context + "/view"
		def department(department: Department) = context + "/view/%s" format encoded(department.code)
		def departmentForYear(department: Department, academicYear: AcademicYear) =
			context + "/view/%s/%s" format(encoded(department.code), encoded(academicYear.startYear.toString))
		def students(department: Department, academicYear: AcademicYear) =
			context + "/view/%s/%s/students" format(encoded(department.code), encoded(academicYear.startYear.toString))
		def studentsUnrecorded(department: Department, academicYear: AcademicYear) =
			context + "/view/%s/%s/students?hasBeenFiltered=true&otherCriteria=Unrecorded" format(encoded(department.code), encoded(academicYear.startYear.toString))
		def student(department: Department, academicYear: AcademicYear, student: StudentMember) =
			context + "/view/%s/%s/students/%s" format(
				encoded(department.code),
				encoded(academicYear.startYear.toString),
				encoded(student.universityId)
			)
		def points(department: Department, academicYear: AcademicYear) =
			context + "/view/%s/%s/points" format(encoded(department.code), encoded(academicYear.startYear.toString))
		def pointsUnrecorded(department: Department, academicYear: AcademicYear) =
			context + "/view/%s/%s/points?otherCriteria=Unrecorded" format(encoded(department.code), encoded(academicYear.startYear.toString))
		def agents(department: Department, academicYear: AcademicYear, relationshipType: StudentRelationshipType) =
			context + "/view/%s/%s/agents/%s" format(
				encoded(department.code),
				encoded(academicYear.startYear.toString),
				encoded(relationshipType.urlPart)
			)
	}

	object Agent {
		def home = context + "/agent"
		def relationship(relationshipType: StudentRelationshipType) = context + "/agent/%s" format encoded(relationshipType.urlPart)
		def relationshipForYear(relationshipType: StudentRelationshipType, academicYear: AcademicYear) =
			context + "/agent/%s/%s" format(encoded(relationshipType.urlPart), encoded(academicYear.startYear.toString))
		def student(relationshipType: StudentRelationshipType, academicYear: AcademicYear, student: StudentMember) =
			context + "/agent/%s/%s/%s" format(encoded(relationshipType.urlPart), encoded(academicYear.startYear.toString), encoded(student.universityId))
	}

	object Profile {
		def home = context + "/profile"
		def years(student: StudentMember) = context + "/profile/%s" format encoded(student.universityId)
		def profileForYear(student: StudentMember, academicYear: AcademicYear) =
			context + "/profile/%s/%s" format(encoded(student.universityId), encoded(academicYear.startYear.toString))
	}
}
