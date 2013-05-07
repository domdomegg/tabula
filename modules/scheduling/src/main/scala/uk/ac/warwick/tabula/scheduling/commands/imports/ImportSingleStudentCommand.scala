package uk.ac.warwick.tabula.scheduling.commands.imports

import java.sql.Date
import java.sql.ResultSet
import org.joda.time.DateTime
import org.joda.time.LocalDate
import org.springframework.beans.BeanWrapper
import org.springframework.beans.BeanWrapperImpl
import uk.ac.warwick.spring.Wire
import uk.ac.warwick.tabula.commands.Command
import uk.ac.warwick.tabula.commands.Description
import uk.ac.warwick.tabula.commands.Unaudited
import uk.ac.warwick.tabula.data.Daoisms
import uk.ac.warwick.tabula.data.FileDao
import uk.ac.warwick.tabula.data.MemberDao
import uk.ac.warwick.tabula.data.SitsStatusDao
import uk.ac.warwick.tabula.data.Transactions._
import uk.ac.warwick.tabula.data.model.AlumniProperties
import uk.ac.warwick.tabula.data.model.Member
import uk.ac.warwick.tabula.data.model.MemberProperties
import uk.ac.warwick.tabula.data.model.Route
import uk.ac.warwick.tabula.data.model.StaffProperties
import uk.ac.warwick.tabula.data.model.StudentMember
import uk.ac.warwick.tabula.data.model.StudentProperties
import uk.ac.warwick.tabula.data.model.StudentProperties
import uk.ac.warwick.tabula.data.model.StudyDetailsProperties
import uk.ac.warwick.tabula.data.model.StudyDetailsProperties
import uk.ac.warwick.tabula.helpers.Closeables._
import uk.ac.warwick.tabula.helpers.Logging
import uk.ac.warwick.tabula.scheduling.services.MembershipInformation
import uk.ac.warwick.tabula.services.ModuleAndDepartmentService
import uk.ac.warwick.userlookup.User
import uk.ac.warwick.tabula.data.model.SitsStatus
import uk.ac.warwick.tabula.scheduling.services.SitsStatusesImporter
import uk.ac.warwick.tabula.data.model.ModeOfAttendance
import uk.ac.warwick.tabula.scheduling.services.ModeOfAttendanceImporter
import uk.ac.warwick.tabula.data.model.OtherMember
import uk.ac.warwick.tabula.services.ProfileService
import uk.ac.warwick.tabula.data.model.RelationshipType.PersonalTutor
import uk.ac.warwick.tabula.data.model.Department
import uk.ac.warwick.tabula.data.model.StudentRelationship

class ImportSingleStudentCommand(member: MembershipInformation, ssoUser: User, resultSet: ResultSet)
  extends ImportSingleMemberCommand(member, ssoUser, resultSet)
	with Logging with Daoisms with StudentProperties with StudyDetailsProperties with Unaudited {
	import ImportMemberHelpers._

	implicit val rs = resultSet
	implicit val metadata = rs.getMetaData

	var sitsStatusesImporter = Wire.auto[SitsStatusesImporter]
	var modeOfAttendanceImporter = Wire.auto[ModeOfAttendanceImporter]
	var profileService = Wire.auto[ProfileService]

	// A few intermediate properties that will be transformed later
	var studyDepartmentCode: String = _
	var routeCode: String = _
	var sprStatusCode: String = _
	var enrolmentStatusCode: String = _
	var modeOfAttendanceCode: String = _

	// tutor data also needs some work before it can be persisted, so store it in local variables for now:
	var sprTutor1 = rs.getString("spr_tutor1")
	//var sprTutor2 = rs.getString("spr_tutor2")
	//var scjTutor1 = rs.getString("scj_tutor1")
	//var scjTutor2 = rs.getString("scj_tutor2")

	this.sprCode = rs.getString("spr_code")
	this.sitsCourseCode = rs.getString("sits_course_code")
	this.routeCode = rs.getString("route_code")
	this.studyDepartmentCode = rs.getString("study_department")
	this.yearOfStudy = rs.getInt("year_of_study")

	this.nationality = rs.getString("nationality")
	this.mobileNumber = rs.getString("mobile_number")

	this.intendedAward = rs.getString("award_code")
	this.beginDate = toLocalDate(rs.getDate("begin_date"))
	this.endDate = toLocalDate(rs.getDate("end_date"))

	this.expectedEndDate = toLocalDate(rs.getDate("expected_end_date"))

	this.fundingSource = rs.getString("funding_source")
	this.courseYearLength = rs.getString("course_year_length")

	this.sprStatusCode = rs.getString("spr_status_code")
	this.enrolmentStatusCode = rs.getString("enrolment_status_code")
	this.modeOfAttendanceCode = rs.getString("mode_of_attendance_code")

	this.ugPg = rs.getString("ug_pg")

	override def applyInternal(): Member = transactional() {
		val memberExisting = memberDao.getByUniversityId(universityId)

		logger.debug("Importing member " + universityId + " into " + memberExisting)

		val (isTransient, member) = memberExisting match {
			case Some(member: StudentMember) => (false, member)
			case Some(member: OtherMember) => {
				// TAB-692 delete the existing member, then return a brand new one
				memberDao.delete(member)
				(true, new StudentMember(universityId))
			}
			case Some(member) => throw new IllegalStateException("Tried to convert " + member + " into a student!")
			case _ => (true, new StudentMember(universityId))
		}

		val commandBean = new BeanWrapperImpl(this)
		val memberBean = new BeanWrapperImpl(member)
		val studyDetailsBean = new BeanWrapperImpl(member.studyDetails)

		// We intentionally use a single pipe rather than a double pipe here - we want both statements to be evaluated
		val hasChanged =
			copyMemberProperties(commandBean, memberBean) |
			copyStudentProperties(commandBean, memberBean) |
			copyStudyDetailsProperties(commandBean, studyDetailsBean)

		if (isTransient || hasChanged) {
			logger.debug("Saving changes for " + member)

			member.lastUpdatedDate = DateTime.now
			memberDao.saveOrUpdate(member)
		}

		captureTutor(studyDetailsBean)

		member
	}

	private val basicStudentProperties = Set(
		"nationality", "mobileNumber"
	)

	// We intentionally use a single pipe rather than a double pipe here - we want all statements to be evaluated
	private def copyStudentProperties(commandBean: BeanWrapper, memberBean: BeanWrapper) =
		copyBasicProperties(basicStudentProperties, commandBean, memberBean)

	private val basicStudyDetailsProperties = Set(
		"sprCode",
		"sitsCourseCode",
		"yearOfStudy",
		"intendedAward",
		"beginDate",
		"endDate",
		"expectedEndDate",
		"fundingSource",
		"courseYearLength",
		"modeOfAttendance",
		"ugPg"
		//,
		//"levelCode"

	)

	private def copyStudyDetailsProperties(commandBean: BeanWrapper, studyDetailsBean: BeanWrapper) = {
		copyBasicProperties(basicStudyDetailsProperties, commandBean, studyDetailsBean) |
		copyDepartment("studyDepartment", homeDepartmentCode, studyDetailsBean) |
		copyRoute("route", routeCode, studyDetailsBean) |
		copyStatus("sprStatus", sprStatusCode, studyDetailsBean) |
		copyStatus("enrolmentStatus", enrolmentStatusCode, studyDetailsBean) |
		copyModeOfAttendance("modeOfAttendance", modeOfAttendanceCode, studyDetailsBean)
	}

	private def copyStatus(property: String, code: String, memberBean: BeanWrapper) = {
		val oldValue = memberBean.getPropertyValue(property) match {
			case null => null
			case value: SitsStatus => value
		}

		if (oldValue == null && code == null) false
		else if (oldValue == null) {
			// From no SPR status to having an SPR status
			memberBean.setPropertyValue(property, toSitsStatus(code))
			true
		} else if (code == null) {
			// User had an SPR status code but now doesn't
			memberBean.setPropertyValue(property, null)
			true
		} else if (oldValue.code == code.toLowerCase) {
			false
		}	else {
			memberBean.setPropertyValue(property, toSitsStatus(code))
			true
		}
	}

	private def copyModeOfAttendance(property: String, code: String, memberBean: BeanWrapper) = {
		val oldValue = memberBean.getPropertyValue(property) match {
			case null => null
			case value: ModeOfAttendance => value
		}

		if (oldValue == null && code == null) false
		else if (oldValue == null) {
			// From no MOA to having an MOA
			memberBean.setPropertyValue(property, toModeOfAttendance(code))
			true
		} else if (code == null) {
			// User had an SPR status code but now doesn't
			memberBean.setPropertyValue(property, null)
			true
		} else if (oldValue.code == code.toLowerCase) {
			false
		}	else {
			memberBean.setPropertyValue(property, toModeOfAttendance(code))
			true
		}
	}

	def captureTutor(studyDetailsBean: BeanWrapper) = {

		// first get the department
		val dept = studyDetailsBean.getPropertyValue("studyDepartment") match {
			case value: Department => value
			case _ => null
		}

		if (dept == null)
			logger.warn("Trying to capture tutor for " + sprCode + " but department is null.")
			
		// is this student in a department that is set to import tutor data from SITS?
		else if (dept.personalTutorSource != null && dept.personalTutorSource == "SITS") {
			val pts = dept.personalTutorSource
			
			if (sprTutor1 == null)
				logger.warn("Trying to capture tutor for " + sprCode + " but PRS code on SPR is null in SITS.")
			else {
				val tutorUniId = sprTutor1.substring(2)

				// only save the personal tutor if we can match the ID with a staff member in Tabula
				val member = memberDao.getByUniversityId(tutorUniId) match {
					case Some(mem: Member) => {
						logger.info("Got a personal tutor from SITS!  sprcode: " + sprCode + ", tutorUniId: " + tutorUniId)
						profileService.saveStudentRelationship(PersonalTutor, sprCode, tutorUniId)
					}
					case _ => {
						logger.warn("SPR code: " + sprCode + ": no staff member found for PRS code " + sprTutor1 + " - not importing this personal tutor from SITS")
					}
				}
			}
		}
		else {
			val pts = dept.personalTutorSource
		}
	}

	private def copyRoute(property: String, code: String, memberBean: BeanWrapper) = {
		val oldValue = memberBean.getPropertyValue(property) match {
			case null => null
			case value: Route => value
		}

		if (oldValue == null && code == null) false
		else if (oldValue == null) {
			// From no route to having a route
			memberBean.setPropertyValue(property, toRoute(code))
			true
		} else if (code == null) {
			// User had a route but now doesn't
			memberBean.setPropertyValue(property, null)
			true
		} else if (oldValue.code == code.toLowerCase) {
			false
		}	else {
			memberBean.setPropertyValue(property, toRoute(code))
			true
		}
	}

	private def toRoute(routeCode: String) = {
		if (routeCode == null || routeCode == "") {
			null
		} else {
			moduleAndDepartmentService.getRouteByCode(routeCode.toLowerCase).getOrElse(null)
		}
	}

	private def toSitsStatus(code: String) = {
		if (code == null || code == "") {
			null
		} else {
			sitsStatusesImporter.sitsStatusMap.get(code).getOrElse(null)
		}
	}

	private def toModeOfAttendance(code: String) = {
		if (code == null || code == "") {
			null
		} else {
			modeOfAttendanceImporter.modeOfAttendanceMap.get(code).getOrElse(null)
		}
	}

	override def describe(d: Description) = d.property("universityId" -> universityId).property("category" -> "student")
}
