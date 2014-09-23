package uk.ac.warwick.tabula.services.timetables

import net.spy.memcached.transcoders.SerializingTranscoder
import org.joda.time.{DateTime, LocalTime}
import org.junit.Before
import uk.ac.warwick.tabula.data.model.NamedLocation
import uk.ac.warwick.tabula.data.model.groups.{DayOfWeek, WeekRange}
import uk.ac.warwick.tabula.timetables.{TimetableEvent, TimetableEventType}
import uk.ac.warwick.tabula.{AcademicYear, Mockito, TestBase}
import uk.ac.warwick.util.cache.HashMapCacheStore

class CachedTimetableFetchingServiceTest  extends TestBase with Mockito{

	private trait Fixture{

		val studentId = "studentId"
		val studentEvents = Seq(new TimetableEvent("test","test","test","test",TimetableEventType.Lecture,Nil,DayOfWeek.Monday,new LocalTime,new LocalTime,None,None,None,Nil,Nil, AcademicYear(2013)))
		val delegate = mock[CompleteTimetableFetchingService]

		delegate.getTimetableForStudent(studentId) returns studentEvents
		
		val cache = new CachedCompleteTimetableFetchingService(delegate, "cacheName")
	}

	@Before def clearCaches {
		HashMapCacheStore.clearAll()
	}

	@Test
	def firstRequestIsPassedThrough(){new Fixture {
		cache.getTimetableForStudent(studentId) should be(studentEvents)
		there was one (delegate).getTimetableForStudent(studentId)
	}}

	@Test
	def repeatedRequestsAreCached(){new Fixture {
		cache.getTimetableForStudent(studentId) should be(studentEvents)
		cache.getTimetableForStudent(studentId) should be(studentEvents)
		there was one(delegate).getTimetableForStudent(studentId)
	}}

	@Test
	def keyTypesAreDiscriminated() { new Fixture {
		// deliberately use the student ID to look up some staff events. The cache key should be the ID + the type of
		// request (staff, student, room, etc) so we should get different results back for student and staff

		val staffEvents = Seq(new TimetableEvent("test2", "test2", "test2","test2",TimetableEventType.Lecture,Nil,DayOfWeek.Monday,new LocalTime,new LocalTime,None,None,None,Nil,Nil, AcademicYear(2013)))
		delegate.getTimetableForStaff(studentId) returns staffEvents

		cache.getTimetableForStudent(studentId)  should be(studentEvents)
		cache.getTimetableForStudent(studentId)  should be(studentEvents)
		cache.getTimetableForStaff(studentId)  should be(staffEvents)
		cache.getTimetableForStaff(studentId)  should be(staffEvents)
		there was one (delegate).getTimetableForStudent(studentId)
		there was one (delegate).getTimetableForStaff(studentId)

	}}

	@Test
	def serialization() {
		val transcoder: SerializingTranscoder = new SerializingTranscoder
		transcoder.encode(TimetableCacheKey.StudentKey("0672089"))
		transcoder.encode(TimetableCacheKey.StudentKey(""))
		transcoder.encode(TimetableCacheKey.ModuleKey("cs118"))
	}

	@Test
	def eventListSerialization() {
		val transcoder: SerializingTranscoder = new SerializingTranscoder
		val events = List(
			TimetableEvent(
				"event 1 uid",
				"event 1 name",
				"event 1 title",
				"event 1 description",
				TimetableEventType.Lecture,
				Seq(WeekRange(1, 10), WeekRange(18)),
				DayOfWeek.Monday,
				new LocalTime(16, 0),
				new LocalTime(17, 0),
				Some(NamedLocation("event 1 location")),
				Some("CS118"),
				Some("Comments!"),
				Seq("0672089", "0672088"),
				Seq("1234567"),
				AcademicYear.guessSITSAcademicYearByDate(DateTime.now)
			),
			TimetableEvent(
				"event 2 uid",
				"event 2 name",
				"event 2 title",
				"event 2 description",
				TimetableEventType.Other("Another type"),
				Seq(WeekRange(1), WeekRange(2)),
				DayOfWeek.Tuesday,
				new LocalTime(10, 0),
				new LocalTime(14, 0),
				None,
				None,
				None,
				Nil,
				Nil,
				AcademicYear.guessSITSAcademicYearByDate(DateTime.now)
			)
		)

		val cachedData = transcoder.encode(events)
		cachedData should not be (null)

		transcoder.decode(cachedData) should be (events)
	}

}