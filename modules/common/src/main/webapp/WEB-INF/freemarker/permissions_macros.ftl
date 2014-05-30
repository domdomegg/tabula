<#ftl strip_text=true />

<#escape x as x?html>
<#assign f=JspTaglibs["/WEB-INF/tld/spring-form.tld"]>

<#macro alerts commandName scope users="" role="">
	<#local bindingError><@f.errors path="${commandName}.*" /></#local>
	<#if bindingError?has_content>
		<p class="alert alert-error">
			<button type="button" class="close" data-dismiss="alert">&times;</button>
			<i class="icon-warning-sign"></i> <#noescape>${bindingError}</#noescape>
		</p>
	</#if>

	<#if users?has_content && role?has_content>
		<div id="permissionsMessage" class="alert alert-success">
			<button type="button" class="close" data-dismiss="alert">&times;</button>
			<p><i class="icon-ok"></i>
				<#list users?keys as key>
					<strong>${users[key].getFullName()}</strong> <#if users[key].getFullName()!=""> (${key})</#if>
				</#list>
				<#if action = "add">
					<#local actionWords = "now" />
					<#else>
					<#local actionWords = "no longer" />
				</#if>

				<#if users?size gt 1>
				  <br />
					are ${actionWords} <@fmt.role_definition_description role />s for
				<#else>
					is  ${actionWords} a <@fmt.role_definition_description role /> for
				</#if>
			<#noescape>${scope}</#noescape></p>
		</div>
	</#if>
</#macro>

<#macro roleTable permsUrl cssClass scope roleDefinition roleNamePlural>
<@manageRole scope=scope roleName=roleDefinition>
	<table class="table table-bordered table-subtle table-condensed permission-list ${cssClass}">
		<tbody>
			<tr>
				<td>
					<div class="form-inline">
						<@form.flexipicker cssClass="pickedUser" name="usercodes" />
					</div>
				</td>
				<td class="actions">
					<#if can_delegate>
						<form action="${permsUrl}" method="post" class="add-permissions">
							<input type="hidden" name="_command" value="add">
							<input type="hidden" name="roleDefinition" value="${roleDefinition}">
							<input type="hidden" name="usercodes">
							<button class="btn btn-mini" type="submit"><i class="icon-plus"></i></button>
						</form>
					<#else>
						<#local popoverText>
							<p>You can't add new ${roleNamePlural} because you don't have permission to:</p>
							<ul>
								<#list denied_permissions as perm>
									<li>${perm.description}</li>
								</#list>
							</ul>
							<p>on ${scope.toString}.</p>
						</#local>
					
						<button class="btn btn-mini use-tooltip disabled" type="button" 
										data-html="true"
										data-title="${popoverText}"><i class="icon-plus"></i></button>
					</#if>
				</td>
			</tr>

			<#local users = usersWithRole('${roleDefinition}', scope) />
			<#if users?size gt 0>
				<#list users as u>
					<tr>
						<td class="user">
							${u.fullName} <span class="muted">${u.userId}</span>
						</td>
						<td class="actions">
							<#if can_delegate>
								<form action="${permsUrl}" method="post" class="remove-permissions" onsubmit="return confirm('Are you sure you want to remove permission for this user?');">
									<input type="hidden" name="_command" value="remove">
									<input type="hidden" name="roleDefinition" value="${roleDefinition}">
									<input type="hidden" name="usercodes" value="${u.userId}">
									<a class="btn btn-danger btn-mini removeUser"><i class="icon-white icon-remove"></i></a>
								</form>
							<#else>
								<#local popoverText>
									<p>You can't remove ${roleNamePlural} because you don't have permission to:</p>
									<ul>
										<#list denied_permissions as perm>
											<li>${perm.description}</li>
										</#list>
									</ul>
									<p>on ${scope.toString}.</p>
								</#local>
							
								<button class="btn btn-danger btn-mini use-tooltip disabled" type="button" 
												data-html="true"
												data-title="${popoverText}"><i class="icon-white icon-remove"></i></button>
							</#if>
						</td>
					</tr>
				</#list>
			<#else>
				<tr>
					<td colspan="2" class="empty-list">
						There are no ${roleNamePlural} yet.
					</td>
				</tr>
			</#if>
		</tbody>
	</table>
</@manageRole>
</#macro>

<#macro script>
<script>
	jQuery(function($) {
		$('.removeUser').click(function() {
			$(this).parent("form").submit();
		});

		// copy to hidden field to avoid breaking table/form DOM hierarchy
		$('input.pickedUser').change(function() {
			$(this).closest('table').find('.add-permissions input[name=usercodes]').val($(this).val());
		});

		$('.removeUser').hover(function() {
				$(this).closest("tr").find("td").addClass("highlight");
			}, function() {
				$(this).closest("tr").find("td").removeClass("highlight");
			}
		);
	});
</script>
</#macro>

<#macro debugPermission permission scope={} showScopes=true>
	<#local isTarget=permissionsHelperCommand?? && permissionsHelperCommand.permission?? && (permission.name == permissionsHelperCommand.permission.name && (scope?size == 0 || (scope.id == results.resolvedScope.id)))>

	<#if isTarget><strong class="text-success"></#if>

	<span class="permission"><i class="icon-lock use-tooltip" title="${permission.name}"></i> ${permission.description}</span>
	<#if showScopes && scope?? && scope?size != 0>
		on <span class="scope"><i class="icon-bookmark"></i> ${scope.toString}</span>
	<#elseif showScopes && permission.scoped>
		<i class="icon-globe use-tooltip" title="Granted against any scope" data-placement="right"></i>
	</#if>

	<#if isTarget></strong></#if>
</#macro>

<#macro debugRole role showScopes=true>
<span class="role"><i class="icon-user"></i> ${role.definition.description}</span><#if showScopes && role.scope??> on <span class="scope"><i class="icon-bookmark"></i> ${role.scope.toString}</span></#if>
	<#if role.explicitPermissions?size gt 0 || role.subRoles?size gt 0>
	<ul>
		<#list role.subRoles as subRole>
			<li><@debugRole role=subRole showScopes=showScopes /></li>
		</#list>
		<#list role.explicitPermissionsAsList as permission>
			<li><@debugPermission permission=permission._1() scope=permission._2() showScopes=showScopes /></li>
		</#list>
	</ul>
	</#if>
</#macro>

</#escape>
