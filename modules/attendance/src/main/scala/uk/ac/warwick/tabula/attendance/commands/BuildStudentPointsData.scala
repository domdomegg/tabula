package uk.ac.warwick.tabula.attendance.commands

import uk.ac.warwick.tabula.data.model.StudentMember
import org.joda.time.DateTime
import uk.ac.warwick.tabula.services.{UserLookupComponent, ProfileServiceComponent, TermServiceComponent, MonitoringPointServiceComponent}
import uk.ac.warwick.tabula.AcademicYear
import scala.collection.JavaConverters._
import uk.ac.warwick.tabula.data.model.attendance.{MonitoringPointAttendanceNote, AttendanceState, MonitoringPoint}
import uk.ac.warwick.tabula.commands.TaskBenchmarking

case class CheckpointData(
	state: String,
	recorded: String,
	note: MonitoringPointAttendanceNote
)

case class StudentPointsData(
	student: StudentMember,
	pointsByTerm: Map[String, Map[MonitoringPoint, CheckpointData]],
	unrecorded: Int,
	missed: Int
)


trait BuildStudentPointsData extends MonitoringPointServiceComponent with TermServiceComponent
	with GroupMonitoringPointsByTerm with TaskBenchmarking with CheckpointUpdatedDescription with UserLookupComponent {

	def buildData(
		students: Seq[StudentMember],
		academicYear: AcademicYear,
		missedCounts: Seq[(StudentMember, Int)] = Seq(),
		unrecordedCounts: Seq[(StudentMember, Int)] = Seq()
	): Seq[StudentPointsData] = {
		val pointSetsByStudent = benchmarkTask("Find point sets for students, by student") { monitoringPointService.findPointSetsForStudentsByStudent(students, academicYear) }
		val allPoints = pointSetsByStudent.flatMap(_._2.points.asScala).toSeq
		val checkpoints = benchmarkTask("Get checkpoints for all students") { monitoringPointService.getCheckpointsByStudent(allPoints) }
		lazy val currentAcademicWeek = benchmarkTask("Get current academic week") { termService.getAcademicWeekForAcademicYear(DateTime.now(), academicYear) }
		val attendanceNotes = benchmarkTask("Get attendance notes") {
			monitoringPointService.findAttendanceNotes(students, allPoints).groupBy(_.student).map{
				case (student, notes) => student -> notes.groupBy(_.point)
			}.toMap
		}

		students.map{ student => {
			pointSetsByStudent.get(student).map{ pointSet =>
				val pointsByTerm = groupByTerm(pointSetsByStudent(student).points.asScala, academicYear)
				val pointsByTermWithCheckpointString = pointsByTerm.map{ case(term, points) =>
					term -> points.map{ point =>
						point -> {
							val checkpointOption = checkpoints.find{
								case (s, checkpoint) => s == student && checkpoint.point == point
							}
							val attendanceNoteOption = attendanceNotes.get(student).flatMap{ pointMap =>
								pointMap.get(point).flatMap{_.headOption}
							}
							checkpointOption.map{
								case (_, checkpoint) => CheckpointData(
									checkpoint.state.dbValue,
									describeCheckpoint(checkpoint),
									attendanceNoteOption.getOrElse(null)
								)
							}.getOrElse({
								if (currentAcademicWeek > point.requiredFromWeek)
									CheckpointData(
										"late",
										"",
										attendanceNoteOption.getOrElse(null)
									)
								else
									CheckpointData(
										"",
										"",
										attendanceNoteOption.getOrElse(null)
									)
							})
						}
					}.toMap
				}
				
				val unrecorded = {
					if (unrecordedCounts.size > 0)
						unrecordedCounts.find{case(s, count) => student == s}.getOrElse((student, 0))._2
					else
						pointsByTermWithCheckpointString.values.flatMap(_.values).count(_.state == "late")
				}
				val missed = {
					if (missedCounts.size > 0)
						missedCounts.find{case(s, count) => student == s}.getOrElse((student, 0))._2
					else
						pointsByTermWithCheckpointString.values.flatMap(_.values).count(_.state == AttendanceState.MissedUnauthorised.dbValue)
				}
				StudentPointsData(student, pointsByTermWithCheckpointString, unrecorded, missed)
			}.getOrElse(
				StudentPointsData(student, Map(), 0, 0)
			)
		}}
	}

}
