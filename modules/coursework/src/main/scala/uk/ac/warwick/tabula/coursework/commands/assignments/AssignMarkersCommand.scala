package uk.ac.warwick.tabula.coursework.commands.assignments

import scala.collection.JavaConversions._
import scala.collection.JavaConverters._
import uk.ac.warwick.tabula.commands.{Description, Command}
import uk.ac.warwick.tabula.data.model.{UserGroup, Module, Assignment}
import uk.ac.warwick.tabula.data.{SessionComponent, Daoisms}
import uk.ac.warwick.tabula.data.Transactions._
import uk.ac.warwick.spring.Wire
import uk.ac.warwick.tabula.services.{UserLookupService, AssignmentService}
import uk.ac.warwick.tabula.permissions.Permissions
import uk.ac.warwick.userlookup.User
import uk.ac.warwick.tabula.services.AssignmentMembershipService
import scala.beans.BeanProperty
import org.apache.commons.collections.FactoryUtils
import org.apache.commons.collections.list.LazyList

class AssignMarkersCommand(module: Module, assignment:Assignment)
	extends AbstractAssignMarkersCommand(module, assignment)
	with Daoisms

abstract class AbstractAssignMarkersCommand(val module: Module, val assignment:Assignment) extends Command[Assignment] {
	// declare dependencies through self-type
	self: SessionComponent =>

	case class Marker(fullName:String, userCode:String, var students:JList[Student])
	case class Student(displayValue: String, userCode: String)

	PermissionCheck(Permissions.Assignment.Update, assignment)

	var assignmentService = Wire[AssignmentService]("assignmentService")
	var assignmentMembershipService = Wire[AssignmentMembershipService]("assignmentMembershipService")
	var userLookup = Wire.auto[UserLookupService]

	var firstMarkerUnassignedStudents: JList[Student] = _
	var secondMarkerUnassignedStudents: JList[Student] = _
	var firstMarkers: JList[Marker] = _
	var secondMarkers: JList[Marker] = _

	val allMarkers = assignment.markingWorkflow.firstMarkers.members ++ assignment.markingWorkflow.secondMarkers.members

	var markerMapping : JMap[String, JList[String]] = allMarkers.map({ x =>
			val myList : JList[String] = JArrayList()
			(x, myList)
	}).toMap.asJava


	def onBind() {
		def retrieveMarkers(markerDef:Seq[String]): JList[Marker] = markerDef.map{marker =>
			val students:JList[Student] = assignment.markerMap.toMap.get(marker) match {
				case Some(userGroup:UserGroup) => userGroup.users.map{student =>
					val displayValue = module.department.showStudentName match {
						case true => student.getFullName
						case false => student.getWarwickId
					}
					new Student(displayValue, student.getUserId)
				}
				case None => JArrayList()
			}
			val user = Option(userLookup.getUserByUserId(marker))
			val fullName = user match{
				case Some(u) => u.getFullName
				case None => ""
			}

			new Marker(fullName, marker, students)
		}

		firstMarkers = retrieveMarkers(assignment.markingWorkflow.firstMarkers.members)
		secondMarkers = retrieveMarkers(assignment.markingWorkflow.secondMarkers.members)
		val members = assignmentMembershipService.determineMembershipUsers(assignment).map{s =>
			val displayValue = module.department.showStudentName match {
				case true => s.getFullName
				case false => s.getWarwickId
			}
			new Student(displayValue, s.getUserId)
		}

		firstMarkerUnassignedStudents = members.toList filterNot firstMarkers.map(_.students).flatten.contains
		secondMarkerUnassignedStudents = members.toList filterNot secondMarkers.map(_.students).flatten.contains

		markerMapping = new java.util.HashMap[String, JList[String]]()
		for (marker <- (firstMarkers.toList ++ secondMarkers.toList)){
			markerMapping.put(marker.userCode, marker.students.map(_.userCode))
		}
	}

	def applyInternal() = {
		transactional(){
			assignment.markerMap = new java.util.HashMap[String, UserGroup]()
			if(Option(markerMapping).isDefined){
				markerMapping.foreach{case(marker, studentList) =>
					val group = UserGroup.ofUsercodes
					group.includedUserIds = studentList.asScala
					session.saveOrUpdate(group)
					assignment.markerMap.put(marker, group)
				}
			}
			session.saveOrUpdate(assignment)
			assignment
		}
	}

	def describe(d: Description) {
		d.assignment(assignment)
	}
}
