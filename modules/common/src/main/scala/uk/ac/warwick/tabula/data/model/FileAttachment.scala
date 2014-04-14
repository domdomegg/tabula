package uk.ac.warwick.tabula.data.model

import scala.language.postfixOps
import java.io._
import com.google.common.io.Files
import org.hibernate.annotations.AccessType
import org.joda.time.DateTime
import javax.persistence._
import uk.ac.warwick.tabula.JavaImports._
import uk.ac.warwick.tabula.data.FileDao
import uk.ac.warwick.tabula.data.model.forms.{SavedFormValue, Extension}
import scala.util.matching.Regex
import javax.persistence.CascadeType._
import uk.ac.warwick.spring.Wire
import uk.ac.warwick.tabula.helpers.DetectMimeType._

@Entity @AccessType("field")
class FileAttachment extends GeneratedId {
	import FileAttachment._

	@transient var fileDao = Wire.auto[FileDao]

	@Column(name="file_hash")
	var hash: String = _

	// optional link to a SubmissionValue
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "submission_id")
	var submissionValue: SavedFormValue = null

	// optional link to some Feedback
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "feedback_id")
	var feedback: Feedback = _

	// optional link to an Extension
	@ManyToOne(fetch=FetchType.LAZY)
	@JoinColumn(name="extension_id")
	var extension:Extension =_

	// optional link to a Member Note
	@ManyToOne(fetch=FetchType.LAZY)
	@JoinColumn(name="member_note_id")
	var memberNote:MemberNote =_

	// optional link to Meeting Record
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "meetingrecord_id")
	var meetingRecord: AbstractMeetingRecord = _

	@ManyToOne(fetch=FetchType.LAZY)
	@JoinTable(name="MarkerFeedbackAttachment",
		joinColumns=Array( new JoinColumn(name="file_attachment_id") ),
		inverseJoinColumns=Array( new JoinColumn(name="marker_feedback_id")) )
	var markerFeedback:MarkerFeedback = _

	@OneToOne(fetch = FetchType.LAZY, cascade = Array(PERSIST), mappedBy = "attachment")
	var originalityReport: OriginalityReport = _

	@OneToOne(fetch = FetchType.LAZY, cascade = Array(PERSIST), mappedBy = "attachment")
	var feedbackForm: FeedbackTemplate = _

	/**
	 * WARNING this method isn't exhaustive. It only checks fields that are directly on this
	 * attachment table. It won't check mappings where the foreign key is on the other side,
	 * which is the case for things like member photos.
	 */
	def isAttached: JBoolean = Seq(feedback, submissionValue, extension, originalityReport).exists(_ != null)

	var temporary: JBoolean = true

	var dateUploaded: DateTime = DateTime.now
	var uploadedBy: String = _

	@transient private var _file: File = null
	def file = {
		if (_file == null) _file = fileDao.getData(id).orNull
		_file
	}
	def file_=(f: File) { _file = f }

	@Column(name = "name")
	private var _name: String = _
	def name = _name
	def getName = _name
	def setName(n: String) { name = n }
	def name_=(n: String) {
		_name = Option(n).map(sanitisedFilename).orNull
	}

	def this(n: String) {
		this()
		name = n
	}

	def length: Option[Long] = Option(file) map { _.length }

	// checks the length field first. If that is not populated use uploadedData instead
	def actualDataLength = length getOrElse uploadedDataLength

	def fileExt: String = {
		if (name.lastIndexOf('.') > -1) {
			name.substring(name.lastIndexOf('.') + 1)
		} else {
			""
		}
	}

	/**
	 * A stream to read the entirety of the data Blob, or null
	 * if there is no Blob.
	 */
	def dataStream: InputStream = Option(file) map { new FileInputStream(_) } orNull

	def hasData = file != null

	@transient var uploadedData: InputStream = null
	@transient var uploadedDataLength: Long = 0

	def isDataEqual(other: Any) = other match {
		case that: FileAttachment =>
			if (this.actualDataLength != that.actualDataLength) false
			else {
				Files.equal(this.file, that.file)
			}
		case _ => false
	}

	@transient lazy val mimeType: String = file match {
		case null => "application/octet-stream"
		case f => detectMimeType(f)
	}
}

object FileAttachment {

	private val BadCharacters = new Regex("""[<\\"|;:*/>?]""")
	private val Space = new Regex("(\\s|%20)+")
	private val BadExtensions = new Regex("\\s*\\.\\s*(\\w+)$")

	def sanitisedFilename(filename: String) = {
		val trimmed = filename.trim
		val niceChars = BadCharacters.replaceAllIn(trimmed, "")
		val deSpaced = Space.replaceAllIn(niceChars, " ")
		BadExtensions.replaceAllIn(deSpaced, ".$1")
	}

}
