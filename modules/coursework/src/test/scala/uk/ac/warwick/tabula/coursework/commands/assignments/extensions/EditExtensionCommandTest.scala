
import scala.collection.JavaConversions._
import org.joda.time.DateTime
import uk.ac.warwick.tabula.coursework.commands.assignments.extensions._
import uk.ac.warwick.tabula.data.model.forms.{ExtensionState, Extension}
import uk.ac.warwick.tabula.data.model.{FileAttachment, Assignment}
import uk.ac.warwick.tabula.events.EventHandling
import uk.ac.warwick.tabula.services.{UserLookupComponent, UserLookupService}
import uk.ac.warwick.tabula.{RequestInfo, Mockito, TestBase}
import uk.ac.warwick.userlookup.User
import uk.ac.warwick.tabula.coursework.commands.assignments.extensions.DeleteExtensionCommandNotification

// scalastyle:off magic.number
class EditExtensionCommandTest extends TestBase with ModifyExtensionCommandState   {

	EventHandling.enabled = false



	@Test
	def addExtension() {
		withUser("cuslat", "1171795") {
			withFakeTime(dateTime(2014, 2, 11)) {

				val assignment = createAssignment()
				val targetUniversityId = "1234567"
				val extension = createExtension(assignment, targetUniversityId)

				extension.approved should be (false)
				extension.rejected should be (false)
				extension.state should be (ExtensionState.Unreviewed)
				extension.reviewerComments should be (null)
				extension.universityId should be (targetUniversityId)
			}
		}
	}

	@Test
	def approveExtension() {
		withUser("cuslat", "1171795") {
			withFakeTime(dateTime(2014, 2, 11)) {

				val currentUser = RequestInfo.fromThread.get.user
				val assignment = createAssignment()
				val targetUniversityId = "1234567"
				val extension = createExtension(assignment, targetUniversityId)

				extension.approved should be (false)
				extension.rejected should be (false)
				extension.state should be (ExtensionState.Unreviewed)
				extension.reviewerComments should be (null)
				extension.universityId should be (targetUniversityId)

				val reviewerComments = "I've always thought that Tabula should have a photo sharing component"

				val editCommand = new EditExtensionCommandInternal(assignment.module, assignment, targetUniversityId, currentUser, ApprovalAction) with EditExtensionCommandTestSupport
				editCommand.reviewerComments = reviewerComments
				val result = editCommand.apply()

				result.approved should be (true)
				result.rejected should be (false)
				result.state should be (ExtensionState.Approved)
				result.reviewedOn should be (DateTime.now)
				result.reviewerComments should be (reviewerComments)
				result.universityId should be (targetUniversityId)
			}
		}
	}

	@Test
	def rejectExtension() {
		withUser("cuslat", "1171795") {
			withFakeTime(dateTime(2014, 2, 11)) {

				val currentUser = RequestInfo.fromThread.get.user
				val assignment = createAssignment()
				val targetUniversityId = "1234567"
				val extension = createExtension(assignment, targetUniversityId)

				extension.approved should be (false)
				extension.rejected should be (false)
				extension.state should be (ExtensionState.Unreviewed)
				extension.reviewerComments should be (null)
				extension.universityId should be (targetUniversityId)

				val reviewerComments = "something something messaging service something something $17 billion cheers thanks"

				val editCommand = new EditExtensionCommandInternal(assignment.module, assignment, targetUniversityId, currentUser, RejectionAction) with EditExtensionCommandTestSupport
				editCommand.reviewerComments = reviewerComments
				val result = editCommand.apply()

				result.approved should be (false)
				result.rejected should be (true)
				result.state should be (ExtensionState.Rejected)
				result.reviewedOn should be (DateTime.now)
				result.reviewerComments should be (reviewerComments)
				result.universityId should be (targetUniversityId)
			}
		}
	}


	@Test
	def revokeExtension() {
		withUser("cuslat", "1171795") {
			withFakeTime(dateTime(2014, 2, 11)) {

				val currentUser = RequestInfo.fromThread.get.user
				val assignment = createAssignment()
				val targetUniversityId = "1234567"
				val extension = createExtension(assignment, targetUniversityId)

				extension.approved should be (false)
				extension.rejected should be (false)
				extension.state should be (ExtensionState.Unreviewed)
				extension.universityId should be (targetUniversityId)

				assignment.extensions.add(extension)

				assignment.extensions.size should be (2)
				val deleteCommand = new DeleteExtensionCommandInternal(assignment.module, assignment, targetUniversityId, currentUser) with DeleteExtensionCommandTestSupport
				val result = deleteCommand.apply()
				assignment.extensions.size should be (1)

				result.approved should be (false)
				result.rejected should be (false)
				result.state should be (ExtensionState.Revoked)
				deleteCommand.deleted should be (true)
			}
		}
	}

	@Test
	def revokeExtensionEmit() {
		withUser("cuslat", "1171795") {
			withFakeTime(dateTime(2014, 2, 11)) {
				val currentUser = RequestInfo.fromThread.get.user
				val assignment = createAssignment()
				val targetUniversityId = "1234567"
				val extension = createExtension(assignment, targetUniversityId)
				assignment.extensions.add(extension)

				val deleteCommand = DeleteExtensionCommand(assignment.module, assignment, targetUniversityId, currentUser)

				val emitted = deleteCommand.emit(extension)
				emitted.size should be (1)
				emitted.head.recipientUniversityId should be ("1234567")
				emitted.head.entities.head should be (assignment)


			}
		}
	}


	def createAssignment(): Assignment = {
		val assignment = newDeepAssignment()
		assignment.closeDate = DateTime.now.plusMonths(1)
		assignment.extensions += new Extension(currentUser.universityId)
		assignment
	}

	def createExtension(assignment: Assignment, targetUniversityId: String, reviewerComments: String = "") : Extension = {
		val extension = new Extension(targetUniversityId)
		extension.assignment = assignment
		extension.expiryDate = DateTime.now.plusMonths(2)
		if(reviewerComments.isEmpty)
			extension.reviewerComments = null
		else
			extension.reviewerComments = reviewerComments
		extension
	}

}

trait ModifyExtensionCommandTestSupport extends UserLookupComponent
with ExtensionPersistenceComponent
with ModifyExtensionCommandState
with Mockito {

	var userLookup = mock[UserLookupService]
	val testuser = new User("cuslat")
	var deleted = false
	testuser.setWarwickId("1171975")

	userLookup.getUserByWarwickUniId(any[String]) answers { id => testuser	}

	def delete(attachment: FileAttachment) {}
	def delete(extension: Extension) { }
	def save(extension: Extension) {}

}

trait EditExtensionCommandTestSupport extends ModifyExtensionCommandTestSupport {

	this : EditExtensionCommandInternal =>

	def apply(): Extension = this.applyInternal()
}

trait DeleteExtensionCommandTestSupport extends ModifyExtensionCommandTestSupport {

	this : DeleteExtensionCommandInternal =>

	def apply(): Extension = this.applyInternal()

	override def delete(extension: Extension) { deleted = true }
}