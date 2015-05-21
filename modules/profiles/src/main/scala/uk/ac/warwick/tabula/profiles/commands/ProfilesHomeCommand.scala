package uk.ac.warwick.tabula.profiles.commands

import uk.ac.warwick.tabula.commands.{Command, CommandInternal, ReadOnly, TaskBenchmarking, Unaudited}
import uk.ac.warwick.tabula.data.model.groups.SmallGroup
import uk.ac.warwick.tabula.data.model.{Department, Member, StudentRelationshipType}
import uk.ac.warwick.tabula.permissions.Permissions
import uk.ac.warwick.tabula.profiles.web.controllers.ChecksAgent
import uk.ac.warwick.tabula.services._
import uk.ac.warwick.tabula.system.permissions.Public
import uk.ac.warwick.tabula.{CurrentUser, FeaturesComponent}

case class ProfilesHomeInformation(
	smallGroups: Seq[SmallGroup] = Nil,
	relationshipTypesMap: Map[StudentRelationshipType, Boolean] = Map(),
	adminDepartments: Set[Department] = Set()
)

object ProfilesHomeCommand {
	def apply(user: CurrentUser, currentMember: Option[Member]) =
		new ProfilesHomeCommand(user, currentMember) 
			with Command[ProfilesHomeInformation] 
			with AutowiringSmallGroupServiceComponent
			with AutowiringRelationshipServiceComponent 
			with AutowiringModuleAndDepartmentServiceComponent
			with AutowiringSecurityServiceComponent
			with Public with ReadOnly with Unaudited
		
}

abstract class ProfilesHomeCommand(val user: CurrentUser, val currentMember: Option[Member])
	extends CommandInternal[ProfilesHomeInformation] with TaskBenchmarking with ChecksAgent {

	self: FeaturesComponent with SmallGroupServiceComponent with RelationshipServiceComponent with ModuleAndDepartmentServiceComponent with SecurityServiceComponent =>

	override def applyInternal() = {
		if (user.isStaff || isAgent(user.universityId)) {
			val smallGroups =
				if (features.smallGroupTeachingTutorView) benchmarkTask("Find all small groups with user as tutor") { 
					smallGroupService.findSmallGroupsByTutor(user.apparentUser)
						.filter { group =>
							!group.groupSet.deleted &&
							(
								// The set is visible to tutors; OR
								group.groupSet.releasedToTutors ||

								// I have permission to view the membership of the set anyway
								securityService.can(user, Permissions.SmallGroups.ReadMembership, group)
							)
						}
				}
				else Nil
				
			// Get all the relationship types that the current member is an agent of
			val downwardRelationshipTypes = currentMember.map { m => 
				benchmarkTask("Get all relationship types with member") { relationshipService.listAllStudentRelationshipTypesWithMember(m) } 
			}.getOrElse(Nil)
				
			// Get all the enabled relationship types for a department
			// Filtered by department visibility in view
			val allRelationshipTypes = benchmarkTask("Get all relationship types") { relationshipService.allStudentRelationshipTypes }
			
			// A map from each type to a boolean for whether the current member has downward relationships of that type
			val relationshipTypesMap = benchmarkTask("Map relationship types to existing ones") { allRelationshipTypes.map { t =>
				(t, downwardRelationshipTypes.exists(_ == t))
			}.toMap }
			
			val adminDepartments = benchmarkTask("Get all departments with permissions to manage profiles") { 
				moduleAndDepartmentService.departmentsWithPermission(user, Permissions.Department.ManageProfiles)
			}
			
			ProfilesHomeInformation(
				smallGroups = smallGroups,
				relationshipTypesMap = relationshipTypesMap,
				adminDepartments = adminDepartments
			)
		} else ProfilesHomeInformation()
	}
}