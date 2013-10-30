package uk.ac.warwick.tabula.dev.web.commands

import scala.collection.JavaConverters.asScalaBufferConverter

import uk.ac.warwick.spring.Wire
import uk.ac.warwick.tabula.AcademicYear
import uk.ac.warwick.tabula.commands.{CommandInternal, ComposableCommand, Unaudited}
import uk.ac.warwick.tabula.data.{AutowiringTransactionalComponent, Daoisms, MemberDao, MemberDaoImpl, ModuleDao, ModuleDaoImpl, SessionComponent, TransactionalComponent}
import uk.ac.warwick.tabula.data.model.{ModuleRegistration, StudentMember}
import uk.ac.warwick.tabula.helpers.Logging
import uk.ac.warwick.tabula.system.permissions.PubliclyVisiblePermissions

class ModuleRegistrationFixtureCommand extends CommandInternal[Seq[ModuleRegistration]] with Logging {
	this: SessionComponent with TransactionalComponent  =>

	var memberDao: MemberDao = Wire[MemberDaoImpl]
	var moduleDao: ModuleDao = Wire[ModuleDaoImpl]

	var moduleCode: String = _
	var universityIds: String = _

	protected def applyInternal() =
		transactional() {
			val module = moduleDao.getByCode(moduleCode).get
			val cats = new java.math.BigDecimal(12.0)

			val regs: Seq[ModuleRegistration] = 
				for {
					uniId <- universityIds.split(",")
					student <- memberDao.getByUniversityId(uniId).filter { _.isInstanceOf[StudentMember] }.toSeq
					scd <- student.asInstanceOf[StudentMember].studentCourseDetails.asScala
				} yield {
					val modReg = new ModuleRegistration(scd, module, cats, AcademicYear(2013), "A")
					session.save(modReg)
					scd.moduleRegistrations.add(modReg)
					session.save(scd)
					session.flush
					
					modReg
				}
				
			regs
		}
}
object ModuleRegistrationFixtureCommand{
	def apply()={
		new ModuleRegistrationFixtureCommand
			with ComposableCommand[Seq[ModuleRegistration]]
			with Daoisms
		  with AutowiringTransactionalComponent
			with Unaudited
			with PubliclyVisiblePermissions

	}
}
