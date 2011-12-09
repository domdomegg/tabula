package uk.ac.warwick.courses.services
import uk.ac.warwick.courses.data.Daoisms
import uk.ac.warwick.courses.data.model.Assignment
import org.springframework.stereotype.Service
import uk.ac.warwick.courses.data.model.Submission
import uk.ac.warwick.courses.data.model.Module
import uk.ac.warwick.courses.AcademicYear


trait AssignmentService {
	def getAssignmentById(id:String): Option[Assignment]
	def save(assignment:Assignment)
	def saveSubmission(submission:Submission)
	
	def getAssignmentByNameYearModule(name:String, year:AcademicYear, module:Module): Option[Assignment]
}

@Service
class AssignmentServiceImpl extends AssignmentService with Daoisms {
	def getAssignmentById(id:String) = getById[Assignment](id)
	def save(assignment:Assignment) = session.saveOrUpdate(assignment)
	def saveSubmission(submission:Submission) = {
		session.saveOrUpdate(submission)
	}
	
	def getAssignmentByNameYearModule(name:String, year:AcademicYear, module:Module) = {
		option[Assignment](session.createQuery("from Assignment where name=:name and academicYear=:year and module=:module")
			.setString("name", name)
			.setParameter("year", year)
			.setEntity("module", module)
			.uniqueResult
			)
	}
}