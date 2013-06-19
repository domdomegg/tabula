package uk.ac.warwick.tabula.scheduling.commands.imports

import java.sql.Date
import java.sql.ResultSet
import org.joda.time.DateTime
import org.joda.time.LocalDate
import org.springframework.beans.BeanWrapper
import org.springframework.beans.BeanWrapperImpl
import uk.ac.warwick.spring.Wire
import uk.ac.warwick.tabula.commands.Description
import uk.ac.warwick.tabula.commands.Unaudited
import uk.ac.warwick.tabula.data.Daoisms
import uk.ac.warwick.tabula.data.FileDao
import uk.ac.warwick.tabula.data.MemberDao
import uk.ac.warwick.tabula.data.SitsStatusDao
import uk.ac.warwick.tabula.data.Transactions._
import uk.ac.warwick.tabula.data.model.AlumniProperties
import uk.ac.warwick.tabula.data.model.Department
import uk.ac.warwick.tabula.data.model.Member
import uk.ac.warwick.tabula.data.model.MemberProperties
import uk.ac.warwick.tabula.data.model.ModeOfAttendance
import uk.ac.warwick.tabula.data.model.OtherMember
import uk.ac.warwick.tabula.data.model.RelationshipType.PersonalTutor
import uk.ac.warwick.tabula.data.model.RelationshipType.Supervisor
import uk.ac.warwick.tabula.data.model.Route
import uk.ac.warwick.tabula.data.model.SitsStatus
import uk.ac.warwick.tabula.data.model.StaffProperties
import uk.ac.warwick.tabula.data.model.StudentMember
import uk.ac.warwick.tabula.data.model.StudentProperties
import uk.ac.warwick.tabula.data.model.StudentProperties
import uk.ac.warwick.tabula.data.model.StudentRelationship
import uk.ac.warwick.tabula.helpers.Closeables._
import uk.ac.warwick.tabula.helpers.Logging
import uk.ac.warwick.tabula.scheduling.services.MembershipInformation
import uk.ac.warwick.tabula.scheduling.services.ModeOfAttendanceImporter
import uk.ac.warwick.tabula.scheduling.services.SitsStatusesImporter
import uk.ac.warwick.tabula.services.ModuleAndDepartmentService
import uk.ac.warwick.tabula.services.ProfileService
import uk.ac.warwick.userlookup.User
import uk.ac.warwick.tabula.data.model.StudentProperties
import uk.ac.warwick.tabula.commands.Unaudited
import uk.ac.warwick.tabula.scheduling.helpers.PropertyCopying
import uk.ac.warwick.tabula.data.model.SitsStatus
import uk.ac.warwick.tabula.data.Daoisms
import uk.ac.warwick.tabula.data.model.ModeOfAttendance
import uk.ac.warwick.tabula.services.ProfileService
import uk.ac.warwick.tabula.data.model.Department
import uk.ac.warwick.tabula.data.model.Member
import uk.ac.warwick.tabula.commands.Command
import uk.ac.warwick.tabula.data.model.Route
import uk.ac.warwick.tabula.commands.Description
import uk.ac.warwick.tabula.helpers.Logging
import uk.ac.warwick.tabula.data.model.StudentProperties
import uk.ac.warwick.tabula.commands.Unaudited
import uk.ac.warwick.tabula.data.model.SitsStatus
import uk.ac.warwick.tabula.data.Daoisms
import uk.ac.warwick.tabula.data.model.ModeOfAttendance
import uk.ac.warwick.tabula.services.ProfileService
import uk.ac.warwick.tabula.data.model.Department
import uk.ac.warwick.tabula.data.model.Member
import uk.ac.warwick.tabula.commands.Command
import uk.ac.warwick.tabula.data.model.Route
import uk.ac.warwick.tabula.commands.Description
import uk.ac.warwick.tabula.helpers.Logging
import uk.ac.warwick.tabula.data.model.StudentProperties
import uk.ac.warwick.tabula.commands.Unaudited
import uk.ac.warwick.tabula.data.model.SitsStatus
import uk.ac.warwick.tabula.data.Daoisms
import uk.ac.warwick.tabula.data.model.ModeOfAttendance
import uk.ac.warwick.tabula.services.ProfileService
import uk.ac.warwick.tabula.system.permissions.Restricted

class ImportSingleStudentRowCommand(member: MembershipInformation, ssoUser: User, resultSet: ResultSet)
	extends ImportSingleMemberCommand(member, ssoUser, resultSet)
	with Logging with Daoisms
	with StudentProperties with Unaudited with PropertyCopying {
	import ImportMemberHelpers._

	implicit val rs = resultSet
	implicit val metadata = rs.getMetaData

	var sitsStatusesImporter = Wire.auto[SitsStatusesImporter]
	var modeOfAttendanceImporter = Wire.auto[ModeOfAttendanceImporter]
	var profileService = Wire.auto[ProfileService]

	this.nationality = rs.getString("nationality")
	this.mobileNumber = rs.getString("mobile_number")

	val importSingleStudentCourseCommand = new ImportSingleStudentCourseCommand(rs)

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

		val hasChanged = copyMemberProperties(commandBean, memberBean)

		if (isTransient || hasChanged) {
			logger.debug("Saving changes for " + member)

			member.lastUpdatedDate = DateTime.now
			memberDao.saveOrUpdate(member)
		}

		importSingleStudentCourseCommand.stuMem = member
		importSingleStudentCourseCommand.apply
		//new ImportSingleStudentCourseCommand(member, rs).apply

		member
	}

	private val basicStudentProperties = Set(
		"nationality", "mobileNumber"
	)

	// We intentionally use a single pipe rather than a double pipe here - we want all statements to be evaluated
	private def copyStudentProperties(commandBean: BeanWrapper, memberBean: BeanWrapper) =
		copyBasicProperties(basicStudentProperties, commandBean, memberBean)

	private def getUniIdFromPrsCode(prsCode: String): Option[String] = {
		if (prsCode == null || prsCode.length() !=9) {
			None
		}
		else {
			val uniId = prsCode.substring(2)
			if (uniId forall Character.isDigit ) Some(uniId)
			else None
		}
	}

	override def describe(d: Description) = d.property("universityId" -> universityId).property("category" -> "student")
}
