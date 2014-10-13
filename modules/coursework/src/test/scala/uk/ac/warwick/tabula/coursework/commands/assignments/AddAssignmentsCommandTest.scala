package uk.ac.warwick.tabula.coursework.commands.assignments

import scala.collection.JavaConversions._
import org.springframework.transaction.annotation.Transactional
import org.springframework.validation.BindException
import uk.ac.warwick.tabula.AppContextTestBase
import uk.ac.warwick.tabula.Fixtures
import uk.ac.warwick.tabula.AcademicYear
import uk.ac.warwick.tabula.data.model._
import org.springframework.web.bind.WebDataBinder
import org.springframework.beans.MutablePropertyValues
import uk.ac.warwick.tabula.system.{NoAutoGrownNestedPaths, CustomDataBinder}

//scalastyle:off magic.number
class AddAssignmentsCommandTest extends AppContextTestBase {
	
	@Transactional
	@Test def applyCommand = withUser("cuscav") {
		val f = MyFixtures()
		
		val cmd = new AddAssignmentsCommand(f.department, currentUser)
		cmd.academicYear = new AcademicYear(2012)
		cmd.assignmentItems = Seq(
			item(f.upstream1, true, "A"),
			item(f.upstream2, false, null),
			item(f.upstream3, true, "A", true)
		)
		cmd.optionsMap = Map(
			"A" -> new SharedAssignmentPropertiesForm
		)
		
		// check validation
		val errors = new BindException(cmd, "command")
		cmd.validate(errors)
		errors.hasErrors should be (false)
		
		cmd.apply
		
		val query1 = session.createQuery("from Assignment where module=:module")
		query1.setEntity("module", f.module1)
		val result1 = query1.uniqueResult().asInstanceOf[Assignment]
		result1.name should be ("Assignment 1")
		
		//check the default fields were added.
		withClue("Expecting attachment field.") { result1.attachmentField should be ('defined) }
		withClue("Expecting comment field.") { result1.commentField should be ('defined) }
		withClue("Expected not open ended") { assert(result1.openEnded === false) }
		
		val query2 = session.createQuery("from Assignment where module=:module")
		query2.setEntity("module", f.module3)
		val result2 = query2.uniqueResult().asInstanceOf[Assignment]
		result2.name should be ("Assignment 3")
		
		//check the default fields were added.
		withClue("Expecting attachment field.") { result2.attachmentField should be ('defined) }
		withClue("Expecting comment field.") { result2.commentField should be ('defined) }
		withClue("Expected open ended") { assert(result2.openEnded === true) }
	} 

	@Test
	def optionsMapBinding() {
		val command = new AddAssignmentsCommand(null, null)
		val binder = new CustomDataBinder(command, "cmd") with NoAutoGrownNestedPaths
		val pvs = new MutablePropertyValues()

		pvs.add("optionsMap[A].allowExtensions", true)
		binder.bind(pvs)

		command.optionsMap("A").allowExtensions.booleanValue should be (true)
	}
	
	case class MyFixtures() {
		val department = Fixtures.department(code="ls", name="Life Sciences")
		val module1 = Fixtures.module(code="ls101")
		val module2 = Fixtures.module(code="ls102")
		val module3 = Fixtures.module(code="ls103")

		val upstream1 = Fixtures.upstreamAssignment(module=module1, number=1)
		val upstream2 = Fixtures.upstreamAssignment(module=module2, number=2)
		val upstream3 = Fixtures.upstreamAssignment(module=module3, number=3)
		val assessmentGroup1 = Fixtures.assessmentGroup(upstream1)
		val assessmentGroup3 = Fixtures.assessmentGroup(upstream3)

		department.modules.add(module1)
		department.modules.add(module2)
		department.modules.add(module3)

		session.save(department)
		session.save(module1)
		session.save(module2)
		session.save(module3)

		session.save(upstream1)
		session.save(upstream2)
		session.save(upstream3)
		session.save(assessmentGroup1)
		session.save(assessmentGroup3)
	}
	
	def item(assignment: AssessmentComponent, include: Boolean, optionsId: String, openEnded: Boolean = false) = {
		val item = new AssignmentItem(include, "A", assignment)
		item.optionsId = optionsId
		item.openDate  = dateTime(2012, 9)
		item.closeDate = dateTime(2012, 11)
		item.openEnded = openEnded
		item
	}
	

}