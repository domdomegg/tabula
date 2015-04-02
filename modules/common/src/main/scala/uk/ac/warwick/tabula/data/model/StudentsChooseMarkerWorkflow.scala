package uk.ac.warwick.tabula.data.model

import uk.ac.warwick.userlookup.User
import scala.collection.JavaConverters._
import javax.persistence.{Entity, DiscriminatorValue}
import uk.ac.warwick.tabula.data.model.MarkingMethod.StudentsChooseMarker
import uk.ac.warwick.tabula.services.{UserLookupService, SubmissionService}
import uk.ac.warwick.spring.Wire

@Entity
@DiscriminatorValue(value="StudentsChooseMarker")
class StudentsChooseMarkerWorkflow extends MarkingWorkflow with NoSecondMarker {

	def this(dept: Department) = {
		this()
		this.department = dept
	}

	@transient var submissionService = Wire[SubmissionService]
	@transient var userLookupService = Wire[UserLookupService]

	def markingMethod = StudentsChooseMarker

	override def studentsChooseMarker = true

	def getStudentsFirstMarker(assessment: Assessment, universityId: String): Option[String] = assessment match {
		case exam:Exam => None
		case assignment:Assignment => assignment.markerSelectField.flatMap { field =>
			val submission = submissionService.getSubmissionByUniId(assignment, universityId)
			submission.flatMap(_.getValue(field).map(_.value))
		}
	}

	def getMarkersStudents(assessment: Assessment, user: User) = assessment match {
		case assignment: Assignment => getSubmissions(assignment, user).map(s => userLookupService.getUserByWarwickUniId(s.universityId))
		case _ => Nil
	}


	def getSubmissions(assignment: Assignment, user: User) = assignment.markerSelectField.map { markerField =>
			val releasedSubmission = assignment.submissions.asScala.filter(s => assignment.isReleasedForMarking(s.universityId))
			releasedSubmission.filter(submission => {
				submission.getValue(markerField) match {
					case Some(subValue) => user.getUserId == subValue.value
					case None => false
				}
			})
		}.getOrElse(Nil)


}