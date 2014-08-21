package uk.ac.warwick.tabula.profiles.commands.relationships

import uk.ac.warwick.spring.Wire
import uk.ac.warwick.tabula.commands.{Command, ReadOnly, Unaudited}
import uk.ac.warwick.tabula.data.model.{Department, StudentRelationshipType}
import uk.ac.warwick.tabula.permissions._
import uk.ac.warwick.tabula.services.{ProfileService, RelationshipService}
import uk.ac.warwick.tabula.web.views.ExcelView

class StudentRelationshipTemplateCommand(val department: Department, val relationshipType: StudentRelationshipType)
	extends Command[ExcelView] with ReadOnly with Unaudited with GeneratesStudentRelationshipWorkbook {

	PermissionCheck(Permissions.Profiles.StudentRelationship.Read(mandatory(relationshipType)), department)
	
	var service = Wire[RelationshipService]
	var profileService = Wire[ProfileService]

	def applyInternal() = {

		val existingRelationships = service.listStudentRelationshipsByDepartment(relationshipType, department)
		val unallocated = service.listStudentsWithoutRelationship(relationshipType, department)

		val allAgents =
			existingRelationships
				.groupBy(_.agent)
				.filter { case (agent, _) => agent.forall(_.isDigit)}
				.flatMap { case (agent, _) => profileService.getMemberByUniversityId(agent)}
				.toSeq

		// Transform into a list of (Member, Seq[Member]) pairs
		val existingAllocations =
			existingRelationships
				.groupBy(_.studentMember)
				.toSeq
				.flatMap { case (student, rels) =>
				val agents = rels.flatMap {
					_.agentMember
				}

				(student, agents) match {
					case (None, _) => None
					case (_, Nil) => None
					case (Some(s), a) => Some((s, a))
				}
			}

		val allAllocations =
			(existingAllocations ++ unallocated.map {
				(_, Nil)
			})
				.sortBy { case (student, _) => student.lastName + ", " + student.firstName}

		val workbook = generateWorkbook(allAgents, allAllocations, department, relationshipType)

		new ExcelView("Allocation for " + allocateSheetName(department, relationshipType) + ".xlsx", workbook)

	}
}
