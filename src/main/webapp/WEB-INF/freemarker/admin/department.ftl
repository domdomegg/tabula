<#assign spring=JspTaglibs["/WEB-INF/tld/spring.tld"]>
<#escape x as x?html>
 
<#macro longDateRange start end>
	<#assign openTZ><@warwick.formatDate value=start pattern="z" /></#assign>
	<#assign closeTZ><@warwick.formatDate value=end pattern="z" /></#assign>
	<@warwick.formatDate value=start pattern="d MMMM yyyy HH:mm" /> 
	<#if openTZ != closeTZ>(${openTZ})</#if>
	-<br>
	<@warwick.formatDate value=end pattern="d MMMM yyyy HH:mm (z)" />
</#macro>
 
<#if department??>
<h1>${department.name}</h1>

<#list modules as module>
<#assign can_manage=can.manage(module) />
<a id="module-${module.code}"></a>
<div class="module-info">
<h2><@fmt.module_name module /></h2>
	
	
	<div>
		
		<#assign  module_managers = ((module.participants.includeUsers)![]) />
		<@fmt.p module_managers?size "module manager"/><#if module_managers?size gt 0>:
			<@fmt.user_list_csv ids=module_managers />
		</#if>
		<#if can_manage >	
		<span class="actions">
		<a title="Edit module permissions" href="<@url page="/admin/module/${module.code}/permissions" />">
		edit
		</a>
		</span>
		</#if>
	</div>
	
	<#if module.assignments!?size = 0>
		<p>This module has no assignments. 
		<span class="actions">
		<a href="<@url page="/admin/module/${module.code}/assignments/new" />">New assignment</a>
		</span>
		</p>
	<#else>
		<#list module.assignments as assignment>
		<#if !assignment.deleted>
		<#assign has_feedback = assignment.feedbacks?size gt 0 >
		<div class="assignment-info">
			<div class="column1">
			<h3 class="name">${assignment.name}</h3>
			</div>
			<div class="stats">
				<div class="open-date">
					<span class="label">Opens</span> <@warwick.formatDate value=assignment.openDate pattern="d MMM yyyy HH:mm" /> 
				</div>
				<div class="close-date">
					<span class="label">Closes</span> <@warwick.formatDate value=assignment.closeDate pattern="d MMM yyyy HH:mm" /> 
				</div>
				<#if features.submissions && assignment.collectSubmissions>
					<div class="submission-count">
						<#if assignment.submissions?size gt 0>
							<a href="<@routes.assignmentsubmissions assignment=assignment />" title="View all submissions">
								${assignment.submissions?size} submissions
							</a>
						<#else>
							${assignment.submissions?size} submissions
						</#if>
					</div>
				</#if>
				<div class="feedback-count">
				<#if has_feedback><a class="list-feedback-link" href="<@routes.assignmentfeedbacks assignment=assignment  />"></#if>
				${assignment.feedbacks?size} feedback<#if has_feedback></a></#if>
				<#assign unreleasedFeedback=assignment.unreleasedFeedback />
				<#if unreleasedFeedback?size gt 0>
					<span class="has-unreleased-feedback">
					(${unreleasedFeedback?size} to publish)
					</span>
				<#elseif has_feedback>
					<span class="no-unreleased-feedback">
					(all published)
					</span>
				</#if>
				</div>
				
				<#if assignment.anyReleasedFeedback || features.submissions>
				<p class="feedback-published">
					<#assign urlforstudents><@url page="/module/${module.code}/${assignment.id}"/></#assign>
					<a class="copyable-url" href="${urlforstudents}" title="This is the link you can freely give out to students or publish on your module web page. Copy it to the clipboard and then paste it into an email or page.">
						URL for students
					</a>
				</p>
				</#if>
				
			</div>
			<div class="actions assignment-buttons">
				<a class="edit-link" href="<@url page="/admin/module/${module.code}/assignments/${assignment.id}/edit" />">edit details</a>
				<a class="feedback-link" href="<@url page="/admin/module/${module.code}/assignments/${assignment.id}/feedback/batch" />">add feedback</a>
				<#if has_feedback >
				<a class="list-feedback-link" href="<@url page="/admin/module/${module.code}/assignments/${assignment.id}/feedback/list" />">list feedback</a>
				<#if assignment.canPublishFeedback>
				<a class="list-feedback-link" href="<@url page="/admin/module/${module.code}/assignments/${assignment.id}/publish" />">publish feedback</a>
				</#if>
				</#if>
			</div>
			<div class="end-assignment-info"></div>
		</div>
		</#if>
		</#list>
		
		<div class="actions">
		<a href="<@url page="/admin/module/${module.code}/assignments/new" />">New assignment</a>
		</div>
	</#if>
	
</div>
</#list>

<#else>
<p>No department.</p>
</#if>

</#escape>