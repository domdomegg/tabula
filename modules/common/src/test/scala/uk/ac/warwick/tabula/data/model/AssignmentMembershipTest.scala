package uk.ac.warwick.tabula.data.model

import uk.ac.warwick.tabula.{TestBase, Mockito}
import uk.ac.warwick.tabula.services.UserLookupService
import uk.ac.warwick.tabula.services.{AssignmentMembershipServiceImpl, AssignmentMembershipService}
import uk.ac.warwick.userlookup.User
import org.junit.Before
import uk.ac.warwick.userlookup.AnonymousUser
import uk.ac.warwick.tabula.services.IncludeType
import uk.ac.warwick.tabula.services.SitsType
import uk.ac.warwick.tabula.services.ExcludeType
import scala.collection.JavaConverters._
import uk.ac.warwick.tabula.JavaImports.{JHashMap,JList}

class AssignmentMembershipTest extends TestBase with Mockito {

	var userDatabase = Seq(
		("0000000","aaaaa"),
		("0000001","aaaab"),
		("0000002","aaaac"),
		("0000003","aaaad"),
		("0000004","aaaae"),
		("0000005","aaaaf"),
		("0000006","aaaag"),
		("0000007","aaaah")
	) map { case(id,code) =>
		val user = new User(code)
		user.setWarwickId(id)
		user.setFullName("Roger " + code.head.toUpper + code.tail)
		user
	}

  var assignmentMembershipService: AssignmentMembershipService = _
	var userLookup: UserLookupService = _
	val nobody = UserGroup.ofUsercodes

	@Before def before {
		userLookup = mock[UserLookupService]
		userLookup.getUserByUserId(any[String]) answers { id =>
			userDatabase find {_.getUserId == id} getOrElse (new AnonymousUser())			
		}
		userLookup.getUserByWarwickUniId(any[String]) answers { id =>
			userDatabase find {_.getWarwickId == id} getOrElse (new AnonymousUser())
		}
		userLookup.getUsersByUserIds(any[JList[String]]) answers { case ids:JList[String @unchecked] =>
			val users = ids.asScala.map(id=>(id,userDatabase find {_.getUserId == id} getOrElse (new AnonymousUser())))
			JHashMap(users:_*)
		}
		userLookup.getUsersByWarwickUniIds(any[Seq[String]]) answers { case ids: Seq[String @unchecked] => 
			ids.map(id => (id, userDatabase.find {_.getWarwickId == id}.getOrElse (new AnonymousUser()))).toMap
		}
    assignmentMembershipService = {
      val s = new AssignmentMembershipServiceImpl
      s.userLookup = userLookup
      s
    }
	}
	
	@Test def empty {
		val membership = assignmentMembershipService.determineMembership(Nil, Option(nobody)).items
		membership.size should be (0)
	}
	
	@Test def emptyWithNone {
		val membership = assignmentMembershipService.determineMembership(Nil, None).items
		membership.size should be (0)
	}
	
	@Test def plainSits {
		val upstream = newAssessmentGroup(Seq("0000005","0000006"))
		val membership = assignmentMembershipService.determineMembership(Seq(upstream), Option(nobody)).items
		membership.size should be (2)
		membership(0).user.getFullName should be ("Roger Aaaaf")
		membership(1).user.getFullName should be ("Roger Aaaag")
	}
	
	@Test def plainSitsWithNone {
		val upstream = newAssessmentGroup(Seq("0000005","0000006"))
		val membership = assignmentMembershipService.determineMembership(Seq(upstream), None).items
		membership.size should be (2)
		membership(0).user.getFullName should be ("Roger Aaaaf")
		membership(1).user.getFullName should be ("Roger Aaaag")
	}
	
	@Test def includeAndExclude {
		val upstream = newAssessmentGroup(Seq("0000005","0000006"))
		val others = UserGroup.ofUsercodes
		others.userLookup = userLookup
		others.includeUsers.add("aaaaa")
		others.excludeUsers.add("aaaaf")
		val membership = assignmentMembershipService.determineMembership(Seq(upstream), Option(others)).items

		membership.size should be (3)
		
		membership(0).user.getFullName should be ("Roger Aaaaa")
		membership(0).itemType should be (IncludeType)
		membership(0).itemTypeString should be ("include")
		membership(0).extraneous should be (false)
		
		membership(1).user.getFullName should be ("Roger Aaaaf")
		membership(1).itemType should be (ExcludeType)
		membership(1).itemTypeString should be ("exclude")
		membership(1).extraneous should be (false)
		
		membership(2).user.getFullName should be ("Roger Aaaag")
		membership(2).itemType should be (SitsType)
		membership(2).itemTypeString should be ("sits")
		membership(2).extraneous should be (false)

    // test the simpler methods that return a list of Users

    val users = assignmentMembershipService.determineMembershipUsers(Seq(upstream), Option(others))
    users.size should be (2)
    users(0).getFullName should be ("Roger Aaaaa")
    users(1).getFullName should be ("Roger Aaaag")
	}
	
	/**
	 * Test that the "extraneous" flag is set because "aaaaf" is already
	 * part of the SITS group, and excluded code "aaaah" is not in the
	 * group anyway so the exclusion does nothing. 
	 */
	@Test def redundancy {
		val upstream = newAssessmentGroup(Seq("0000005", "0000006"))
		val others = UserGroup.ofUsercodes
		others.includeUsers.add("aaaaf")
		others.excludeUsers.add("aaaah")
		others.userLookup = userLookup
		val membership = assignmentMembershipService.determineMembership(Seq(upstream), Option(others)).items
		membership.size should be(3)

		membership(0).user.getFullName should be("Roger Aaaaf")
		membership(0).itemType should be(IncludeType)
		membership(0).itemTypeString should be("include")
		membership(0).extraneous should be(true)

		membership(1).user.getFullName should be("Roger Aaaah")
		membership(1).itemType should be(ExcludeType)
		membership(1).itemTypeString should be("exclude")
		membership(1).extraneous should be(true)

		membership(2).user.getFullName should be("Roger Aaaag")
		membership(2).itemType should be(SitsType)
		membership(2).itemTypeString should be("sits")
		membership(2).extraneous should be(false)
	}


	def newAssessmentGroup(uniIds:Seq[String]) = {
        val upstream = new UpstreamAssessmentGroup
        uniIds foreach upstream.members.addUser
        upstream
    }
}