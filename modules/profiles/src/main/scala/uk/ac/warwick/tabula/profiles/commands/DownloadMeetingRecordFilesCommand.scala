package uk.ac.warwick.tabula.profiles.commands

import scala.collection.JavaConversions.asScalaBuffer
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Configurable
import uk.ac.warwick.tabula.commands._
import uk.ac.warwick.tabula.data.model._
import uk.ac.warwick.tabula.data._
import uk.ac.warwick.tabula.helpers.StringUtils.StringToSuperString
import uk.ac.warwick.tabula.services.fileserver._
import uk.ac.warwick.tabula.services.ZipService
import uk.ac.warwick.tabula.CurrentUser
import uk.ac.warwick.spring.Wire
import uk.ac.warwick.tabula.JavaImports._
import uk.ac.warwick.tabula.permissions._


class DownloadMeetingRecordFilesCommand (val meetingRecord: AbstractMeetingRecord) extends Command[Option[RenderableFile]] with ReadOnly {

	PermissionCheck(Permissions.Profiles.MeetingRecord.ReadDetails(meetingRecord.relationship.relationshipType), meetingRecord)

	var filename: String = _

	var zipService = Wire.auto[ZipService]

	private var fileFound: Boolean = _
	var callback: (RenderableFile) => Unit = _

	/**
	 * If filename is unset, it returns a renderable Zip of all files.
	 * If filename is set, it will return a renderable attachment if found.
	 * In either case if it's not found, None is returned.
	 */
	def applyInternal() = {
		val result: Option[RenderableFile] =
			filename match {
				case filename: String if filename.hasText => {
					meetingRecord.attachments.find(_.name == filename).map(new RenderableAttachment(_))
				}
				case _ => Some(zipped(meetingRecord))
			}

		fileFound = result.isDefined
		if (callback != null) {
			result.map { callback(_) }
		}
		result
	}

	private def zipped(meetingRecord: AbstractMeetingRecord) = new RenderableZip(zipService.getSomeMeetingRecordAttachmentsZip(meetingRecord))

	override def describe(d: Description) = {
		d.property("filename", filename)
	}

	override def describeResult(d: Description) {
		d.property("fileFound", fileFound)
	}
}