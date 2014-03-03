package uk.ac.warwick.tabula.commands.permissions

import uk.ac.warwick.tabula.TestBase
import uk.ac.warwick.tabula.Mockito
import uk.ac.warwick.tabula.services.permissions.{PermissionsServiceComponent, PermissionsService}
import uk.ac.warwick.tabula.Fixtures
import uk.ac.warwick.tabula.permissions.Permissions
import uk.ac.warwick.tabula.data.model.permissions.GrantedRole
import org.springframework.validation.BindException
import uk.ac.warwick.tabula.permissions.PermissionsTarget
import uk.ac.warwick.tabula.services.{SecurityServiceComponent, SecurityService}
import uk.ac.warwick.tabula.roles.BuiltInRoleDefinition
import uk.ac.warwick.tabula.JavaImports._
import uk.ac.warwick.tabula.data.model.Department
import uk.ac.warwick.tabula.commands.{Describable, SelfValidating, Appliable, DescriptionImpl}

class RevokeRoleCommandTest extends TestBase with Mockito {

	trait CommandTestSupport[A <: PermissionsTarget] extends RevokeRoleCommandState[A] with PermissionsServiceComponent with SecurityServiceComponent {
		val permissionsService = mock[PermissionsService]
		val securityService = mock[SecurityService]
	}

	// a role with a single permission to keep things simple
	val singlePermissionsRoleDefinition = new BuiltInRoleDefinition(){
		override val getName="test"
		override def description="test"
		GrantsScopedPermission(
			Permissions.Department.ArrangeModules)
		def canDelegateThisRolesPermissions: JBoolean = false
	}

	trait Fixture {
		val department = Fixtures.department("in", "IT Services")

		val command = new RevokeRoleCommandInternal(department) with CommandTestSupport[Department] with RevokeRoleCommandValidation
	}
	
	@Test def nonExistingRole { new Fixture {
		command.roleDefinition = singlePermissionsRoleDefinition
		command.usercodes.add("cuscav")
		command.usercodes.add("cusebr")

		command.permissionsService.getGrantedRole(department, singlePermissionsRoleDefinition) returns (None)
				
		// Doesn't blow up, just a no-op
		command.applyInternal() should be (null)

		there was no (command.permissionsService).saveOrUpdate(any[GrantedRole[_]])
	}}
	
	@Test def itWorksWithExisting { new Fixture {
		command.roleDefinition = singlePermissionsRoleDefinition
		command.usercodes.add("cuscav")
		command.usercodes.add("cusebr")
		
		val existing = GrantedRole(department, singlePermissionsRoleDefinition)
		existing.users.knownType.addUserId("cuscao")
		existing.users.knownType.addUserId("cuscav")
		existing.users.knownType.addUserId("cusebr")

		command.permissionsService.getGrantedRole(department, singlePermissionsRoleDefinition) returns (Some(existing))
				
		val grantedPerm = command.applyInternal()
		(grantedPerm.eq(existing)) should be (true)
		
		grantedPerm.roleDefinition should be (singlePermissionsRoleDefinition)
		grantedPerm.users.size should be (1)
		grantedPerm.users.knownType.includesUserId("cuscav") should be (false)
		grantedPerm.users.knownType.includesUserId("cusebr") should be (false)
		grantedPerm.users.knownType.includesUserId("cuscao") should be (true)
		grantedPerm.scope should be (department)

		there was one (command.permissionsService).saveOrUpdate(existing)
	}}
	
	@Test def validatePasses { withUser("cuscav", "0672089") { new Fixture {
		command.roleDefinition = singlePermissionsRoleDefinition
		command.usercodes.add("cuscav")
		command.usercodes.add("cusebr")
		
		val existing = GrantedRole(department, singlePermissionsRoleDefinition)
		existing.users.knownType.addUserId("cuscao")
		existing.users.knownType.addUserId("cusebr")
		existing.users.knownType.addUserId("cuscav")
		
		command.permissionsService.getGrantedRole(department, singlePermissionsRoleDefinition) returns (Some(existing))
		command.securityService.canDelegate(currentUser, Permissions.Department.ArrangeModules, department) returns true
		
		val errors = new BindException(command, "command")
		command.validate(errors)

		errors.hasErrors should be (false)
	}}}
	
	@Test def noUsercodes { withUser("cuscav", "0672089") { new Fixture {
		command.roleDefinition = singlePermissionsRoleDefinition
		
		val existing = GrantedRole(department, singlePermissionsRoleDefinition)
		existing.users.knownType.addUserId("cuscao")
		existing.users.knownType.addUserId("cusebr")

		command.permissionsService.getGrantedRole(department, singlePermissionsRoleDefinition) returns (Some(existing))
		command.securityService.canDelegate(currentUser,Permissions.Department.ArrangeModules, department) returns true
		
		val errors = new BindException(command, "command")
		command.validate(errors)

		errors.hasErrors should be (true)
		errors.getErrorCount should be (1)
		errors.getFieldError.getField should be ("usercodes")
		errors.getFieldError.getCode should be ("NotEmpty")
	}}}
	
	@Test def usercodeNotInGroup { withUser("cuscav", "0672089") { new Fixture {
		command.roleDefinition = singlePermissionsRoleDefinition
		command.usercodes.add("cuscav")
		command.usercodes.add("cuscao")
		
		val existing = GrantedRole(department, singlePermissionsRoleDefinition)
		existing.users.knownType.addUserId("cuscao")
		existing.users.knownType.addUserId("cusebr")

		command.permissionsService.getGrantedRole(department, singlePermissionsRoleDefinition) returns (Some(existing))
		command.securityService.canDelegate(currentUser,Permissions.Department.ArrangeModules, department) returns true
		
		val errors = new BindException(command, "command")
		command.validate(errors)

		errors.hasErrors should be (true)
		errors.getErrorCount should be (1)
		errors.getFieldError.getField should be ("usercodes")
		errors.getFieldError.getCode should be ("userId.notingroup")
	}}}
	
	@Test def noRole { withUser("cuscav", "0672089") { new Fixture {
		command.usercodes.add("cuscav")
		command.usercodes.add("cusebr")

		command.permissionsService.getGrantedRole(department, null) returns (None)
		
		val errors = new BindException(command, "command")
		command.validate(errors)

		errors.hasErrors should be (true)
		errors.getErrorCount should be (1)
		errors.getFieldError.getField should be ("roleDefinition")
		errors.getFieldError.getCode should be ("NotEmpty")
	}}}
	
	@Test def cantRevokeWhatYouDontHave { withUser("cuscav", "0672089") { new Fixture {
		command.roleDefinition = singlePermissionsRoleDefinition
		command.usercodes.add("cusebr")
		
		val existing = GrantedRole(department, singlePermissionsRoleDefinition)
		existing.users.knownType.addUserId("cuscao")
		existing.users.knownType.addUserId("cusebr")

		command.permissionsService.getGrantedRole(department, singlePermissionsRoleDefinition) returns (Some(existing))
		command.securityService.canDelegate(currentUser,Permissions.Department.ArrangeModules, department) returns false

		val errors = new BindException(command, "command")
		command.validate(errors)

		errors.hasErrors should be (true)
		errors.getErrorCount should be (1)
		errors.getFieldError.getField should be ("roleDefinition")
		errors.getFieldError.getCode should be ("permissions.cantRevokeWhatYouDontHave")
	}}}

	@Test
	def describe {
		val dept = Fixtures.department("in")
		dept.id = "dept-id"

		val command = new RevokeRoleCommandDescription[Department] with CommandTestSupport[Department] {
			val eventName: String = "test"

			val scope = dept
			val grantedRole = None
		}

		command.roleDefinition = singlePermissionsRoleDefinition
		command.usercodes.add("cuscav")
		command.usercodes.add("cusebr")

		val d = new DescriptionImpl
		command.describe(d)

		d.allProperties should be (Map(
			"scope" -> "Department[dept-id]",
			"usercodes" -> "cuscav,cusebr",
			"roleDefinition" -> "test"
		))
	}

	@Test def gluesEverythingTogether {
		val department = Fixtures.department("in")
		val command = RevokeRoleCommand(department)

		command should be (anInstanceOf[Appliable[GrantedRole[Department]]])
		command should be (anInstanceOf[RevokeRoleCommandState[Department]])
		command should be (anInstanceOf[RevokeRoleCommandPermissions])
		command should be (anInstanceOf[SelfValidating])
		command should be (anInstanceOf[RevokeRoleCommandValidation])
		command should be (anInstanceOf[Describable[GrantedRole[Department]]])
	}


}
