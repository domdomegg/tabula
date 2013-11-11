package uk.ac.warwick.tabula.scheduling.commands.imports

import scala.Option.option2Iterable
import scala.collection.JavaConversions.{mapAsScalaMap, seqAsJavaList}
import scala.collection.JavaConverters.asScalaBufferConverter
import org.joda.time.DateTime
import uk.ac.warwick.spring.Wire
import uk.ac.warwick.tabula.Features
import uk.ac.warwick.tabula.commands.{Command, Description}
import uk.ac.warwick.tabula.data.{Daoisms, MemberDao, ModuleRegistrationDaoImpl, StudentCourseDetailsDao, StudentCourseYearDetailsDao}
import uk.ac.warwick.tabula.data.Transactions.transactional
import uk.ac.warwick.tabula.data.model.{Member, ModuleRegistration, StudentCourseDetails, StudentCourseYearDetails, StudentMember}
import uk.ac.warwick.tabula.helpers.Logging
import uk.ac.warwick.tabula.permissions.Permissions
import uk.ac.warwick.tabula.scheduling.helpers.ImportRowTracker
import uk.ac.warwick.tabula.scheduling.services.{CourseImporter, MembershipInformation, ModeOfAttendanceImporter, ModuleRegistrationImporter, ProfileImporter, SitsAcademicYearAware, SitsStatusesImporter}
import uk.ac.warwick.tabula.services.{ModuleAndDepartmentService, ProfileIndexService, ProfileService, SmallGroupService, UserLookupService}
import uk.ac.warwick.userlookup.User
import scala.collection.JavaConverters._
import uk.ac.warwick.tabula.data.MemberDao

class ImportProfilesCommand extends Command[Unit] with Logging with Daoisms with SitsAcademicYearAware {

	PermissionCheck(Permissions.ImportSystemData)

	var madService = Wire.auto[ModuleAndDepartmentService]
	var profileImporter = Wire.auto[ProfileImporter]
	var profileService = Wire.auto[ProfileService]
	var userLookup = Wire.auto[UserLookupService]
	var sitsStatusesImporter = Wire.auto[SitsStatusesImporter]
	var modeOfAttendanceImporter = Wire.auto[ModeOfAttendanceImporter]
	var courseImporter = Wire.auto[CourseImporter]
	var moduleRegistrationImporter = Wire.auto[ModuleRegistrationImporter]
	var features = Wire.auto[Features]
	var moduleRegistrationDao = Wire.auto[ModuleRegistrationDaoImpl]
	var smallGroupService = Wire.auto[SmallGroupService]
	var profileIndexService = Wire.auto[ProfileIndexService]
	var memberDao = Wire.auto[MemberDao]
	var studentCourseDetailsDao = Wire.auto[StudentCourseDetailsDao]
	var studentCourseYearDetailsDao = Wire.auto[StudentCourseYearDetailsDao]

	val BatchSize = 250

	def applyInternal() {
		if (features.profiles) {
			benchmark("ImportMembers") {
				importSitsStatuses
				importModeOfAttendances
				courseImporter.importCourses
				doMemberDetails
				logger.info("Import completed")
			}
		}
	}

	def importSitsStatuses {
		logger.info("Importing SITS statuses")

		transactional() {
			sitsStatusesImporter.getSitsStatuses map { _.apply }

			session.flush
			session.clear
		}
	}

	def importModeOfAttendances {
		logger.info("Importing modes of attendance")

		transactional() {
			modeOfAttendanceImporter.getImportCommands foreach { _.apply() }

			session.flush
			session.clear
		}
	}

	/** Import basic info about all members in Membership, batched 250 at a time (small batch size is mostly for web sign-on's benefit) */

	def doMemberDetails {
		benchmark("Import all member details") {
			logger.info("Importing member details")
			val importRowTracker = new ImportRowTracker
			val importStart = DateTime.now

			// everything before the for is for debugging
			//val mem = memberDao.getByUniversityId("1014367").get
			//val membOpt = profileImporter.membershipInfoForIndividual(mem)
			//val memb = membOpt.get
			//val membSeq = Seq(Seq(memb))

			for {
				// to revert debugging, uncomment next 2 lines and remove following
				department <- madService.allDepartments;
				membershipInfos <- logSize(profileImporter.membershipInfoByDepartment(department)).grouped(BatchSize)

				//membershipInfos <- membSeq
			} {
				logger.info("Fetching user details for " + membershipInfos.size + " usercodes from websignon")
				val users: Map[String, User] = userLookup.getUsersByUserIds(membershipInfos.map(x => x.member.usercode)).toMap

				transactional() {
					logger.info("Fetching member details for " + membershipInfos.size + " members from Membership")
					val commands = profileImporter.getMemberDetails(membershipInfos, users, importRowTracker)
					commands map { _.apply }
					session.flush
					session.clear


					updateModuleRegistrationsAndSmallGroups(membershipInfos, users)
				}
			}

			stampMissingRows(importRowTracker, importStart)
		}
	}

	def stampMissingRows(importRowTracker: ImportRowTracker, importStart: DateTime) {
		// make sure any rows we've got for this student in the db which we haven't seen in this import are recorded as missing
		logger.info("Timestamping missing rows")
		val tracker = importRowTracker

		for (stu: StudentMember <- memberDao.getStudentsPresentInSits) {
			if (!importRowTracker.studentsSeen.contains(stu))
				stu.missingFromImportSince = importStart
		}

		for (scd: StudentCourseDetails <- studentCourseDetailsDao.getAllPresentInSits
				if !importRowTracker.studentCourseDetailsSeen.contains(scd)) {
			scd.missingFromImportSince = importStart
			studentCourseDetailsDao.saveOrUpdate(scd)
		}

		for (scyd: StudentCourseYearDetails <- studentCourseYearDetailsDao.getAllPresentInSits
				if !importRowTracker.studentCourseYearDetailsSeen.contains(scyd)) {
			scyd.missingFromImportSince = importStart
			studentCourseYearDetailsDao.saveOrUpdate(scyd)
		}
	}


	def updateModuleRegistrationsAndSmallGroups(membershipInfo: Seq[MembershipInformation], users: Map[String, User]): Seq[ModuleRegistration] = {
		logger.info("Fetching module registrations")
		val importModRegCommands = moduleRegistrationImporter.getModuleRegistrationDetails(membershipInfo, users)

		logger.info("Saving or updating module registrations")
		val newModuleRegistrations = (importModRegCommands map {_.apply }).flatten

		val usercodesProcessed: Seq[String] = membershipInfo map { _.member.usercode }

		logger.info("Removing old module registrations")
		deleteOldModuleRegistrations(usercodesProcessed, newModuleRegistrations)
		session.flush
		session.clear

		newModuleRegistrations
	}

	def refresh(member: Member) {
		transactional() {
			val usercode = member.userId
			val user = userLookup.getUserByUserId(usercode)

			val importRowTracker = new ImportRowTracker

			profileImporter.membershipInfoForIndividual(member) match {
				case Some(membInfo: MembershipInformation) => {

					// retrieve details for this student from SITS and store the information in Tabula
					val importMemberCommands = profileImporter.getMemberDetails(List(membInfo), Map(usercode -> user), importRowTracker)
					if (importMemberCommands.isEmpty) logger.warn("Refreshing student " + membInfo.member.universityId + " but found no data to import.")
					val members = importMemberCommands map { _.apply }

					// update missingFromSitsSince field in this student's member and course records:
					updateMissingForIndividual(member, importRowTracker)

					session.flush

					// re-import module registrations and delete old module and group registrations:
					val newModuleRegistrations = updateModuleRegistrationsAndSmallGroups(List(membInfo), Map(usercode -> user))

					// TAB-1435 refresh profile index
					profileIndexService.indexItemsWithoutNewTransaction(members.flatMap { m => profileService.getMemberByUniversityId(m.universityId) })

					for (thisMember <- members) session.evict(thisMember)
					for (modReg <- newModuleRegistrations) session.evict(modReg)

					logger.info("Data refreshed for " + member.universityId)
				}
				case None => logger.warn("Student is no longer in uow_current_members in membership - not updating")
			}
		}
	}

	/*
	 * Called after refreshing an individual student.
	 *
	 * For any rows for this member which were previously not seen in the import but have now re-emerged,
	 * record that fact by setting missingFromSitsImportSince to null.
	 *
	 * Also, for any rows for this member which were previously seen but which are now missing,
	 * record that fact by setting missingFromSiteImportSince to now.
	 */
	def updateMissingForIndividual(member: Member, importRowTracker: ImportRowTracker) {
		member match {
			case stu: StudentMember => {

				// update missingFromImportSince on member
				if (stu.missingFromImportSince != null && importRowTracker.studentsSeen.contains(stu)) {
					stu.missingFromImportSince = null
					memberDao.saveOrUpdate(stu)
				}
				else if (stu.missingFromImportSince == null && !importRowTracker.studentsSeen.contains(stu)) {
					var missingSince = stu.missingFromImportSince
					stu.missingFromImportSince = DateTime.now
					missingSince = stu.missingFromImportSince

					memberDao.saveOrUpdate(stu)
				}

				for (scd <- stu.studentCourseDetails.asScala) {

					// on studentCourseDetails
					if (scd.missingFromImportSince != null
							&& importRowTracker.studentCourseDetailsSeen.contains(scd)) {
						scd.missingFromImportSince = null
						studentCourseDetailsDao.saveOrUpdate(scd)
					}
					else if (scd.missingFromImportSince == null
							&& !importRowTracker.studentCourseDetailsSeen.contains(scd)) {
						scd.missingFromImportSince = DateTime.now
						studentCourseDetailsDao.saveOrUpdate(scd)
					}

					// and on studentCourseYearDetails
					for (scyd <- scd.studentCourseYearDetails.asScala) {
						if (scyd.missingFromImportSince != null
								&& importRowTracker.studentCourseYearDetailsSeen.contains(scyd)) {
							scyd.missingFromImportSince = null
							studentCourseYearDetailsDao.saveOrUpdate(scyd)
						}
						else if (scyd.missingFromImportSince == null
								&& !importRowTracker.studentCourseYearDetailsSeen.contains(scyd)) {
							scyd.missingFromImportSince = DateTime.now
							studentCourseYearDetailsDao.saveOrUpdate(scyd)
						}
					}
				}
			}
			case _ => {}
		}
	}

	def deleteOldModuleRegistrations(usercodes: Seq[String], newModuleRegistrations: Seq[ModuleRegistration]) {
		val existingModuleRegistrations = moduleRegistrationDao.getByUsercodesAndYear(usercodes, getCurrentSitsAcademicYear)
		for (existingMR <- existingModuleRegistrations.filterNot(mr => newModuleRegistrations.contains(mr))) {
			existingMR.studentCourseDetails.moduleRegistrations.remove(existingMR)
			session.delete(existingMR)

			smallGroupService.removeFromSmallGroups(existingMR)
		}
	}

	def equal(s1: Seq[String], s2: Seq[String]) =
		s1.length == s2.length && s1.sorted == s2.sorted

	def describe(d: Description) {

	}

}
