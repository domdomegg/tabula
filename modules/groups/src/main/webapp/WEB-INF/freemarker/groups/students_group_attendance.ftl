<#import "*/group_components.ftl" as components />
<#escape x as x?html>
<div id="student-groups-view">
	<#assign student=member />	
	<#assign is_the_student=user.apparentUser.warwickId! == command.member.universityId />
	
	<#macro eventsInATerm term>
		<#local weekNumbers = mapGet(termWeeks, term) />
		<#local groupsMap = mapGet(terms, term) />
	
		<div class="item-info row-fluid term">
			<div class="span12">
				<h4>${term.termTypeAsString}</h4>
				<div class="row-fluid term">
					<table id="group_attendance_${term.termTypeAsString}" class="table table-striped table-bordered table-condensed attendance-table">
						<thead>
							<tr>
								<th class="sortable nowrap">
									<#-- TAB-1124 don't show Group header on gadget view -->
									<#if defaultExpand!false>
										Group
									</#if>
								</th>
								<#list (weekNumbers.minWeek)..(weekNumbers.maxWeek) as weekNumber>
									<th class="instance-date-header">
										<div class="instance-date">
											<#if member.homeDepartment?has_content>
												<@fmt.singleWeekFormat week=weekNumber academicYear=academicYear dept=member.homeDepartment short=!(defaultExpand!false) />
											</#if>
										</div>
									</th>
								</#list>
								<th class="sortable">
									<i title="Missed events" class="icon-remove icon-fixed-width unauthorised"></i>
								</th>
							</tr>
						</thead>
						<tbody>
							<#list groupsMap?keys as group>
								<#local set = group.groupSet />
								<#local module = set.module />
								<#local department = module.department />
								
								<#local weeksMap = mapGet(groupsMap, group) />
								<#local missedCount = 0 />
								
								<tr>
									<td class="nowrap" title="${group.groupSet.name} - ${group.name}">
										<#if defaultExpand!false>
											${group.groupSet.name}
										<#else>
											${group.groupSet.module.code?upper_case}
										</#if>
									</td>
									<#list (weekNumbers.minWeek)..(weekNumbers.maxWeek) as weekNumber>
										<#if weeksMap?keys?seq_contains(weekNumber)>
											<#local weekMap = mapGet(weeksMap, weekNumber) />
											<td>
												<#list weekMap?keys as instance>
													<#local state = mapGet(weekMap, instance) />

													<#local title><@components.instanceFormat instance academicYear department /></#local>

													<#if state.name == 'Attended'>
														<#local class = "icon-ok attended" />
														<#local title = "${student.fullName} attended: " + title />
													<#elseif state.name == 'MissedAuthorised'>
														<#local class = "icon-remove-circle authorised" />
														<#local title = "${student.fullName} did not attend (authorised absence): " + title />
													<#elseif state.name == 'MissedUnauthorised'>
														<#local class = "icon-remove unauthorised" />
														<#local title = "${student.fullName} did not attend (unauthorised): " + title />
														<#local missedCount = missedCount + 1 />
													<#elseif state.name == 'Late'>
														<#local class = "icon-warning-sign late" />
														<#local title = "No data: " + title />
													<#elseif state.name == 'NotExpected'>
														<#local class = "" />
														<#local title = "No longer in group" />
													<#else>
														<#local class = "icon-minus" />
													</#if>

													<#local titles = [title] />

													<#if mapGet(attendanceNotes, instance)??>
														<#local studentNote = mapGet(attendanceNotes, instance) />
														<#local note>
														${studentNote.truncatedNote}
															<#if (studentNote.truncatedNote?length > 0)>
																<br/>
															</#if>
															<a class='attendance-note-modal' href='<@routes.viewNote studentNote.student studentNote.occurrence />'>View attendance note</a>
														</#local>
														<#local titles = titles + [note] />
													</#if>

													<#local renderedTitle>
														<#list titles as t>
															<#if (titles?size > 1)>
																<p>${t}</p>
															<#else>
															${t}
															</#if>
														</#list>
													</#local>

													<i class="use-popover icon-fixed-width ${class}" data-content="<#noescape>${renderedTitle}</#noescape>" data-html="true"></i>
												</#list>
											</td>
										<#else>
											<td title="No event in this week">
												&nbsp;
											</td>
										</#if>
									</#list>
									<td>
										<span class="badge badge-<#if (missedCount > 2)>important<#elseif (missedCount > 0)>warning<#else>success</#if>">${missedCount}</span>
									</td>
								</tr>
							</#list>
						</tbody>
					</table>
				</div>
			</div>
		</div>
	</#macro>

	<#if !terms?? || !hasGroups>
		<h4><#if title?has_content>${title}<#else>Small groups</#if></h4>
	
		<p><em>There are no small group events defined for this academic year.</em></p>
	<#else>	
		<div class="seminar-attendance-profile striped-section collapsible <#if defaultExpand!false>expanded</#if>">
			<h3 class="section-title"><#if title?has_content>${title}<#else>Small groups</#if></h3>
			<div class="missed-info">
				<#if missedCount == 0>
					<#if is_the_student>
						You have missed 0 small group events.
					<#else>
						${command.member.firstName} has missed 0 small group events.
					</#if>
				<#else>
					<#list missedCountByTerm?keys as term>
						<#if mapGet(missedCountByTerm, term) != 0>
							<div class="missed">
								<i class="icon-warning-sign"></i>
								<#if is_the_student>
									You have
								<#else>
									${command.member.firstName} has
								</#if>
								 missed
								<#if mapGet(missedCountByTerm, term) == 1>
									1 small group event
								<#else>
									${mapGet(missedCountByTerm, term)} small group events
								</#if>
								in ${term.termTypeAsString}
							</div>
						</#if>
					</#list>
				</#if>
			</div>
		
			<div class="striped-section-contents">
				<#if terms?keys?size == 0>
					<div class="item-row row-fluid">
						<div class="span12"><em>There are no small group events for this route and year of study.</em></div>
					</div>
				<#else>
					<#list terms?keys as term>
						<@eventsInATerm term />
					</#list>
				</#if>
			</div>
		</div>
	</#if>
</div>
</#escape>
