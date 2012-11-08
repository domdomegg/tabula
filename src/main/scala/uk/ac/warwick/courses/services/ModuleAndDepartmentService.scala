package uk.ac.warwick.courses.services

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.InitializingBean
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import uk.ac.warwick.courses.data.Transactions._
import uk.ac.warwick.courses.data.model._
import uk.ac.warwick.courses.data.DepartmentDao
import uk.ac.warwick.courses.data.ModuleDao
import uk.ac.warwick.courses.helpers.Logging
import uk.ac.warwick.userlookup.GroupService
import org.springframework.scheduling.annotation.Async
import collection.JavaConverters._
import uk.ac.warwick.userlookup.Group
import uk.ac.warwick.courses.commands.imports.ImportModulesCommand

/**
 * Handles data about modules and departments
 */
@Service
class ModuleAndDepartmentService extends Logging {

	@Autowired var moduleDao: ModuleDao = _
	@Autowired var departmentDao: DepartmentDao = _
	@Autowired var userLookup: UserLookupService = _
	def groupService = userLookup.getGroupService

	
	def allDepartments = transactional(readOnly = true) {
		departmentDao.allDepartments
	}

	def getDepartmentByCode(code: String) = transactional(readOnly = true) {
		departmentDao.getByCode(code)
	}

	def getModuleByCode(code: String) = transactional(readOnly = true) {
		moduleDao.getByCode(code)
	}

	def departmentsOwnedBy(usercode: String) = departmentDao.getByOwner(usercode)

	def modulesManagedBy(usercode: String) = moduleDao.findByParticipant(usercode)
	def modulesManagedBy(usercode: String, dept: Department) = moduleDao.findByParticipant(usercode, dept)

	def addOwner(dept: Department, owner: String) = transactional() {
		dept.owners.addUser(owner)
	}

	def removeOwner(dept: Department, owner: String) = transactional() {
		dept.owners.removeUser(owner)
	}

}