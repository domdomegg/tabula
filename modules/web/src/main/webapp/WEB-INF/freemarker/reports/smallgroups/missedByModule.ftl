<#escape x as x?html>
<#import "../reports_macros.ftl" as reports_macros />

<h1>Missed event attendance by module</h1>

<#assign reportUrl><@routes.reports.missedSmallGroupsByModule department academicYear /></#assign>
<@reports_macros.reportLoader reportUrl>
	<ul class="dropdown-menu">
		<li>
			<a href="#" data-href="<@routes.reports.missedSmallGroupsByModuleDownloadCsv department academicYear />">
				<i class="icon-table"></i> CSV
			</a>
		</li>
		<li>
			<a href="#" data-href="<@routes.reports.missedSmallGroupsByModuleDownloadXlsx department academicYear />">
				<i class="icon-list-alt"></i> Excel
			</a>
		</li>
		<li>
			<a href="#" data-href="<@routes.reports.missedSmallGroupsByModuleDownloadXml department academicYear />">
				<i class="icon-code"></i> XML
			</a>
		</li>
	</ul>
</@reports_macros.reportLoader>
<@reports_macros.smallGroupByModuleReportScript />

</#escape>