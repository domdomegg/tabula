<#escape x as x?html>

<div class="btn-toolbar dept-toolbar">
	<div class="btn-group dept-settings">
		<a class="btn btn-medium dropdown-toggle" data-toggle="dropdown" href="#">
			<i class="icon-calendar"></i>
			${academicYear.label}
			<span class="caret"></span>
		</a>
		<ul class="dropdown-menu pull-right">
			<li><a href="<@routes.viewDepartment department />"><#if academicYear.startYear == 2013><strong>13/14</strong><#else>13/14</#if></a></li>
			<#if features.attendanceMonitoringAcademicYear2014>
				<li><a href="<@routes.viewHomeForYear department '2014' />"><#if academicYear.startYear == 2014><strong>14/15</strong><#else>14/15</#if></a></li>
			</#if>
			<#if features.attendanceMonitoringAcademicYear2015>
				<li><a href="<@routes.viewHomeForYear department '2015' />"><#if academicYear.startYear == 2015><strong>15/16</strong><#else>15/16</#if></a></li>
			</#if>
		</ul>
	</div>
</div>

<#macro deptheaderroutemacro dept>
	<@routes.viewHomeForYear dept academicYear.startYear?c />
</#macro>
<#assign deptheaderroute = deptheaderroutemacro in routes/>
<@fmt.deptheader "View and record attendance for ${academicYear.toString}" "in" department routes "deptheaderroute" />

<#if hasSchemes>
	<h3><a href="<@routes.viewStudents department academicYear.startYear?c />">View by student and report to SITS:eVision</a></h3>
	<h3><a href="<@routes.viewPoints department academicYear.startYear?c />">View by point</a></h3>
	<#if can.do("MonitoringPoints.View", department)>
		<#list department.displayedStudentRelationshipTypes as relationshipType>
		<h3><a href="<@routes.viewAgents department academicYear.startYear?c relationshipType />">View by ${relationshipType.agentRole}</a></h3>
		</#list>
	</#if>
<#else>
	<p class="alert alert-information"><i class="icon-info-sign"></i> There are no monitoring point schemes for this department for this academic year.</p>
</#if>

</#escape>