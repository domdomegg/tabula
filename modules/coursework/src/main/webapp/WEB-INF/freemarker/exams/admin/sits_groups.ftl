<#macro exams_sits_groups command >

	<#if command.availableUpstreamGroups?has_content>
	<div>
		<table id="sits-table" class="table table-bordered table-striped table-condensed table-hover table-sortable table-checkable sticky-table-headers tabula-orangeLight">
			<thead>
			<tr>
				<th class="for-check-all" style="width: 20px; padding-right: 0;"></th>
				<th class="sortable">Name</th>
				<th class="sortable">Members</th>
				<th class="sortable">Assessment group</th>
				<th class="sortable">CATS</th>
				<th class="sortable">Occurrence</th>
				<th class="sortable">Sequence</th>
				<th class="sortable">Type</th>
			</tr>
			</thead>
			<tbody>
				<#assign slectedIds = filterIds(command.upstreamGroups) >
				<#list command.availableUpstreamGroups as available>
					<#local isLinked = available.isLinked(command.assessmentGroups) />
				<#-- <@f.hidden id="upstreamGroups${available_index}" name="upstreamGroups[${available_index}]"  value="${available.id}" cssClass="upstreamGroups" /> -->
				<tr>
					<td>
						<input
							type="checkbox"
							id="chk-${available.id}"
							name=""
							value="${available.id}" class="upstreamGroups"
							${slectedIds?seq_contains(available.id)?string(" checked","")}
						>
					</td>

					<td><label for="chk-${available.id}">${available.name}<#if isLinked> <span class="label label-success">Linked</span></#if></label></td>
					<td>${available.memberCount}</td>
					<td>${available.group.assessmentGroup}</td>
					<td>${available.cats!'-'}</td>
					<td>${available.occurrence}</td>
					<td>${available.sequence}</td>
					<td>${available.assessmentType!'A'}</td>
				</tr>
				</#list>
			</tbody>
		</table>
	</div>

	<#else>
	<div class="modal-body">
		<p class="alert alert-warning">No SITS membership groups for ${command.module.code?upper_case} are available</p>
	</div>
	</#if>
</#macro>

<#function filterIds things>
	<#local result = []>
	<#list things as thing>
		<#local result = result + [thing.id]>
	</#list>
	<#return result>
</#function>


