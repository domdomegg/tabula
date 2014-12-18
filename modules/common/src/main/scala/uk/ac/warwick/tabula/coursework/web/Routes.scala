package uk.ac.warwick.tabula.coursework.web

import uk.ac.warwick.tabula.data.model._
import uk.ac.warwick.tabula.web.RoutesUtils
import uk.ac.warwick.userlookup.User

/**
 * Generates URLs to various locations, to reduce the number of places where URLs
 * are hardcoded and repeated.
 *
 * For methods called "apply", you can leave out the "apply" and treat the object like a function.
 */
object Routes {
	import RoutesUtils._
	private val context = "/coursework"
	def home = context + "/"

	object assignment {
		def apply(assignment: Assignment) = context + "/module/%s/%s/" format (encoded(assignment.module.code), encoded(assignment.id))
		def receipt(assignment: Assignment) = apply(assignment)
	}

	object admin {
		def department(department: Department) = context + "/admin/department/%s/" format encoded(department.code)
		def feedbackTemplates (department: Department) = context + "/admin/department/%s/settings/feedback-templates/" format encoded(department.code)
		def extensionSettings (department: Department) = context + "/admin/department/%s/settings/extensions" format encoded(department.code)
		def feedbackReports (department: Department) = context + "/admin/department/%s/reports/feedback/" format encoded(department.code)

		object markingWorkflow {
			def list(department: Department) = admin.department(department) + "markingworkflows"
			def add(department: Department) = list(department) + "/add"
			def edit(scheme: MarkingWorkflow) = list(scheme.department) + "/edit/" + scheme.id
		}

		object module {
			def apply(module: Module) = department(module.adminDepartment) + "#module-" + encoded(module.code)
		}

		object assignment {

			private def markerroot(assignment: Assignment, marker: User) = assignmentroot(assignment) + s"/marker/${marker.getWarwickId}"

			object markerFeedback {
				def apply(assignment: Assignment, marker: User) = markerroot(assignment, marker) + "/list"
				object complete {
					def apply(assignment: Assignment, marker: User) = markerroot(assignment, marker) + "/marking-completed"
				}
				object uncomplete {
					def apply(assignment: Assignment, marker: User) = markerroot(assignment, marker) + "/marking-uncompleted"
				}
				object marksTemplate {
					def apply(assignment: Assignment, marker: User) = markerroot(assignment, marker) + "/marks-template"
				}
				object onlineFeedback {
					def apply(assignment: Assignment, marker: User) = markerroot(assignment, marker) + "/feedback/online"

					object student {
						def apply(assignment: Assignment, marker: User, student: User) =
							markerroot(assignment, marker) + s"/feedback/online/${student.getWarwickId}/"
					}
					object moderation {
						def apply(assignment: Assignment, marker: User, student: User) =
							markerroot(assignment, marker) + s"/feedback/online/moderation/${student.getWarwickId}/"
					}
				}
				object marks {
					def apply(assignment: Assignment, marker: User) = markerroot(assignment, marker) + "/marks"
				}
				object feedback {
					def apply(assignment: Assignment, marker: User) = markerroot(assignment, marker) + "/feedback"
				}
				object submissions {
					def apply(assignment: Assignment, marker: User) = markerroot(assignment, marker) + "/submissions.zip"
				}
				object downloadFeedback {
					object marker {
						def apply(assignment: Assignment, marker: User, feedbackId: String, filename: String) =
							markerroot(assignment, marker) + s"/feedback/download/${feedbackId}/${filename}"
					}

					object all {
						def apply(assignment: Assignment, marker: User, markerFeedback: String) = markerroot(assignment, marker) + s"/feedback/download/$markerFeedback/attachments/"
					}

					object one {
						def apply(assignment: Assignment, marker: User, markerFeedback: String, filename: String) = markerroot(assignment, marker) + s"/feedback/download/$markerFeedback/attachment/$filename"
					}
				}

			}

			object feedbackAdjustment {
				def apply(assignment: Assignment, student: User) = assignmentroot(assignment) + "/feedback/adjustments"
			}

			object onlineFeedback {
				def apply(assignment: Assignment) = assignmentroot(assignment) + "/feedback/online"
			}

			object onlineModeration {
				def apply(assignment: Assignment, marker: User) = assignmentroot(assignment) + s"/marker/${marker.getWarwickId}/feedback/online/moderation"
			}

			object onlineSecondMarker {
				def apply(assignment: Assignment, marker: User) = assignmentroot(assignment) + s"/marker/${marker.getWarwickId}/feedback/online/secondmarker"
			}

			def create(module: Module) = context + "/admin/module/%s/assignments/new" format encoded(module.code)

			private def assignmentroot(assignment: Assignment) = context + "/admin/module/%s/assignments/%s" format (encoded(assignment.module.code), assignment.id)

			def edit(assignment: Assignment) = assignmentroot(assignment) + "/edit"

			def delete(assignment: Assignment) = assignmentroot(assignment) + "/delete"

			def submissionsZip(assignment: Assignment) = assignmentroot(assignment) + "/submissions.zip"

			object submissionsandfeedback {
				def apply(assignment: Assignment) = assignmentroot(assignment) + "/list"
				def summary(assignment: Assignment) = assignmentroot(assignment) + "/summary"
				def table(assignment: Assignment) = assignmentroot(assignment) + "/table"
			}

			object turnitin {
				def status(assignment: Assignment) = assignmentroot(assignment) + "/turnitin"
			}

			object extension {
				def expandrow (assignment: Assignment, universityId: String) = assignmentroot(assignment) + "/extensions?universityId=" + universityId

				// def detail doesn't use assignmentroot since assignmentroot includes the assignment ID in the middle, but
				// it needs to be on the end for managing extension requests across department so that
				// it can be passed as a unique contentId when toggling rows (jquery-expandingTable.js)
				def detail (assignment: Assignment) = context + "/admin/module/%s/assignments/extensions/detail" format encoded(assignment.module.code)
				def revoke (assignment: Assignment, universityId: String) = assignmentroot(assignment) + "/extensions/revoke/" + universityId
			}
		}
	}

}
