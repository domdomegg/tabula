package uk.ac.warwick.tabula.scheduling.commands.imports

import uk.ac.warwick.spring.Wire

import uk.ac.warwick.tabula.commands._
import uk.ac.warwick.tabula.data.Transactions._
import uk.ac.warwick.tabula.data.model._
import uk.ac.warwick.tabula.data.model.DegreeType.Postgraduate
import uk.ac.warwick.tabula.data.model.StaffMember
import uk.ac.warwick.tabula.helpers.Logging
import uk.ac.warwick.tabula.permissions._
import uk.ac.warwick.tabula.scheduling.services.SupervisorImporter
import uk.ac.warwick.tabula.services.ProfileService
import uk.ac.warwick.tabula.services.RelationshipService
import org.joda.time.DateTime

class ImportSupervisorsForStudentCommand(var studentCourseDetails: StudentCourseDetails)
	extends Command[Unit] with Unaudited with Logging {
	PermissionCheck(Permissions.ImportSystemData)

	var supervisorImporter = Wire.auto[SupervisorImporter]
	var profileService = Wire.auto[ProfileService]
	var relationshipService = Wire.auto[RelationshipService]

	def applyInternal() {
		if (studentCourseDetails.route != null && studentCourseDetails.route.degreeType == Postgraduate) {
			transactional() {
				importSupervisors()
			}
		}
	}

	override def describe(d: Description) = d.property("sprCode" -> studentCourseDetails.sprCode)

	def importSupervisors() {
		relationshipService
			.getStudentRelationshipTypesWithRdxType // only look for relationship types that are in RDX
			.filter { relType => // where the department settings specify that SITS should be the source
				val source = Option(studentCourseDetails.department).map { _.getStudentRelationshipSource(relType) }.getOrElse(relType.defaultSource)
				source == StudentRelationshipSource.SITS
			}
			.foreach { relationshipType =>
				val supervisorUniIds = supervisorImporter.getSupervisorUniversityIds(studentCourseDetails.scjCode, relationshipType)
				val supervisors = supervisorUniIds.flatMap { case (supervisorUniId, percentage) =>
					val m = profileService.getMemberByUniversityId(supervisorUniId)
					if (m.isEmpty) {
						logger.warn("Can't save supervisor " + supervisorUniId + " for " + studentCourseDetails.sprCode + " - not a member in Tabula db")
					}
					m.map { m => (m, percentage) }
				}

				relationshipService.replaceStudentRelationshipsWithPercentages(relationshipType, studentCourseDetails, supervisors)
			}
	}
}
