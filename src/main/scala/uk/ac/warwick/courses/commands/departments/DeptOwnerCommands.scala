package uk.ac.warwick.courses.commands.departments

import scala.reflect.BeanProperty
import uk.ac.warwick.courses.commands._
import uk.ac.warwick.courses.data.Daoisms
import uk.ac.warwick.courses.data.model.Department
import org.hibernate.validator.constraints.NotEmpty
import uk.ac.warwick.courses.data.Transactions._

class AddDeptOwnerCommand(val department: Department) extends Command[Unit] with Daoisms {

	def getUsercodes: Seq[String] = department.owners.members

	@NotEmpty
	@BeanProperty var usercode: String = _

	override def work = transactional() {
		department.addOwner(usercode)
	}

	override def describe(d: Description) = d.properties(
		"department" -> department.code,
		"usercode" -> usercode)
}

class RemoveDeptOwnerCommand(val department: Department) extends Command[Unit] with Daoisms {

	def getUsercodes: Seq[String] = department.owners.members

	@NotEmpty
	@BeanProperty var usercode: String = _

	override def work = transactional() {
		department.removeOwner(usercode)
	}

	override def describe(d: Description) = d.properties(
		"department" -> department.code,
		"usercode" -> usercode)
}
