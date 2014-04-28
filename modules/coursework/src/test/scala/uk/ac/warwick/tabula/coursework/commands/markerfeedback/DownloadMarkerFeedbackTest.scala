package uk.ac.warwick.tabula.coursework.commands.markerfeedback

import collection.JavaConversions._
import uk.ac.warwick.tabula.{Mockito, AppContextTestBase}
import org.junit.Before
import uk.ac.warwick.tabula.data.model.MarkingState._
import uk.ac.warwick.tabula.data.model.{FirstFeedback, FileAttachment}
import java.io.{FileInputStream, ByteArrayInputStream}
import uk.ac.warwick.tabula.coursework.commands.feedback.{DownloadMarkersFeedbackForPositionCommand, AdminGetSingleMarkerFeedbackCommand}
import java.util.zip.ZipInputStream
import uk.ac.warwick.tabula.services.{UserLookupService, AutowiringZipServiceComponent, Zips}
import org.springframework.util.FileCopyUtils
import java.io.FileOutputStream

class DownloadMarkerFeedbackTest extends AppContextTestBase with MarkingWorkflowWorld with Mockito {

	@Before
	def setup() {
		val attachment = new FileAttachment
		
		val file = createTemporaryFile()
		FileCopyUtils.copy(new ByteArrayInputStream("yes".getBytes), new FileOutputStream(file))
		
		attachment.file = file

		assignment.feedbacks.foreach{feedback =>
			feedback.firstMarkerFeedback.attachments = List(attachment)
			feedback.firstMarkerFeedback.state = MarkingCompleted
			val smFeedback = feedback.retrieveSecondMarkerFeedback
			smFeedback.state = ReleasedForMarking
		}
	}

	@Test
	def downloadSingle()= transactional{ts=>
		withUser("cuslaj"){
			val markerFeedback = assignment.getMarkerFeedback("9876004", currentUser.apparentUser, FirstFeedback)
			val command = new AdminGetSingleMarkerFeedbackCommand(assignment.module, assignment, markerFeedback.get)
			val renderable = command.apply()
			val stream = new ZipInputStream(new FileInputStream(renderable.file.get))
			val items = Zips.map(stream){item => item.getName}
			items.size should be (1)
		}
	}

	@Test
	def downloadAll()= transactional{ts=>
		withUser("cuslat"){
			val command = new DownloadMarkersFeedbackForPositionCommand(assignment.module, assignment, currentUser, FirstFeedback) with AutowiringZipServiceComponent
			assignment.markingWorkflow.userLookup = mockUserLookup
			val renderable = command.applyInternal()
			val stream = new ZipInputStream(new FileInputStream(renderable.file.get))
			val items = Zips.map(stream){item => item.getName}
			items.size should be (3)
		}
	}

}
