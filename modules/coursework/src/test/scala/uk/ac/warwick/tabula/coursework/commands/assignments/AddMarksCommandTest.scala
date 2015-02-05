package uk.ac.warwick.tabula.coursework.commands.assignments

import uk.ac.warwick.tabula.coursework.commands.feedback.GeneratesGradesFromMarks

import scala.collection.JavaConversions._
import uk.ac.warwick.tabula.{TestBase, RequestInfo, Mockito}
import uk.ac.warwick.tabula.events.EventHandling
import org.springframework.validation.BindException
import uk.ac.warwick.tabula.services.UserLookupService
import org.springframework.transaction.annotation.Transactional
import org.hibernate.Session

// scalastyle:off magic.number
class AddMarksCommandTest extends TestBase with Mockito {

	EventHandling.enabled = false

	/**
	 * Check that validation marks an empty mark as an invalid row
	 * so that the apply method skips it.
	 */

	@Test
	def emptyMarkField() {
		withUser("cusebr") {
			val currentUser = RequestInfo.fromThread.get.user
			val assignment = newDeepAssignment()
			val gradeGenerator = smartMock[GeneratesGradesFromMarks]
			val command = new AdminAddMarksCommand(assignment.module, assignment, currentUser, gradeGenerator)
			command.userLookup = mock[UserLookupService]
			command.userLookup.getUserByWarwickUniId("0672088") answers { id =>
				currentUser.apparentUser
			}

			val errors = new BindException(command, "command")

			val marks1 = command.marks.get(0)
			marks1.universityId = "0672088"
			marks1.actualMark = ""

			val marks2 = command.marks.get(1)
			marks2.universityId = "1235"
			marks2.actualMark = "5"

			command.postExtractValidation(errors)
			command.apply()
		}
	}

	/**
	 * Check that validation allows either mark or grade to be non-empty
	 */
	@Transactional @Test
	def gradeButEmptyMarkField() {
		withUser("cusebr") {
			val currentUser = RequestInfo.fromThread.get.user
			val assignment = newDeepAssignment()
			val gradeGenerator = smartMock[GeneratesGradesFromMarks]
			val command = new AdminAddMarksCommand(assignment.module, assignment, currentUser, gradeGenerator) {
				override val session = mock[Session]
			}
			command.userLookup = mock[UserLookupService]
			command.userLookup.getUserByWarwickUniId("0672088") answers { id =>
				currentUser.apparentUser
			}

			val errors = new BindException(command, "command")

			val marks1 = command.marks.get(0)
			marks1.universityId = "0672088"
			marks1.actualMark = ""
			marks1.actualGrade = "EXCELLENT"

			val marks2 = command.marks.get(1)
			marks2.universityId = "55555"
			marks2.actualMark = "65"

			val marks3 = command.marks.get(2)
			marks3.universityId = "1235"
			marks3.actualMark = "A"

			val marks4 = command.marks.get(3)
			marks4.universityId = ""
			marks4.actualMark = "80"
			marks4.actualGrade = "EXCELLENT"

			val marks5 = command.marks.get(4)
			marks5.universityId = "0672088"
			marks5.actualMark = ""
			marks5.actualGrade = ""

			command.postExtractValidation(errors)
			command.marks.count(_.isValid) should be (1)

			command.apply()
		}
	}

}