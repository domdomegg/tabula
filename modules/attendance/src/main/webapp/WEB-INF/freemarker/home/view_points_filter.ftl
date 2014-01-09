<#escape x as x?html>
<#import "../attendance_macros.ftl" as attendance_macros />

<h1>View monitoring points for ${command.department.name}</h1>

<div class="btn-toolbar dept-toolbar">
	<#if command.department.parent??>
		<a class="btn btn-medium use-tooltip" href="<@routes.viewDepartmentPoints command.department.parent />" data-container="body" title="${command.department.parent.name}">
			Parent department
		</a>
	</#if>

	<#if command.department.children?has_content>
		<div class="btn-group">
			<a class="btn btn-medium dropdown-toggle" data-toggle="dropdown" href="#">
				Subdepartments
				<span class="caret"></span>
			</a>
			<ul class="dropdown-menu pull-right">
				<#list command.department.children as child>
					<li><a href="<@routes.viewDepartmentPoints child />">${child.name}</a></li>
				</#list>
			</ul>
		</div>
	</#if>
</div>

<#if updatedMonitoringPoint??>
	<div class="alert alert-success">
		<button type="button" class="close" data-dismiss="alert">&times;</button>
		Attendance recorded for '${updatedMonitoringPoint.name}'
	</div>
</#if>

<#assign submitUrl><@routes.viewDepartmentPoints command.department /></#assign>
<@attendance_macros.academicYearSwitcher submitUrl command.academicYear command.thisAcademicYear />

<#assign filterCommand = command />
<#assign filterCommandName = "command" />
<#assign filterResultsPath = "/WEB-INF/freemarker/home/view_points_results.ftl" />
<#include "/WEB-INF/freemarker/filter_bar.ftl" />
</#escape>