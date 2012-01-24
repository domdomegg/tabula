package uk.ac.warwick.courses.commands
import collection.mutable
import uk.ac.warwick.courses.data.model._
import uk.ac.warwick.courses.AppImports

trait Describable {
	// describe the thing that's happening.
	def describe(d:Description)
	// optional extra description after the thing's happened.
	def describeResult(d:Description) {}
	val eventName:String
}

/**
 * Stateful instance of an action in the application.
 * It could be anything that we might want to keep track of,
 * especially if we might want to audit log it. Anything that
 * adds or changes any data is a candidate. Read-only queries,
 * not so much (unless we're interested in when a thing is observed/downloaded).
 */
trait Command[R] extends Describable with AppImports {
	def apply(): R
	lazy val eventName = getClass.getSimpleName.replaceAll("Command$","")
}

trait Unaudited { self:Describable =>
	// override describe() with nothing, since it'll never be used.
	override def describe(d:Description) {}
}

/**
 * Object for a Command to describe what it's just done.
 */
abstract class Description {
	protected var map = Map[String,Any]()
	def properties(props: Pair[String,Any]*) = {
		map ++= props
		this
	}
	def properties(otherMap: Map[String,Any]) = {
		map ++= otherMap
		this
	}
	def property(prop: Pair[String,Any]) = {
		map += prop
		this
	}
	
	def feedback(feedback:Feedback) = {
		map += "feedback" -> feedback.id
		if (feedback.assignment != null) assignment(feedback.assignment)
		this
	}
	
	def studentIds(universityIds:Seq[String]) = {
		map += "students" -> universityIds
		this
	}
	
	def assignment(assignment:Assignment) = {
		map += "assignment" -> assignment.id
		if (assignment.module != null) module(assignment.module)
		this
	}
	
	def module(module:Module) = {
		map += "module" -> module.id
		this
	}
	
	// delegate equality to the underlying map
	override def hashCode = map.hashCode
	override def equals(that:Any) = that match {
		case d:Description => map.equals(d.map)
		case _ => false
	}
}

class DescriptionImpl extends Description {
	def allProperties = map
}