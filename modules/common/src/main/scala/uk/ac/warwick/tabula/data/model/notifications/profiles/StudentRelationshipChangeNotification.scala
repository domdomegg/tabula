package uk.ac.warwick.tabula.data.model.notifications.profiles

import javax.persistence.{DiscriminatorValue, Entity}

import uk.ac.warwick.spring.Wire
import uk.ac.warwick.tabula.data.model.{FreemarkerModel, _}
import uk.ac.warwick.tabula.profiles.web.Routes
import uk.ac.warwick.tabula.services.ProfileService

abstract class StudentRelationshipChangeNotification
	extends Notification[StudentRelationship, Unit] with SingleItemNotification[StudentRelationship] {

	var profileService = Wire[ProfileService]

	def templateLocation: String

	def verb = "change"

	def relationship = item.entity
	def relationshipType = relationship.relationshipType

	def newAgent = if (relationship.endDate != null && relationship.endDate.isBeforeNow) {
		None
	} else {
		relationship.agentMember
	}

	@transient val oldAgentIds = StringSeqSetting("oldAgents", Nil)

	def oldAgents = oldAgentIds.value.flatMap { id => profileService.getMemberByUniversityId(id)}

	def content =
		FreemarkerModel(templateLocation, Map(
			"student" -> relationship.studentMember,
			"newAgents" -> newAgent.toSeq,
			"relationshipType" -> relationship.relationshipType,
			"path" -> url,
			"oldAgents" -> oldAgents

		))
	def actionRequired = false
	def url: String = Routes.profile.view(relationship.studentMember.get)
}


trait RelationshipChangeAgent {
	self: StudentRelationshipChangeNotification =>

	private def profileName = relationship.studentMember match {
		case Some(sm) if sm.fullName.nonEmpty => " for " + sm.fullName.get
		case _ => ""
	}

	override def urlTitle = s"view the student profile$profileName"
}

@Entity
@DiscriminatorValue("StudentRelationshipChangeToStudent")
class StudentRelationshipChangeToStudentNotification extends StudentRelationshipChangeNotification {
	def title: String = s"${relationshipType.agentRole.capitalize} allocation change"
	def templateLocation = StudentRelationshipChangeNotification.StudentTemplate
	def recipients = relationship.studentMember.map { _.asSsoUser }.toSeq
	def urlTitle = "view your student profile"
}

@Entity
@DiscriminatorValue("StudentRelationshipChangeToOldAgent")
class StudentRelationshipChangeToOldAgentNotification extends StudentRelationshipChangeNotification
	with RelationshipChangeAgent{

	def title: String = s"Change to ${relationshipType.studentRole}s"
	def templateLocation = StudentRelationshipChangeNotification.OldAgentTemplate
	def recipients = oldAgents.map { _.asSsoUser }.toSeq
}

@Entity
@DiscriminatorValue("StudentRelationshipChangeToNewAgent")
class StudentRelationshipChangeToNewAgentNotification extends StudentRelationshipChangeNotification
	with RelationshipChangeAgent {

	def title = s"Allocation of new ${relationshipType.studentRole}s"
	def templateLocation = StudentRelationshipChangeNotification.NewAgentTemplate
	def recipients = relationship.agentMember.map { _.asSsoUser }.toSeq
}

object StudentRelationshipChangeNotification {
	val NewAgentTemplate = "/WEB-INF/freemarker/notifications/new_agent_notification.ftl"
	val OldAgentTemplate = "/WEB-INF/freemarker/notifications/old_agent_notification.ftl"
	val StudentTemplate = "/WEB-INF/freemarker/notifications/student_change_relationship_notification.ftl"
}