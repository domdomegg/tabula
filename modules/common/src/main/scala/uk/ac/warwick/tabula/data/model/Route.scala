package uk.ac.warwick.tabula.data.model

import org.hibernate.annotations.{BatchSize, Type}
import javax.persistence._
import uk.ac.warwick.tabula.permissions.PermissionsTarget
import uk.ac.warwick.tabula.JavaImports._
import uk.ac.warwick.tabula.data.model.attendance.MonitoringPointSet

@Entity
@NamedQueries(Array(
	new NamedQuery(name = "route.code", query = "select r from Route r where code = :code"),
	new NamedQuery(name = "route.department", query = "select r from Route r where department = :department")))
class Route extends GeneratedId with Serializable with PermissionsTarget {

	def this(code: String = null, department: Department = null) {
		this()
		this.code = code
		this.department = department
	}

	@Column(unique=true)
	var code: String = _

	var name: String = _

	@ManyToOne
	@JoinColumn(name = "department_id")
	var department: Department = _

	@Type(`type` = "uk.ac.warwick.tabula.data.model.DegreeTypeUserType")
	var degreeType: DegreeType = _

	var active: Boolean = _

	override def toString = "Route[" + code + "]"
	
	def permissionsParents = Stream(department)

	@OneToMany(mappedBy="route", fetch = FetchType.LAZY)
	@BatchSize(size=100)
	var monitoringPointSets: JList[MonitoringPointSet] = JArrayList()

}

trait HasRoute {
	def route: Route
}

object Route {
	// For sorting a collection by route code. Either pass to the sort function,
	// or expose as an implicit val.
	val CodeOrdering: Ordering[Route] = Ordering.by[Route, String] ( _.code )
	val NameOrdering: Ordering[Route] = Ordering.by { route: Route => (route.name, route.code) }
	val DegreeTypeOrdering: Ordering[Route] = Ordering.by { route: Route => (Option(route.degreeType), route.code) }

	// Companion object is one of the places searched for an implicit Ordering, so
	// this will be the default when ordering a list of routes.
	implicit val defaultOrdering = CodeOrdering
}