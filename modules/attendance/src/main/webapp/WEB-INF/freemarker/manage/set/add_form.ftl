<#escape x as x?html>

<h1>Create monitoring schemes</h1>

<form id="addMonitoringPointSet" action="<@routes.createSet command.dept command.academicYear />" method="POST" class="modify-monitoring-points">

	<input type="hidden" name="createType" value="${createType}" />

	<div class="routeAndYearPicker">
		<div class="row-fluid">
			<div class="span2">
				<h2>Students</h2>
			</div>
			<div class="span10">
				<span class="hint">
					Create monitoring schemes for the following students for Academic year ${command.academicYear.toString}
				</span>
			</div>
		</div>

		<div class="row-fluid">
			<div class="span2">
				<h3>Route and year of study</h3>
			</div>
			<div class="span10">
				<p class="collapsible">
					<i class="icon-fixed-width icon-chevron-right"></i>
					There <span class="routes-count">are no routes</span> selected <span class="expand-button"><a class="btn btn-primary">Add routes</a></span>
				</p>
				<#assign yearList = ["1","2","3","4","5","6","7","8","All"] />
				<div class="collapsible-target">
					<table class="table table-bordered table-striped table-condensed table-hover header">
						<thead>
							<tr>
								<th class="ellipsis">Route</th>
								<th colspan="9">Year of study</th>
							</tr>
							<tr class="years">
								<th></th>
								<#list yearList as year>
									<th class="year_${year}" data-year="${year}">${year}</th>
								</#list>
							</tr>
						</thead>
					</table>
					<div class="scroller">
						<table class="table table-bordered table-striped table-condensed table-hover">
							<tbody>
								<#list command.availableRoutes as route>
									<#assign availableYearsForRoute = command.availableYears[route.code]/>
									<tr>
										<#assign routeName><@fmt.route_name route /></#assign>
										<td class="ellipsis" title="${routeName?trim}">
											${routeName?trim}
										</td>
										<#list yearList as year>
											<#assign checked = ""/>
											<#if command.selectedRoutesAndYearsByRouteCode(route)[year] && availableYearsForRoute[year]>
												<#assign checked = "checked"/>
											</#if>
											<td class="year_${year}">
												<#if availableYearsForRoute[year]>
													<input ${checked} data-year="${year}" type="checkbox" name="selectedRoutesAndYears[${route.code}][${year}]" value="true" />
												<#else>
													<input data-year="${year}" type="checkbox" name="selectedRoutesAndYears[${route.code}][${year}]" value="false" disabled title="Unavailable"/>
												</#if>
											</td>
										</#list>
									</tr>
								</#list>
							</tbody>
						</table>
					</div>
				</div>

				<@spring.bind path="command.selectedRoutesAndYears">
					<#if status.error>
						<div class="alert alert-error"><@f.errors path="command.selectedRoutesAndYears" cssClass="error"/></div>
					</#if>
				</@spring.bind>
			</div>
		</div>
	</div>

	<hr />

	<div class="row-fluid">
		<div class="span4">
			<h2>Monitoring points</h2>
		</div>
		<div class="span8">
			<a href="<@routes.addPoint command.dept />?form=true" class="btn btn-primary new-point"><i class="icon-plus"></i> Create new point</a>
		</div>
	</div>

	<#include "_monitoringPoints.ftl" />

	<input type="submit" value="Create" class="btn btn-primary"/> <a class="btn" href="<@routes.manageDepartment command.dept />">Cancel</a>

</form>

<div id="modal" class="modal hide fade" style="display:none;"></div>

</#escape>