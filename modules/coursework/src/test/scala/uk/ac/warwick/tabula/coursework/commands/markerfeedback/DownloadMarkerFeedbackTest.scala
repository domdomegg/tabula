package uk.ac.warwick.tabula.coursework.commands.markerfeedback

import collection.JavaConversions._
import uk.ac.warwick.tabula.AppContextTestBase
import org.junit.Before
import uk.ac.warwick.tabula.data.model.MarkingState._
import uk.ac.warwick.tabula.data.model.FileAttachment
import java.io.{FileInputStream, ByteArrayInputStream}
import uk.ac.warwick.tabula.coursework.commands.feedback.{DownloadFirstMarkersFeedbackCommand, AdminGetSingleMarkerFeedbackCommand}
import java.util.zip.ZipInputStream
import uk.ac.warwick.tabula.services.Zips
import org.springframework.util.FileCopyUtils
import java.io.FileOutputStream

class DownloadMarkerFeedbackTest extends AppContextTestBase with MarkingWorkflowWorld {

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
			val markerFeedback = assignment.getMarkerFeedbackForCurrentPosition("9876004", currentUser.apparentUser)
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
			val command = new DownloadFirstMarkersFeedbackCommand(assignment.module, assignment, currentUser)
			val renderable = command.apply()
			val stream = new ZipInputStream(new FileInputStream(renderable.file.get))
			val items = Zips.map(stream){item => item.getName}
			items.size should be (3)
		}
	}

}
