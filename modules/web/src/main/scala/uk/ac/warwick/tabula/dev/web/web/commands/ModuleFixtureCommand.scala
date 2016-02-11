package uk.ac.warwick.tabula.dev.web.commands

import uk.ac.warwick.tabula.commands.scheduling.imports.ImportAcademicInformationCommand
import uk.ac.warwick.tabula.commands.{Unaudited, ComposableCommand, CommandInternal}
import uk.ac.warwick.tabula.data.Transactions._
import uk.ac.warwick.tabula.services.scheduling.ModuleInfo
import uk.ac.warwick.tabula.services.{AutowiringModuleAndDepartmentServiceComponent, ModuleAndDepartmentServiceComponent}
import uk.ac.warwick.tabula.data.{Daoisms, SessionComponent}
import uk.ac.warwick.tabula.helpers.Logging
import uk.ac.warwick.tabula.system.permissions.PubliclyVisiblePermissions
import uk.ac.warwick.tabula.data.model.{DegreeType, Module}

class ModuleFixtureCommand extends CommandInternal[Module] with Logging{

	this: ModuleAndDepartmentServiceComponent with SessionComponent=>
	import ImportAcademicInformationCommand._

  var name:String = _
	var code:String = _
	var departmentCode:String = _

	def moduleInfo = ModuleInfo(name, code,"", DegreeType.Undergraduate)
	def applyInternal() =
		transactional() {
			val department  = moduleAndDepartmentService.getDepartmentByCode(departmentCode).get
			moduleAndDepartmentService.getModuleByCode(code).foreach { module =>
				department.modules.remove(module)
				session.delete(module)
				logger.info(s"Deleted module ${code}")
			}
			val m = newModuleFrom(moduleInfo, department)
			session.save(m)
			logger.info(s"Created module ${code}")
			m
		}
}
object ModuleFixtureCommand{
	def apply()={
		new ModuleFixtureCommand()
			with ComposableCommand[Module]
			with AutowiringModuleAndDepartmentServiceComponent
			with Daoisms
			with Unaudited
			with PubliclyVisiblePermissions

	}
}
