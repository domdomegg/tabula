<#escape x as x?html>
	<#if !command.point.pointType?? || command.point.pointType.dbValue != "meeting">
		<div class="alert alert-error">
			Specified monitoring point is not a Meeting point
		</div>
	<#else>

		<p>
			Monitoring point requires ${command.point.meetingQuantity}
			<#if command.point.meetingFormats?size == allMeetingFormats?size>
				meeting of any format
			<#else>
				<#list command.point.meetingFormats as format>
					${format.description}<#if format_has_next> or </#if>
				</#list>
			</#if>
			with the student's
			<#list command.point.meetingRelationships as relationship>
			${relationship.description}<#if relationship_has_next> or </#if>
			</#list>
			<#if command.point.validFromWeek == command.point.requiredFromWeek>
				in <@fmt.monitoringPointWeeksFormat command.point.validFromWeek command.point.requiredFromWeek command.point.pointSet.academicYear command.point.pointSet.route.department />
			<#else>
				between
				<@fmt.monitoringPointWeeksFormat command.point.validFromWeek command.point.validFromWeek command.point.pointSet.academicYear command.point.pointSet.route.department />
				and
				<@fmt.monitoringPointWeeksFormat command.point.requiredFromWeek command.point.requiredFromWeek command.point.pointSet.academicYear command.point.pointSet.route.department />
			</#if>

		</p>

		<#if meetingsStatuses?size == 0>
			<p><em>There were no meetings found.</em></p>
		<#else>

			<table class="table table-bordered table-condensed table-hover">
				<thead>
					<tr>
						<th>Meeting</th>
						<th>Status</th>
					</tr>
				</thead>
				<tbody>
					<#list meetingsStatuses as meetingStatus>
						<#assign meeting = meetingStatus._1() />
						<#assign reasons = meetingStatus._2() />
						<tr>
							<td>
								${(meeting.format.description)!"Unknown format"} with
								${meeting.relationship.relationshipType.agentRole}
								on <@fmt.date date=meeting.meetingDate includeTime=false/>
								created <@fmt.date meeting.lastUpdatedDate />
							</td>
							<td>
								<#if reasons?size == 0>
									<i class="icon-fixed-width icon-ok"></i>
								<#else>
									<#assign popoverContent>
										<#list reasons as reason>
											<#if reason == "Took place before">
												Took place before <@fmt.monitoringPointWeeksFormat command.point.validFromWeek command.point.validFromWeek command.point.pointSet.academicYear command.point.pointSet.route.department />
											<#elseif reason == "Took place after">
												Took place after <@fmt.monitoringPointWeeksFormat command.point.requiredFromWeek command.point.requiredFromWeek command.point.pointSet.academicYear command.point.pointSet.route.department />
											<#else>
												${reason}
											</#if>
											<#if reason_has_next><br /></#if>
										</#list>
									</#assign>
									<a class="use-popover" id="popover-meeting-status-${meetingStatus_index}" data-placement="left" data-html="true" data-content="${popoverContent}">
										<i class="icon-fixed-width icon-remove"></i>
									</a>
								</#if>
							</td>
						</tr>
					</#list>
				</tbody>
			</table>

		</#if>
	</#if>
</#escape>