<#compress><#escape x as x?html>

<#import "*/permissions_macros.ftl" as pm />
<#assign perms_url><@routes.admin.deptperms department/></#assign>

<div class="permissions-page">
	<div class="pull-right">
		<div><a class="btn" href="<@routes.admin.permissions department />"><i class="icon-lock"></i> Advanced</a></div>
		<br>
		<div class="pull-right"><a href="<@routes.admin.rolesDepartment department />"><strong>About roles</strong></a></div>
	</div>

	<@fmt.deptheader "Departmental permissions" "for" department routes.admin "deptperms" "with-settings" />

	<@pm.alerts "addCommand" department.name users role />

	<div class="row-fluid" id="tutors-supervisors-row">
		<div class="span6">
			<h3 class="permissionTitle">Senior tutors</h3> <a class="use-popover colour-h3" id="popover-seniortutors" data-html="true"
			   data-original-title="Senior tutors"
			   data-content="A senior tutor can see everything that a personal tutor can, for every student in the department."><i class="icon-question-sign"></i></a>

			<@pm.roleTable perms_url "tutor-table" department "StudentRelationshipAgentRoleDefinition(tutor)" "senior tutors" />
		</div>

		<div class="span6">
			<h3 class="permissionTitle">Senior supervisors</h3> <a class="use-popover colour-h3" id="popover-seniorsupervisors" data-html="true"
			   data-original-title="Senior supervisors"
			   data-content="A senior supervisor can see everything that a supervisor can, for every student in the department."><i class="icon-question-sign"></i></a>

			<@pm.roleTable perms_url "supervisor-table" department "StudentRelationshipAgentRoleDefinition(supervisor)" "senior supervisors" />
		</div>
	</div>
	<div class="row-fluid">
		<div class="span6">
			<h3 class="permissionTitle">Departmental administrators</h3> <a class="use-popover colour-h3" id="popover-deptadmins" data-html="true"
			   data-original-title="Departmental Administrators"
			   data-content="A departmental administrator can manage Modules, Marking workflows and Extension settings; and can assign tutors and supervisors."><i class="icon-question-sign"></i></a>

			<@pm.roleTable perms_url "admin-table" department "DepartmentalAdministratorRoleDefinition" "departmental administrators" />
		</div>

	</div>

	<#assign scope=department />

	<h2>Module roles (for all modules in the department)</h2>

	<#include "../modules/_roles.ftl" />

	<h2>Route roles (for all routes in the department)</h2>

	<#include "../routes/_roles.ftl" />
</div>

<@pm.script />

</#escape></#compress>