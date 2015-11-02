package uk.ac.warwick.tabula.data.model

import javax.persistence._
import javax.persistence.CascadeType._
import uk.ac.warwick.tabula.ToString
import org.joda.time.DateTime
import org.hibernate.annotations.{BatchSize, Type}
import org.springframework.format.annotation.DateTimeFormat
import uk.ac.warwick.tabula.DateFormats
import uk.ac.warwick.tabula.JavaImports._
import org.hibernate.`type`.StandardBasicTypes
import java.sql.Types
import uk.ac.warwick.tabula.permissions.{Permission, Permissions, PermissionsTarget}
import uk.ac.warwick.tabula.system.permissions.RestrictionProvider
import uk.ac.warwick.tabula.data.model.forms.FormattedHtml
import uk.ac.warwick.tabula.timetables.TimetableEvent.Parent
import uk.ac.warwick.tabula.timetables.{TimetableEvent, TimetableEventType, EventOccurrence}

trait MeetingRecordAttachments {
	var attachments: JList[FileAttachment]

	def removeAttachment(attachment: FileAttachment) = {
		attachments.remove(attachment)
	}

	def removeAllAttachments() = attachments.clear()

}

object AbstractMeetingRecord {
	// do not remove - import needed for sorting DateTimes
	import uk.ac.warwick.tabula.helpers.DateTimeOrdering._
	implicit val defaultOrdering = Ordering.by { meeting: AbstractMeetingRecord => (meeting.meetingDate, meeting.lastUpdatedDate) }.reverse
}

@Entity
@Table(name = "meetingrecord")
@DiscriminatorColumn(name = "discriminator", discriminatorType = DiscriminatorType.STRING)
abstract class AbstractMeetingRecord extends GeneratedId with PermissionsTarget with ToString with CanBeDeleted
	with FormattedHtml with ToEntityReference with MeetingRecordAttachments {

	type Entity = AbstractMeetingRecord

	def isScheduled: Boolean = this match {
		case (m: ScheduledMeetingRecord) => true
		case _ => false
	}

	@Column(name="creation_date")
	var creationDate: DateTime = DateTime.now

	@Column(name="last_updated_date")
	var lastUpdatedDate: DateTime = creationDate

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "relationship_id")
	var relationship: StudentRelationship = _

	@Column(name="meeting_date")
	@DateTimeFormat(pattern = DateFormats.DateTimePicker)
	var meetingDate: DateTime = _

	@Column(name="meeting_format")
	@Type(`type` = "uk.ac.warwick.tabula.data.model.MeetingFormatUserType")
	var format: MeetingFormat = _

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name="creator_id")
	var creator: Member = _

	def readPermissions(): Seq[Permission] = Seq(Permissions.Profiles.MeetingRecord.ReadDetails(relationship.relationshipType))

	@OneToMany(mappedBy="meetingRecord", fetch=FetchType.LAZY, cascade=Array(ALL))
	@RestrictionProvider("readPermissions")
	@BatchSize(size=200)
	var attachments: JList[FileAttachment] = JArrayList()

	@RestrictionProvider("readPermissions")
	var title: String = _

	@RestrictionProvider("readPermissions")
	var description: String = _

	def escapedDescription:String = formattedHtml(description)

	def this(creator: Member, relationship: StudentRelationship) {
		this()
		this.creator = creator
		this.relationship = relationship
	}

	def toEventOccurrence(context: TimetableEvent.Context): Option[EventOccurrence]

	protected def asEventOccurrence(context: TimetableEvent.Context): Option[EventOccurrence] = {
		Some(EventOccurrence(
			uid = id,
			name = title,
			title = title,
			description = description,
			eventType = TimetableEventType.Meeting,
			start = meetingDate.toLocalDateTime,
			end = meetingDate.plusHours(1).toLocalDateTime,
			location = if (format == MeetingFormat.FaceToFace) None else Option(format).map { _.description }.map(NamedLocation),
			parent = TimetableEvent.Parent(relationship.relationshipType),
			comments = None,
			staff = context match {
				case TimetableEvent.Context.Staff => relationship.studentMember.map { _.asSsoUser }.toSeq
				case TimetableEvent.Context.Student => relationship.agentMember.map { _.asSsoUser }.toSeq
			}
		))
	}

	def permissionsParents = Option(relationship.studentCourseDetails).toStream

	def toStringProps = Seq(
		"creator" -> creator,
		"creationDate" -> creationDate,
		"relationship" -> relationship)

	override def toEntityReference = new MeetingRecordEntityReference().put(this)
}

sealed abstract class MeetingFormat(val code: String, val description: String) {
	def getCode = code
	def getDescription = description

	override def toString = description
}

object MeetingFormat {
	case object FaceToFace extends MeetingFormat("f2f", "Face-to-face meeting")
	case object VideoConference extends MeetingFormat("video", "Video conference")
	case object PhoneCall extends MeetingFormat("phone", "Telephone call")
	case object Email extends MeetingFormat("email", "Email conversation")

	// lame manual collection. Keep in sync with the case objects above
	val members = Set(FaceToFace, VideoConference, PhoneCall, Email)

	def fromCode(code: String) =
		if (code == null) null
		else members.find{_.code == code} match {
			case Some(caseObject) => caseObject
			case None => throw new IllegalArgumentException()
		}

	@Deprecated // use only in MonitoringPoint, AttendanceMonitoringPoint to catch legacy db data
	def fromCodeOrDescription(value: String) =
		if (value == null) null
		else members.find{ m => m.description == value || m.code == value} match {
			case Some(caseObject) => caseObject
			case None => throw new IllegalArgumentException()
		}
}

class MeetingFormatUserType extends AbstractBasicUserType[MeetingFormat, String] {

	val basicType = StandardBasicTypes.STRING
	override def sqlTypes = Array(Types.VARCHAR)

	val nullValue = null
	val nullObject = null

	override def convertToObject(string: String) = MeetingFormat.fromCode(string)
	override def convertToValue(format: MeetingFormat) = format.code
}
