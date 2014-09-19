package uk.ac.warwick.tabula.groups.web.controllers

import uk.ac.warwick.tabula.{TestBase, Mockito}
import uk.ac.warwick.tabula.groups.{SmallGroupBuilder, SmallGroupSetBuilder}
import uk.ac.warwick.tabula.data.model.groups.SmallGroupAllocationMethod.{Manual, StudentSignUp}
import uk.ac.warwick.userlookup.{UserLookup, User}
import uk.ac.warwick.tabula.groups.web.views.GroupsViewModel.{ViewerRole, StudentAssignedToGroup, StudentNotAssignedToGroup}
import uk.ac.warwick.tabula.data.model.UserGroup
import uk.ac.warwick.tabula.services.UserLookupService
import uk.ac.warwick.tabula.data.model.groups.{SmallGroup, SmallGroupSet}
import scala.collection.JavaConverters._
import uk.ac.warwick.userlookup.AnonymousUser

class HomeControllerTest extends TestBase with Mockito{

	import GroupsDisplayHelper._

	private trait Fixture{
		val userLookup = mock[UserLookupService]

		val unallocatedUser = new User
		unallocatedUser.setWarwickId("unallocated")
		unallocatedUser.setUserId("unallocated")

		val allocatedUser = new User
		allocatedUser.setWarwickId("allocated")
		allocatedUser.setUserId("allocated")
		
		val userDatabase = Seq(unallocatedUser, allocatedUser)
		userLookup.getUsersByWarwickUniIds(any[Seq[String]]) answers { case ids: Seq[String @unchecked] =>
			ids.map(id => (id, userDatabase.find {_.getWarwickId == id}.getOrElse (new AnonymousUser()))).toMap
		}

		val group1 = new SmallGroupBuilder()
			.withStudents(createUserGroup(Seq(allocatedUser)))
			.build

		val group2 = new SmallGroupBuilder().build
		val group3 = new SmallGroupBuilder().build

		val groupSet = new SmallGroupSetBuilder()
			.withAllocationMethod(StudentSignUp)
			.withMembers(createUserGroup(Seq(allocatedUser,unallocatedUser)))
			.withGroups(Seq(group1, group2))
			.build
		val groupSet2 = new SmallGroupSetBuilder()
			.withAllocationMethod(StudentSignUp)
			.withMembers(createUserGroup(Seq(allocatedUser,unallocatedUser)))
			.withGroups(Seq(group3))
			.withModule(groupSet.module)
			.build

		def createUserGroup(users:Seq[User])= {
			val ug = UserGroup.ofUniversityIds
			ug.userLookup = userLookup
			users.foreach (ug.add)
			ug
		}

	}

	@Test
	def getGroupsToDisplayReturnsNilForNonSelfSignUpWithNoAllocations() { new Fixture{
		groupSet.allocationMethod= Manual
		getGroupsToDisplay(groupSet, unallocatedUser) should be ((Nil, StudentNotAssignedToGroup))
	}}

	@Test
	def getGroupsToDisplayReturnsAllocatedGroupsForNonSelfSignUpWithNoAllocations() { new Fixture{
		groupSet.allocationMethod= Manual
		getGroupsToDisplay(groupSet, unallocatedUser) should be ((Nil, StudentNotAssignedToGroup))
	}}

	@Test
	def getGroupstoDisplayReturnsAllGroupsForOpenSelfSignupWithNoAllocation(){new Fixture{
		groupSet.openForSignups = true
		groupSet.allocationMethod = StudentSignUp
		getGroupsToDisplay(groupSet, unallocatedUser) should be ((Seq(group1, group2), StudentNotAssignedToGroup))
	}}

	@Test
	def getGroupstoDisplayReturnsNilForClosedSelfSignupWithNoAllocation(){new Fixture{
		groupSet.openForSignups = false
		groupSet.allocationMethod = StudentSignUp
		getGroupsToDisplay(groupSet, unallocatedUser) should be ((Nil, StudentNotAssignedToGroup))
	}}

	@Test
	def getGroupsToDisplayReturnsAllocatedGroupForNonSelfSignUp(){new Fixture {
		groupSet.allocationMethod= Manual
		getGroupsToDisplay(groupSet, allocatedUser) should be ((Seq(group1), StudentAssignedToGroup))
	}}

	@Test
	def getGroupsToDisplayReturnsAllocatedGroupForSelfSignUp(){new Fixture {
		groupSet.allocationMethod= StudentSignUp
		getGroupsToDisplay(groupSet, allocatedUser) should be ((Seq(group1), StudentAssignedToGroup))
	}}

	@Test
	def getViewModulesRemovesModulesWithNoGroups(){new Fixture{
		// dummy getGroupsToDisplay that always returns an empty set
		def neverReturnGroups(set:SmallGroupSet):(Seq[SmallGroup],ViewerRole)  = (Nil,StudentNotAssignedToGroup )
		getViewModulesForStudent(Seq(groupSet), neverReturnGroups) should be (Nil)
	}}

	@Test
	def getManualGroupSetsReleasedToStudents(){new Fixture{
		groupSet.allocationMethod=Manual
		groupSet.releasedToStudents=false
		getGroupSetsReleasedToStudents(Seq(groupSet)) should be (Nil)
		groupSet.releasedToStudents=true
		getGroupSetsReleasedToStudents(Seq(groupSet)) should not be (Nil)

	}}

	@Test
	def getSelfSignUpGroupSetsReleasedToStudents(){new Fixture{
		groupSet.allocationMethod=StudentSignUp
		// self signup groups should be returned - we ignore the releasedToStudents flag
		groupSet.releasedToStudents=false
		getGroupSetsReleasedToStudents(Seq(groupSet)) should not be (Nil)
		groupSet.releasedToStudents=true
		getGroupSetsReleasedToStudents(Seq(groupSet)) should not be (Nil)
	}}

	@Test
	def getViewModulesConvertsModuleToViewModule(){new Fixture{
		// dummy getGroupsToDisplay that always returns all groups in the set
		def allGroups(set:SmallGroupSet):(Seq[SmallGroup],ViewerRole)  = (set.groups.asScala,StudentNotAssignedToGroup )
		val viewModules = getViewModulesForStudent(Seq(groupSet), allGroups)
		viewModules.size should be(1)
		viewModules.head.canManageGroups should be(false)
		val viewSets = viewModules.head.setItems
		viewSets.size should be(1)
		viewSets.head.groups should be (Seq(group1, group2).sorted)
	}}

	@Test
	def getViewModulesGroupsByModule(){new Fixture{
		// dummy getGroupsToDisplay that always returns all groups in the set
		def allGroups(set:SmallGroupSet):(Seq[SmallGroup],ViewerRole)  = (set.groups.asScala,StudentNotAssignedToGroup )

		// two groupsets, both for the same module
		val viewModules = getViewModulesForStudent(Seq(groupSet, groupSet2), allGroups)
		viewModules.size should be(1)
		val viewSets = viewModules.head.setItems
		viewSets.size should be(2)
		viewSets.head.groups should be (Seq(group1, group2).sorted)
		viewSets.tail.head.groups should be (Seq(group3))
	}}


}
