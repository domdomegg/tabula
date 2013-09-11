package uk.ac.warwick.tabula.data.model

import scala.Option.option2Iterable
import org.hibernate.annotations.Type
import org.joda.time.DateTime
import javax.persistence.Basic
import javax.persistence.Entity
import javax.persistence.JoinColumn
import javax.persistence.ManyToOne
import javax.persistence.NamedQueries
import uk.ac.warwick.tabula.AcademicYear
import uk.ac.warwick.tabula.JavaImports.JInteger
import uk.ac.warwick.tabula.ToString
import uk.ac.warwick.tabula.permissions.PermissionsTarget
import uk.ac.warwick.tabula.system.permissions.Restricted

@Entity
class StudentCourseYearDetails extends StudentCourseYearProperties
	with GeneratedId with ToString with HibernateVersioned with PermissionsTarget
	with Ordered[StudentCourseYearDetails]{

	def this(studentCourseDetails: StudentCourseDetails, sceSequenceNumber: JInteger) {
		this()
		this.studentCourseDetails = studentCourseDetails
		this.sceSequenceNumber = sceSequenceNumber
	}

	@ManyToOne
	@JoinColumn(name="scjCode", referencedColumnName="scjCode")
	var studentCourseDetails: StudentCourseDetails = _

	def toStringProps = Seq("studentCourseDetails" -> studentCourseDetails)

	def permissionsParents = Option(studentCourseDetails).toStream

	def compare(that:StudentCourseYearDetails): Int = {
		if (this.academicYear != that.academicYear) {
			this.academicYear.compare(that.academicYear)
		}
		else {
			this.sceSequenceNumber - that.sceSequenceNumber
		}
	}

	def equals(that: StudentCourseYearDetails) = {
		(this.studentCourseDetails == that.studentCourseDetails) && (this.sceSequenceNumber == that.sceSequenceNumber)
	}
}

trait StudentCourseYearProperties {

	var sceSequenceNumber: JInteger = _

	@ManyToOne
	@JoinColumn(name="enrolmentStatusCode", referencedColumnName="code")
	@Restricted(Array("Profiles.Read.StudentCourseDetails.Status"))
	var enrolmentStatus: SitsStatus = _

	@ManyToOne
	@JoinColumn(name="modeOfAttendanceCode", referencedColumnName="code")
	@Restricted(Array("Profiles.Read.StudentCourseDetails.Status"))
	var modeOfAttendance: ModeOfAttendance = _

	@Basic
	@Type(`type` = "uk.ac.warwick.tabula.data.model.AcademicYearUserType")
	@Restricted(Array("Profiles.Read.StudentCourseDetails.Core"))
	var academicYear: AcademicYear = _

	@Restricted(Array("Profiles.Read.StudentCourseDetails.Core"))
	var yearOfStudy: JInteger = _

	@Type(`type` = "uk.ac.warwick.tabula.data.model.ModuleRegistrationStatusUserType")
	var moduleRegistrationStatus: ModuleRegistrationStatus = _ // intuit.cam_ssn.ssn_mrgs

	var lastUpdatedDate = DateTime.now

}

object YearAndSequenceOrdering extends Ordering[StudentCourseYearDetails] {

	def compare(firstScyd: StudentCourseYearDetails, secondScyd: StudentCourseYearDetails) = {
		// return the one with the latest year
		if (firstScyd.academicYear.startYear > secondScyd.academicYear.startYear) 1
		else if (firstScyd.academicYear.startYear < secondScyd.academicYear.startYear) -1

		// the years are the same - compare on SCE sequence number
		else if (firstScyd.sceSequenceNumber > secondScyd.sceSequenceNumber) 1
		else if (firstScyd.sceSequenceNumber < secondScyd.sceSequenceNumber) -1

		// they are the same (this should never happen)
		else 0
	}
}
