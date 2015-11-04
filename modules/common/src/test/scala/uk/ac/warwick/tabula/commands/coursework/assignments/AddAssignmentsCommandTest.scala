package uk.ac.warwick.tabula.commands.coursework.assignments

import org.springframework.beans.MutablePropertyValues
import org.springframework.validation.BindException
import uk.ac.warwick.tabula._
import uk.ac.warwick.tabula.data.model._
import uk.ac.warwick.tabula.services._
import uk.ac.warwick.tabula.system.{CustomDataBinder, NoAutoGrownNestedPaths}

import scala.collection.JavaConversions._

//scalastyle:off magic.number
class AddAssignmentsCommandTest extends TestBase with Mockito {

	trait Fixture {
		val thisDepartment = Fixtures.department(code="ls", name="Life Sciences")
		val module1 = Fixtures.module(code="ls101")
		val module2 = Fixtures.module(code="ls102")
		val module3 = Fixtures.module(code="ls103")

		val upstream1 = Fixtures.upstreamAssignment(module=module1, number=1)
		val upstream2 = Fixtures.upstreamAssignment(module=module2, number=2)
		val upstream3 = Fixtures.upstreamAssignment(module=module3, number=3)
		val assessmentGroup1 = Fixtures.assessmentGroup(upstream1)
		val assessmentGroup3 = Fixtures.assessmentGroup(upstream3)

		thisDepartment.modules.add(module1)
		thisDepartment.modules.add(module2)
		thisDepartment.modules.add(module3)

		val thisModuleAndDepartmentService = smartMock[ModuleAndDepartmentService]
		val thisAssignmentService = smartMock[AssessmentService]
		val thisAssignmentMembershipService = smartMock[AssessmentMembershipService]

		thisModuleAndDepartmentService.getModuleByCode(module1.code) returns Option(module1)
		thisModuleAndDepartmentService.getModuleByCode(module2.code) returns Option(module2)
		thisModuleAndDepartmentService.getModuleByCode(module3.code) returns Option(module3)

		thisAssignmentService.getAssignmentByNameYearModule(any[String], any[AcademicYear], any[Module]) returns Seq()
	}

	@Test def validate(): Unit = new Fixture { withUser("cuscav") {
		val validator = new AddAssignmentsValidation with AddAssignmentsCommandState
			with ModuleAndDepartmentServiceComponent with AssessmentServiceComponent {

			val department = thisDepartment
			val user = currentUser
			val moduleAndDepartmentService = thisModuleAndDepartmentService
			val assessmentService = thisAssignmentService
		}

		validator.academicYear = new AcademicYear(2012)
		validator.assignmentItems = Seq(
			item(upstream1, include = true, optionsId = "A"),
			item(upstream2, include = false, optionsId = null),
			item(upstream3, include = true, "A", openEnded = true)
		)
		validator.optionsMap = Map(
			"A" -> new SharedAssignmentPropertiesForm
		)

		val errors = new BindException(validator, "command")
		validator.validate(errors)
		errors.hasErrors should be {false}
	}}

	@Test def applyCommand() { new Fixture { withUser("cuscav") {
		val cmd = new AddAssignmentsCommandInternal(thisDepartment, currentUser) with AddAssignmentsCommandState
			with ModuleAndDepartmentServiceComponent with AssessmentServiceComponent with AssessmentMembershipServiceComponent {
			val moduleAndDepartmentService = thisModuleAndDepartmentService
			val assessmentService = thisAssignmentService
			val assessmentMembershipService = thisAssignmentMembershipService
		}

		cmd.academicYear = new AcademicYear(2012)
		cmd.assignmentItems = Seq(
			item(upstream1, include = true, optionsId = "A"),
			item(upstream2, include = false, optionsId = null),
			item(upstream3, include = true, "A", openEnded = true)
		)
		cmd.optionsMap = Map(
			"A" -> new SharedAssignmentPropertiesForm
		)

		val result = cmd.applyInternal()

		result.exists(_.module == module1) should be {true}
		val module1result = result.find(_.module == module1).get
		module1result.name should be ("Assignment 1")
		//check the default fields were added.
		withClue("Expecting attachment field.") { module1result.attachmentField should be ('defined) }
		withClue("Expecting comment field.") { module1result.commentField should be ('defined) }
		withClue("Expected not open ended") { assert(module1result.openEnded === false) }

		result.exists(_.module == module3) should be {true}
		val module3result = result.find(_.module == module3).get
		module3result.name should be ("Assignment 3")
		//check the default fields were added.
		withClue("Expecting attachment field.") { module3result.attachmentField should be ('defined) }
		withClue("Expecting comment field.") { module3result.commentField should be ('defined) }
		withClue("Expected open ended") { assert(module3result.openEnded === true) }
	}}}

	@Test	def optionsMapBinding() { new Fixture {
		val cmd = new AddAssignmentsCommandInternal(null, null) with AddAssignmentsCommandState
			with ModuleAndDepartmentServiceComponent with AssessmentServiceComponent with AssessmentMembershipServiceComponent {
			val moduleAndDepartmentService = thisModuleAndDepartmentService
			val assessmentService = thisAssignmentService
			val assessmentMembershipService = thisAssignmentMembershipService
		}
		val binder = new CustomDataBinder(cmd, "cmd") with NoAutoGrownNestedPaths
		val pvs = new MutablePropertyValues()

		pvs.add("optionsMap[A].allowExtensions", true)
		binder.bind(pvs)

		cmd.optionsMap("A").allowExtensions.booleanValue should be (right = true)
	}}

	private def item(assignment: AssessmentComponent, include: Boolean, optionsId: String, openEnded: Boolean = false) = {
		val item = new AssignmentItem(include, "A", assignment)
		item.optionsId = optionsId
		item.openDate  = dateTime(2012, 9)
		item.closeDate = dateTime(2012, 11)
		item.openEnded = openEnded
		item
	}


}