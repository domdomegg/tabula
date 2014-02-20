package uk.ac.warwick.tabula.data.model.notifications

import uk.ac.warwick.tabula.coursework.web.Routes
import uk.ac.warwick.tabula.data.model.FreemarkerModel
import uk.ac.warwick.spring.Wire
import uk.ac.warwick.tabula.services.{ProfileService, RelationshipService}
import javax.persistence.{Entity, DiscriminatorValue}

abstract class ExtensionRequestNotification extends ExtensionNotification {

	@transient
	var relationshipService = Wire.auto[RelationshipService]
	@transient
	var profileService = Wire.auto[ProfileService]

	def template: String

	def url = Routes.admin.assignment.extension.review(assignment, extension.universityId)

	def studentMember = profileService.getMemberByUniversityId(student.getWarwickId)
	def studentRelationships = relationshipService.allStudentRelationshipTypes

	def profileInfo = studentMember.flatMap(_.mostSignificantCourseDetails).map(scd => {
		val relationships = studentRelationships.map(x => (
			x.description,
			relationshipService.findCurrentRelationships(x, scd.student)
		)).filter{ case (relationshipType,relations) => relations.length != 0 }.toMap

		Map(
			"relationships" -> relationships,
			"scdCourse" -> scd.course,
			"scdRoute" -> scd.route,
			"scdAward" -> scd.award
		)
	}).getOrElse(Map())

	def content = FreemarkerModel(template, Map(
		"requestedExpiryDate" -> dateTimeFormatter.print(extension.requestedExpiryDate),
		"reasonForRequest" -> extension.reason,
		"attachments" -> extension.attachments,
		"assignment" -> assignment,
		"student" -> student,
		"path" -> url,
		"moduleManagers" -> assignment.module.managers.users
	) ++ profileInfo)

	def recipients = assignment.module.department.extensionManagers.users
}

@Entity
@DiscriminatorValue("ExtensionRequestCreated")
class ExtensionRequestCreatedNotification extends ExtensionRequestNotification {
	def verb = "create"
	def template = "/WEB-INF/freemarker/emails/new_extension_request.ftl"
	def title = titlePrefix + "New extension request made"
}

@Entity
@DiscriminatorValue("ExtensionRequestModified")
class ExtensionRequestModifiedNotification extends ExtensionRequestNotification {
	def verb = "modify"
	def template = "/WEB-INF/freemarker/emails/modified_extension_request.ftl"
	def title = titlePrefix + "Extension request modified"
}