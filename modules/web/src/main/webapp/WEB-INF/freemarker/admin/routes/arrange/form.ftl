<#assign department=sortRoutesCommand.department />
<#escape x as x?html>

<#if saved??>
<div class="alert alert-success">
<a class="close" data-dismiss="alert">&times;</a>
<p>Changes saved.</p>
</div>
</#if>

<h1>Arrange routes for ${department.name}</h1>

<noscript>
<div class="alert">This page requires Javascript.</div>
</noscript>

<p>Drag routes by their <i class='icon-reorder fa fa-bars'></i> handles to move them between departments. To select multiple departments,
drag a box from one route name to another. You can also hold the <kbd class="keyboard-control-key">Ctrl</kbd> key and drag to add to a selection.</p>

<@spring.hasBindErrors name="sortRoutesCommand">
<#if errors.hasErrors()>
<div class="alert alert-error">
<h3>Some problems need fixing</h3>
<#if errors.hasGlobalErrors()>
	<#list errors.globalErrors as e>
		<div><@spring.message message=e /></div>
	</#list>
<#else>
	<div>See the errors below.</div>
</#if>
</div>
</#if>
</@spring.hasBindErrors>

<@f.form commandName="sortRoutesCommand" action="${url('/admin/department/${department.code}/sort-routes')}">
<div class="tabula-dnd">
	<#macro rots department routes>
		<div class="drag-target clearfix">
			<h1>${department.name}</h1>
			<ul class="drag-list full-width" data-bindpath="mapping[${department.code}]">
			<#list routes as route>
				<li class="label" title="${route.name}">
					${route.code?upper_case}
					<input type="hidden" name="mapping[${department.code}][${route_index}]" value="${route.id}" />
				</li>
			</#list>
			</ul>
		</div>
	</#macro>

	<#list sortRoutesCommand.departments as dept>
		<@rots dept sortRoutesCommand.mappingByCode[dept.code]![] />
	</#list>

	<input id="sort-routes-submit" class="btn btn-primary" type="submit" value="Save changes" />
</div>
</@f.form>

</#escape>