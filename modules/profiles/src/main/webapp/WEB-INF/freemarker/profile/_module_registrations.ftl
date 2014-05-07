<section id="module-registrations" class="clearfix">
	<div class="pull-right">
		<@routes.mrm_link studentCourseDetails studentCourseYearDetails />
		View in MRM<img class="targetBlank" alt="" title="Link opens in a new window" src="/static/images/shim.gif"/> </a>
	</div>
	
	<h4>Modules</h4>
	<p><span class="muted">Module Registration Status:</span>
		<#if studentCourseYearDetails.moduleRegistrationStatus??>
			${(studentCourseYearDetails.moduleRegistrationStatus.description)!}
		<#else>
			Unknown (not in SITS)
		</#if>
	</p>
	<table class="module-registration-table">
		<tbody>
			<tr>
				<th>Code</th>
				<th>Title</th>
				<th>CATS</th>
				<#if studentCourseYearDetails.latest>
					<th>Assess</th>
					<#if studentCourseYearDetails.hasModuleRegistrationWithNonStandardOccurrence>
						<th>Occur</th>
					</#if>
				<#elseif features.showModuleResults>
					<th>Mark</th>
					<th>Grade</th>
				</#if>
				<th>Status</th>
			</tr>
			<#list studentCourseYearDetails.moduleRegistrations as moduleRegistration>
				<tr>
					<td>${(moduleRegistration.module.code?upper_case)!}</td>
					<td>${(moduleRegistration.module.name)!}</td>
					<td>${(moduleRegistration.cats)!}</td>

					<#if studentCourseYearDetails.latest>
						<td>${(moduleRegistration.assessmentGroup)!}</td>
						<#if studentCourseYearDetails.hasModuleRegistrationWithNonStandardOccurrence>
							<td>${(moduleRegistration.occurrence)!}</td>
						</#if>
					<#elseif features.showModuleResults>
						<td>${(moduleRegistration.agreedMark)!}</td>
						<td>${(moduleRegistration.agreedGrade)!}</td>
					</#if>

					<td>
						<#if moduleRegistration.selectionStatus??>
							${(moduleRegistration.selectionStatus.description)!}
						<#else>
							-
						</#if>
					</td>
				</tr>
			</#list>
		</tbody>
	</table>

</section>
