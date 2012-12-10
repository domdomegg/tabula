<#assign has_feedback=assignmentsWithFeedback?has_content />
<#assign has_submissions=assignmentsWithSubmission?has_content />
<#assign has_assignments=enrolledAssignments?has_content />

<#assign has_any_items = (has_feedback || has_submissions || has_assignments) />

<#if has_any_items || user.student>

<h2>Your assignments</h2>

	<p>
	<strong>Is an assignment missing here?</strong> You will need to get in touch with your module convenor in the first instance.
	They may not have set up the assignment, or may not be using this system for assessment, or you may not be correctly
	enrolled.
	</p>

	<#if has_any_items>
		
		<#macro format_name assignment>
			${assignment.module.code?upper_case} (${assignment.module.name}) - ${assignment.name}
		</#macro>
		<#macro assignment_link assignment>
			<a href="<@url page='/module/${assignment.module.code}/${assignment.id}/' />">
				<#nested />	
			</a>
		</#macro>
		
		<div class="simple-assignment-list">
		
		<#if has_assignments>
		<#macro enrolled_assignment info>
			<#local assignment = info.assignment />
			<#local extension = info.extension!false />
			<#local isExtended = info.isExtended!false />
			<#local extensionRequested = info.extensionRequested!false />
			<@assignment_link assignment>
				<@format_name assignment />	
			</@assignment_link>
			<#if info.submittable>
				<#include "../submit/assignment_deadline.ftl" />
			</#if>
		</#macro>
		<#list enrolledAssignments as info>
			<div class="simple-assignment-info">
				<span class="label label-info">Enrolled</span>
				<@enrolled_assignment info />
			</div>
		</#list>
		</#if>
		
		<#if has_feedback>
		<#list assignmentsWithFeedback as assignment>
			<div class="simple-assignment-info">
				<span class="label-green">Marked</span>
				<@assignment_link assignment>
					<@format_name assignment />	
				</@assignment_link>
			</div>
		</#list>
		</#if>
		
		<#if has_submissions>
		<#list assignmentsWithSubmission as assignment>
			<div class="simple-assignment-info">
				<span class="label-orange">Submitted</span>
				<@assignment_link assignment>
					<@format_name assignment />	
				</@assignment_link>
			</div>
		</#list>
		</#if>
		
		</div>
	
	<#else><#-- !has_any_items -->

		<p>
		We don't have anything for you here. Talk to your module convenor if this seems like a mistake.
		</p>
		
	</#if>
	
	<#if archivedAssignments?has_content>
	<div id="archived-assignments-container">
	<div class="simple-assignment-list" id="archived-assignments-list">
	<#list archivedAssignments as assignment>
		<div class="simple-assignment-info">
			<@assignment_link assignment>
				<@format_name assignment />	
			</@assignment_link>
		</div>
	</#list>
	</div>
	</div>
	</#if>


</#if>
