package uk.ac.warwick.tabula.data

import org.hibernate.criterion.{Order, Restrictions}
import org.springframework.stereotype.Repository
import uk.ac.warwick.spring.Wire
import uk.ac.warwick.tabula.data.model._

trait MeetingRecordDao {
	def saveOrUpdate(meeting: MeetingRecord)
	def saveOrUpdate(scheduledMeeting: ScheduledMeetingRecord)
	def saveOrUpdate(meeting: AbstractMeetingRecord)
	def saveOrUpdate(approval: MeetingRecordApproval)
	def listScheduled(rel: Set[StudentRelationship], currentUser: Option[Member]): Seq[ScheduledMeetingRecord]
	def list(rel: Set[StudentRelationship], currentUser: Option[Member]): Seq[MeetingRecord]
	def list(rel: StudentRelationship): Seq[MeetingRecord]
	def get(id: String): Option[AbstractMeetingRecord]
	def purge(meeting: AbstractMeetingRecord): Unit
}

@Repository
class MeetingRecordDaoImpl extends MeetingRecordDao with Daoisms {

	def saveOrUpdate(meeting: MeetingRecord) = session.saveOrUpdate(meeting)

	def saveOrUpdate(scheduledMeeting: ScheduledMeetingRecord) = session.saveOrUpdate(scheduledMeeting)

	def saveOrUpdate(meeting: AbstractMeetingRecord) = session.saveOrUpdate(meeting)

	def saveOrUpdate(approval: MeetingRecordApproval) = session.saveOrUpdate(approval)

	def list(rel: Set[StudentRelationship], currentUser: Option[Member]): Seq[MeetingRecord] = {
		if (rel.isEmpty)
			Seq()
		else
			addMeetingRecordListRestrictions(session.newCriteria[MeetingRecord], rel, currentUser).seq
	}

	def listScheduled(rel: Set[StudentRelationship], currentUser: Option[Member]): Seq[ScheduledMeetingRecord] = {
		if (rel.isEmpty)
			Seq()
		else
			addMeetingRecordListRestrictions(session.newCriteria[ScheduledMeetingRecord], rel, currentUser).seq
	}

	private def addMeetingRecordListRestrictions[A](criteria: ScalaCriteria[A], rel: Set[StudentRelationship], currentUser: Option[Member]) = {
		criteria.add(safeIn("relationship", rel.toSeq))

		// and only pick records where deleted = 0 or the current user id is the creator id
		// - so that no-one can see records created and deleted by someone else
		currentUser match {
			case None | Some(_: RuntimeMember) => criteria.add(is("deleted", false))
			case Some(cu) => criteria.add(Restrictions.disjunction()
				.add(is("deleted", false))
				.add(is("creator", cu))
			)
		}

		criteria.addOrder(Order.desc("meetingDate")).addOrder(Order.desc("lastUpdatedDate"))
	}

	def list(rel: StudentRelationship): Seq[MeetingRecord] = {
		session.newCriteria[MeetingRecord]
			.add(Restrictions.eq("relationship", rel))
			.add(is("deleted", false))
			.addOrder(Order.desc("meetingDate"))
			.addOrder(Order.desc("lastUpdatedDate"))
			.seq
	}


	def get(id: String) = getById[AbstractMeetingRecord](id)

	def purge(meeting: AbstractMeetingRecord): Unit = {
		session.delete(meeting)
		session.flush()
	}
}

trait MeetingRecordDaoComponent {
	val meetingRecordDao: MeetingRecordDao
}
trait AutowiringMeetingRecordDaoComponent extends MeetingRecordDaoComponent{
	val meetingRecordDao = Wire.auto[MeetingRecordDao]
}
