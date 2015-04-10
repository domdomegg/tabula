package uk.ac.warwick.tabula.jobs.reports

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import uk.ac.warwick.tabula.AcademicYear
import uk.ac.warwick.tabula.commands.reports.profiles.ProfileExportSingleCommand
import uk.ac.warwick.tabula.data.FileDao
import uk.ac.warwick.tabula.data.model.{StudentMember, FileAttachment}
import uk.ac.warwick.tabula.jobs.{Job, JobPrototype}
import uk.ac.warwick.tabula.services.jobs.JobInstance
import uk.ac.warwick.tabula.services.{AutowiringProfileServiceComponent, AutowiringFileAttachmentServiceComponent, AutowiringZipServiceComponent}

import scala.collection.JavaConverters._

object ProfileExportJob {
	val identifier = "profile-export"
	val StudentKey = "students"
	val AcademicYearKey = "academicYear"
	val ZipFilePathKey = "zipFilePath"
	val BuildingZip = "Building .zip file"
	def status(universityId: String) = s"Exporting profile for $universityId"

	def apply(students: Seq[String], academicYear: AcademicYear) = JobPrototype(identifier, Map(
		StudentKey -> students.asJava,
		AcademicYearKey -> academicYear.toString
	))
}

@Component
class ProfileExportJob extends Job with AutowiringZipServiceComponent
	with AutowiringFileAttachmentServiceComponent with AutowiringProfileServiceComponent {

	val identifier = ProfileExportJob.identifier

	// TODO remove this
	@Autowired var fileDao: FileDao = _

	override def run(implicit job: JobInstance): Unit = new Runner(job).run()

	class Runner(job: JobInstance) {
		implicit private val _job: JobInstance = job

		def run(): Unit = {
			val studentIDs = job.getStrings(ProfileExportJob.StudentKey)
			val students = profileService.getAllMembersWithUniversityIds(studentIDs).flatMap{
				case student: StudentMember => Some(student)
				case _ => None
			}
			val academicYear = AcademicYear.parse(job.getString(ProfileExportJob.AcademicYearKey))

			updateProgress(0)

			val results: Map[String, Seq[FileAttachment]] = students.zipWithIndex.map{case(student, index) =>
				updateStatus(ProfileExportJob.status(student.universityId))

				val result = ProfileExportSingleCommand(student, academicYear).apply()

				updateProgress(((index + 1).toFloat / students.size.toFloat * 100).toInt)

				student.universityId -> result
			}.toMap

			updateProgress(100)

			updateStatus(ProfileExportJob.BuildingZip)

			val zipFile = zipService.getProfileExportZip(results)
			job.setString(ProfileExportJob.ZipFilePathKey, zipFile.getPath)
			
			job.succeeded = true
		}
	}
}
