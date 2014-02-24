package uk.ac.warwick.tabula.data.model

import scala.collection.JavaConverters._
import javax.persistence._
import uk.ac.warwick.tabula.JavaImports._
import uk.ac.warwick.tabula.services.UserLookupService
import uk.ac.warwick.spring.Wire
import uk.ac.warwick.userlookup.User
import org.hibernate.annotations.AccessType
import uk.ac.warwick.tabula.helpers.StringUtils._

/**
 * Wherever a group of users is referenced in the app, it will be
 * stored as a UserGroup.
 *
 * A UserGroup can either be a totally internal list of users, or it
 * can use a webgroup as a base and then specify users to add and
 * users to exclude from that base.
 *
 * When a webgroup is used, it is a live view on the webgroup (other
 * than the included and excluded users and caching), so it will
 * change when the webgroup does (caches permitting).
 *
 * Depending on what the UserGroup is attached to, the UI might choose
 * not to allow a webgroup to be used, and only allow included users.
 * We might want to subclass UserGroup to make it a bit more explicit which
 * groups support Webgroups, and prevent invalid operations.
 *
 * Depending on context, the usercodes may be university IDs.
 */
@Entity
@AccessType("field")
class UserGroup private(val universityIds: Boolean) extends GeneratedId with UnspecifiedTypeUserGroup with KnownTypeUserGroup {

	/* For Hibernate xx */
	def this() { this(false) }

	@transient var userLookup = Wire.auto[UserLookupService]
	private def groupService = userLookup.getGroupService

	var baseWebgroup: String = _

	def baseWebgroupSize = groupService.getGroupInfo(baseWebgroup).getSize()

	@ElementCollection @Column(name = "usercode")
	@JoinTable(name = "UserGroupInclude", joinColumns = Array(
		new JoinColumn(name = "group_id", referencedColumnName = "id")))
	private val includeUsers: JList[String] = JArrayList()

	@ElementCollection @Column(name = "usercode")
	@JoinTable(name = "UserGroupStatic", joinColumns = Array(
		new JoinColumn(name = "group_id", referencedColumnName = "id")))
	private val staticIncludeUsers: JList[String] = JArrayList()

	@ElementCollection @Column(name = "usercode")
	@JoinTable(name = "UserGroupExclude", joinColumns = Array(
		new JoinColumn(name = "group_id", referencedColumnName = "id")))
	private val excludeUsers: JList[String] = JArrayList()

	def includedUserIds: Seq[String] = includeUsers.asScala
	def includedUserIds_=(userIds: Seq[String]) {
		includeUsers.clear()
		includeUsers.addAll(userIds.asJava)
	}

	def staticUserIds: Seq[String] = staticIncludeUsers.asScala
	def staticUserIds_=(userIds: Seq[String]) {
		staticIncludeUsers.clear()
		staticIncludeUsers.addAll(userIds.asJava)
	}

	def excludedUserIds: Seq[String] = excludeUsers.asScala
	def excludedUserIds_=(userIds: Seq[String]) {
		excludeUsers.clear()
		excludeUsers.addAll(userIds.asJava)
	}

	def add(user:User) = {
		addUserId(getIdFromUser(user))
	}
	def addUserId(user: String) {
		if (!includeUsers.contains(user) && user.hasText) {
			includeUsers.add(user)
		}
	}
	def removeUserId(user: String) = includeUsers.remove(user)

	def remove(user:User) = {
		removeUserId(getIdFromUser(user))
	}

	def excludeUserId(user: String) {
		if (!excludeUsers.contains(user) && user.hasText) {
			excludeUsers.add(user)
		}
	}
	def exclude(user:User)={
		excludeUserId(getIdFromUser(user))
	}
	def unexcludeUserId(user: String) = excludeUsers.remove(user)
  def unexclude(user:User)={
		unexcludeUserId(getIdFromUser(user))
	}

	/*
	 * Could implement as `members.contains(user)`
	 * but this is more efficient
	 */
	def includesUserId(user: String) =
		!(excludeUsers contains user) &&
			(
				(includeUsers contains user) ||
				(staticIncludeUsers contains user) ||
				(baseWebgroup != null && groupService.isUserInGroup(user, baseWebgroup)))

	def excludesUserId(user: String) = excludeUsers contains user

	def includesUser(user:User) = includesUserId(getIdFromUser(user))
	def excludesUser(user:User) = excludesUserId(getIdFromUser(user))

	def isEmpty = members.isEmpty
	def size = members.size

	def members: Seq[String] = allIncludedIds diff allExcludedIds
		
	def allIncludedIds: Seq[String] = (includeUsers.asScala.toSeq ++ staticIncludeUsers.asScala ++ webgroupMembers).distinct
	def allExcludedIds: Seq[String] = excludeUsers.asScala.toSeq

	private def getIdFromUser(user:User):String = {
		if (universityIds)
			user.getWarwickId
		else
			user.getUserId
	}
	private def getUsersFromIds(ids: Seq[String]): Seq[User] = ids match {
		case Nil => Nil
		case ids if universityIds => userLookup.getUsersByWarwickUniIds(ids).values.toSeq
		case ids => userLookup.getUsersByUserIds(ids.asJava).values.asScala.toSeq
	}

	def users: Seq[User] = getUsersFromIds(members)

	def excludes: Seq[User] = getUsersFromIds(excludeUsers.asScala)

	private def webgroupMembers: List[String] = baseWebgroup match {
		case webgroup: String => groupService.getUserCodesInGroup(webgroup).asScala.toList
		case _ => Nil
	}


	def copyFrom(otherGroup: UnspecifiedTypeUserGroup) {
		otherGroup match {
			case other:UserGroup=>{
				assert(this.universityIds == other.universityIds, "Can only copy from a group with same type of users")
				baseWebgroup = other.baseWebgroup
				includeUsers.clear()
				excludeUsers.clear()
				staticIncludeUsers.clear()
				includeUsers.addAll(other.includeUsers)
				excludeUsers.addAll(other.excludeUsers)
				staticIncludeUsers.addAll(other.staticIncludeUsers)
			}
			case _ => {
				assert(false, "Can only copy from one UserGroup to another")
			}
		}

	}

	def duplicate(): UserGroup = {
		val newGroup = new UserGroup(this.universityIds)
		newGroup.copyFrom(this)
		newGroup.userLookup = this.userLookup
		newGroup
	}

	def hasSameMembersAs(other:UnspecifiedTypeUserGroup):Boolean ={
		other match {
			case otherUg:UserGroup if otherUg.universityIds == this.universityIds=> (this.members == otherUg.members)
			case _ => this.users == other.users
		}
	}
	
	def knownType = this
}

object UserGroup {
	def ofUsercodes = new UserGroup(false)
	def ofUniversityIds = new UserGroup(true)
}

/**
 * A usergroup where the value of universityId is hidden from the caller.
 *
 * This means that callers can only add/remove Users, not UserIds/UniversityIds - and therefore they can't add the
 * wrong type of identifier.
 *
 */

trait UnspecifiedTypeUserGroup {
	/**
	 * @return All of the included users (includedUsers, staticUsers, and webgroup members), minus the excluded users
	 */
	def users: Seq[User]

	/**
	 * @return The explicitly excluded users
	 */
	def excludes: Seq[User]
	def add(user:User)
	def remove(user:User)
	def exclude(user:User)
	def unexclude(user:User)
	def size:Int
	def isEmpty:Boolean
  def includesUser(user:User):Boolean
  def excludesUser(user:User):Boolean

	/**
	 * @return true if the other.users() would return the same values as this.users(), else false
	 */
	def hasSameMembersAs(other:UnspecifiedTypeUserGroup): Boolean

	def copyFrom(otherGroup: UnspecifiedTypeUserGroup): Unit
	def duplicate(): UnspecifiedTypeUserGroup
	
	val universityIds: Boolean
	def knownType: KnownTypeUserGroup
}

trait KnownTypeUserGroup extends UnspecifiedTypeUserGroup {
	def allIncludedIds: Seq[String]
	def allExcludedIds: Seq[String]
	def members: Seq[String]

	def addUserId(userId: String)
	def removeUserId(userId: String)
	def excludeUserId(userId: String)
	def unexcludeUserId(userId: String)

	def staticUserIds: Seq[String]
	def staticUserIds_=(userIds: Seq[String])

	def includedUserIds: Seq[String]
	def includedUserIds_=(userIds: Seq[String])

	def excludedUserIds: Seq[String]
	def excludedUserIds_=(userIds: Seq[String])

	def includesUserId(userId: String): Boolean
	def excludesUserId(userId: String): Boolean
}