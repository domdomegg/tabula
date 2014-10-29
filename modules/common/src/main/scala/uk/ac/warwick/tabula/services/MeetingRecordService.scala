package uk.ac.warwick.tabula.services

import uk.ac.warwick.spring.Wire
import uk.ac.warwick.tabula.AcademicYear
import uk.ac.warwick.tabula.data.{MeetingRecordDaoComponent, AutowiringMeetingRecordDaoComponent}
import org.springframework.stereotype.Service
import uk.ac.warwick.tabula.data.model.{AbstractMeetingRecord, StudentRelationship, MeetingRecordApproval, ScheduledMeetingRecord, MeetingRecord, Member}

trait MeetingRecordServiceComponent {
	def meetingRecordService: MeetingRecordService
}

trait AutowiringMeetingRecordServiceComponent extends MeetingRecordServiceComponent {
	var meetingRecordService = Wire[MeetingRecordService]
}

trait MeetingRecordService {

	def saveOrUpdate(meeting: MeetingRecord)
	def saveOrUpdate(scheduledMeeting: ScheduledMeetingRecord)
	def saveOrUpdate(meeting: AbstractMeetingRecord)
	def saveOrUpdate(approval: MeetingRecordApproval)
	def list(rel: Set[StudentRelationship], currentMember: Option[Member]): Seq[MeetingRecord]
	def listScheduled(rel: Set[StudentRelationship], currentMember: Option[Member]): Seq[ScheduledMeetingRecord]
	def listAll(rel: Set[StudentRelationship], currentMember: Option[Member]): Seq[AbstractMeetingRecord]
	def list(rel: StudentRelationship): Seq[MeetingRecord]
	def listAll(rel: StudentRelationship): Seq[AbstractMeetingRecord]
	def get(id: String): Option[AbstractMeetingRecord]
	def purge(meeting: AbstractMeetingRecord): Unit
	def getAcademicYear(meeting: AbstractMeetingRecord, termService: TermService): Option[AcademicYear]
	def getAcademicYear(id: String, termService: TermService): Option[AcademicYear]
	def migrate(from: StudentRelationship, to: StudentRelationship): Unit
}


abstract class AbstractMeetingRecordService extends MeetingRecordService {
	self: MeetingRecordDaoComponent =>

	def saveOrUpdate(meeting: MeetingRecord) = meetingRecordDao.saveOrUpdate(meeting)
	def saveOrUpdate(scheduledMeeting: ScheduledMeetingRecord) = meetingRecordDao.saveOrUpdate(scheduledMeeting)
	def saveOrUpdate(meeting: AbstractMeetingRecord) = meetingRecordDao.saveOrUpdate(meeting)
	def saveOrUpdate(approval: MeetingRecordApproval) = meetingRecordDao.saveOrUpdate(approval)
	def list(rel: Set[StudentRelationship], currentMember: Option[Member]): Seq[MeetingRecord] = meetingRecordDao.list(rel, currentMember)
	def listScheduled(rel: Set[StudentRelationship], currentMember: Option[Member]): Seq[ScheduledMeetingRecord] =
		meetingRecordDao.listScheduled(rel, currentMember)
	def list(rel: StudentRelationship): Seq[MeetingRecord] = meetingRecordDao.list(rel)
	def listAll(rel: Set[StudentRelationship], currentMember: Option[Member]): Seq[AbstractMeetingRecord] = {
		(meetingRecordDao.list(rel, currentMember) ++ meetingRecordDao.listScheduled(rel, currentMember)).sorted
	}
	def listAll(rel: StudentRelationship): Seq[AbstractMeetingRecord] = {
		(meetingRecordDao.list(rel) ++ meetingRecordDao.listScheduled(rel)).sorted
	}
	def get(id: String): Option[AbstractMeetingRecord] =  Option(id) match {
		case Some(uid) if uid.nonEmpty => meetingRecordDao.get(uid)
		case _ => None
	}
	def purge(meeting: AbstractMeetingRecord): Unit = meetingRecordDao.purge(meeting)
	def getAcademicYear(meeting: AbstractMeetingRecord, termService: TermService): Option[AcademicYear] = Some(AcademicYear.findAcademicYearContainingDate(meeting.meetingDate, termService))
  def getAcademicYear(id: String, termService: TermService): Option[AcademicYear] = Option(id).flatMap(get(_)).flatMap(getAcademicYear(_, termService))
	def migrate(from: StudentRelationship, to: StudentRelationship): Unit =
		meetingRecordDao.migrate(from, to)
}

@Service("meetingRecordService")
class MeetingRecordServiceImpl
	extends AbstractMeetingRecordService
	with AutowiringMeetingRecordDaoComponent