package uk.ac.warwick.tabula.scheduling.commands.imports

import java.sql.ResultSet
import java.sql.ResultSetMetaData

import scala.language.implicitConversions

import org.apache.commons.lang3.text.WordUtils
import org.joda.time.LocalDate

import uk.ac.warwick.tabula.commands.Description
import uk.ac.warwick.tabula.commands.Unaudited
import uk.ac.warwick.tabula.data.Daoisms
import uk.ac.warwick.tabula.data.FileDao
import uk.ac.warwick.tabula.data.MemberDao
import uk.ac.warwick.tabula.data.Transactions._
import uk.ac.warwick.tabula.data.model.AlumniProperties
import uk.ac.warwick.tabula.data.model.Gender
import uk.ac.warwick.tabula.data.model.MemberProperties
import uk.ac.warwick.tabula.data.model.MemberUserType
import uk.ac.warwick.tabula.data.model.StaffProperties
import uk.ac.warwick.tabula.data.model.StudentProperties
import uk.ac.warwick.tabula.helpers.Closeables._
import uk.ac.warwick.tabula.helpers.Logging
import uk.ac.warwick.tabula.scheduling.helpers.PropertyCopying
import uk.ac.warwick.tabula.scheduling.services.MembershipInformation
import uk.ac.warwick.tabula.services.ModuleAndDepartmentService
import uk.ac.warwick.userlookup.User

abstract class ImportMemberFromMembershipCommand
	extends ImportMemberCommand with Logging with Daoisms
	with MemberProperties with Unaudited with PropertyCopying {

	import ImportMemberHelpers._

	def this(mac: MembershipInformation, ssoUser: User, rs: ResultSet) {
		this()

		implicit val resultSet = rs
		implicit val metadata = rs.getMetaData

		val member = mac.member
		this.membershipLastUpdated = member.modified

		this.universityId = oneOf(member.universityId, optString("university_id")).get
		this.userId = member.usercode

		this.userType = member.userType

		this.title = oneOf(member.title, optString("title")) map { WordUtils.capitalizeFully(_).trim() } getOrElse("")
		this.firstName = oneOf(
			member.preferredForenames,
			optString("preferred_forename"),
			ssoUser.getFirstName
		) map { formatForename(_, ssoUser.getFirstName) } getOrElse("")
		this.fullFirstName = oneOf(optString("forenames"), ssoUser.getFirstName) map { formatForename(_, ssoUser.getFirstName) } getOrElse("")
		this.lastName = oneOf(member.preferredSurname, optString("family_name"), ssoUser.getLastName) map { formatSurname(_, ssoUser.getLastName) } getOrElse("")

		this.email = (oneOf(member.email, optString("email_address"), ssoUser.getEmail).orNull)
		this.homeEmail = (oneOf(member.alternativeEmailAddress, optString("alternative_email_address")).orNull)

		this.gender = (oneOf(member.gender, optString("gender") map { Gender.fromCode(_) }).orNull)
		this.photoOption = mac.photo

		this.jobTitle = member.position
		this.phoneNumber = member.phoneNumber

		this.inUseFlag = rs.getString("in_use_flag")
		this.groupName = member.targetGroup
		this.inactivationDate = member.endDate

		this.homeDepartmentCode = (oneOf(member.departmentCode, optString("home_department_code"), ssoUser.getDepartmentCode).orNull)
		this.dateOfBirth = (oneOf(member.dateOfBirth, optLocalDate("date_of_birth")).orNull)
	}
	override def describe(d: Description) = d.property("universityId" -> universityId)
}
