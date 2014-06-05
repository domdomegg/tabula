<#compress><#escape x as x?html>

<#import "/WEB-INF/freemarker/permissions_macros.ftl" as pm />
<#import "/WEB-INF/freemarker/formatters.ftl" as fmt />
<#assign perms_url><@routes.routeperms route /></#assign>
<#assign route_name><@fmt.route_name route /></#assign>

<div id="route-permissions-page">
	<div class="pull-right">
		<div><a class="btn" href="<@routes.permissions route />"><i class="icon-lock"></i> Advanced</a></div>
		<br>
		<div class="pull-right"><a href="<@routes.rolesDepartment department />"><strong>About roles</strong></a></div>
	</div>

	<h1 class="with-settings">Route permissions</h1>
	<h5><span class="muted">for</span> <#noescape>${route_name}</#noescape></h5>

	<@pm.alerts "addCommand" route_name users role />

	<#assign scope=route />
	<#include "_roles.ftl" />
</div>

<@pm.script />

</#escape></#compress>