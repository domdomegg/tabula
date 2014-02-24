<#assign student = studentCourseDetails.student/>
<#assign agent_role = relationshipType.agentRole />
<#assign member_role = relationshipType.studentRole />
<#assign agent_name><#if !command.considerAlternatives!false>${command.relationship.agentName}</#if></#assign>

<#assign heading>
	<h2>Schedule a meeting</h2>
	<h6>
		<span class="muted">between ${agent_role}</span> ${agent_name!""}
		<span class="muted">and ${member_role}</span> ${student.fullName}
	</h6>
</#assign>

<#if isModal!false>
	<div class="modal-header">
		<button type="button" class="close" data-dismiss="modal" aria-hidden="true">&times;</button>
		${heading}
	</div>
<#elseif iframe!false>
	<div id="container">
<#else>
	${heading}
</#if>
<#if isModal!false>
	<div class="modal-body" id="meeting-record-modal-body"></div>
	<div class="modal-footer">
		<form class="double-submit-protection">
			<span class="submit-buttons">
				<button class="btn btn-primary spinnable spinner-auto" type="submit" name="submit">
					Schedule
				</button>
				<button class="btn" data-dismiss="modal" aria-hidden="true">Cancel</button>
			</span>
		</form>
	</div>
<#else>
	<!-- blank action = post to current path. needs to be left blank so we know if we should post to create or edit -->
	<@f.form id="meeting-record-form" method="post" enctype="multipart/form-data" action="" commandName="command" class="form-horizontal double-submit-protection">

		<@form.labelled_row "title" "Title">
			<@f.input type="text" path="title" cssClass="input-xxlarge" maxlength="255" placeholder="Subject of meeting" />
		</@form.labelled_row>

		<#if allRelationships?? && allRelationships?size gt 1>
			<#assign isCreatorAgent = creator?? && creator.id == command.relationship.agent />
			<#if !isCreatorAgent>
				<#assign cssClass = "warning" />
			</#if>

			<@form.labelled_row "relationship" agent_role?cap_first cssClass!"">
				<@f.select path="relationship" cssClass="input-large">
					<@f.option disabled="true" selected="true" label="Please select one..." />
					<@f.options items=allRelationships itemValue="agent" itemLabel="agentName" />
				</@f.select>
				<small class="help-block">
					<#if isCreatorAgent>
						You have been selected as ${agent_role} by default. Please change this if you're recording a colleague's meeting.
					<#else>
						The first ${agent_role} has been selected by default. Please check it's the correct one. <i id="supervisor-ok" class="icon-ok"></i>
					</#if>
				</small>
			</@form.labelled_row>
		</#if>

		<script>
			jQuery(function($) {
				$("#supervisor-ok, #relationship").on("focus click keyup", function() {
					$(this).closest(".warning").removeClass("warning");
					$("#supervisor-ok").remove();
				}).addClass("clickable-cursor");
			});
		</script>

		<@form.labelled_row "meetingDate" "Date of meeting">
			<div class="input-append">
				<@f.input type="text" path="meetingDate" cssClass="input-medium date-time-minute-picker" placeholder="Pick the date" />
				<span class="add-on"><i class="icon-calendar"></i></span>
			</div>
		</@form.labelled_row>

		<@form.labelled_row "format" "Format">
			<@f.select path="format" cssClass="input-large">
				<@f.option disabled="true" selected="true" label="Please select one..." />
				<@f.options items=formats />
			</@f.select>
		</@form.labelled_row>

		<#-- file upload (TAB-359) -->
		<#assign fileTypes=command.attachmentTypes />
		<@form.filewidget basename="file" types=fileTypes />

		<#if command.attachedFiles?has_content >
			<@form.labelled_row "attachedFiles" "Previously uploaded files">
				<ul class="unstyled">
					<#list command.attachedFiles as attachment>
						<li id="attachment-${attachment.id}" class="attachment">
							<span>${attachment.name}</span>&nbsp;
							<@f.hidden path="attachedFiles" value="${attachment.id}" />
							<a class="remove-attachment" href="">Remove</a>
						</li>
					</#list>
				</ul>
				<script>
					jQuery(function($){
						$(".remove-attachment").on("click", function(e){
							$(this).closest("li.attachment").remove();
							return false;
						});
					});
				</script>
				<small class="subtle help-block">
					This is a list of all supporting documents that have been attached to this meeting record.
					Click the remove link next to a document to delete it.
				</small>
			</@form.labelled_row>
		</#if>

<#-- TODO: TinyMCE editor, bleh -->
<@form.labelled_row "description" "Description (optional)">
<@f.textarea rows="6" path="description" cssClass="input-xxlarge" />
</@form.labelled_row>

		<#if !iframe!false>
			<#-- separate page, not modal -->
			<div class="form-actions">
				<button class="btn btn-primary spinnable spinner-auto" type="submit" name="submit">
					Schedule
				</button>
				<a class="btn" href="<@routes.profile student />">Cancel</a>
			</div>
		</#if>
	</@f.form>
</#if>

<#if iframe!false>
	</div> <#--container -->
</#if>

