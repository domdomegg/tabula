<#escape x as x?html>

<#macro list studentCourseDetails meetings relationshipType>
	<#assign can_read_meetings = can.do_with_selector("Profiles.MeetingRecord.Read", studentCourseDetails, relationshipType) />
	<#assign can_create_meetings = can.do_with_selector("Profiles.MeetingRecord.Create", studentCourseDetails, relationshipType) />
	<#assign existingRelationship = ((studentCourseDetails.relationships(relationshipType))![])?size gt 0 />
	<#assign can_create_scheduled_meetings = can.do_with_selector("Profiles.ScheduledMeetingRecord.Create", studentCourseDetails, relationshipType) />

	<section class="meetings ${relationshipType.id}-meetings" data-target-container="${relationshipType.id}-meetings">
		<div class="list-controls">
			<#if can_read_meetings>
				<h5>Record of meetings</h5>
			</#if>

			<#if can_create_meetings>
				<a class="btn-like new" href="<@routes.meeting_record studentCourseDetails.urlSafeId relationshipType />" title="Create a new record"><i class="icon-edit"></i> New record</a>
			</#if>
			<#if existingRelationship && can_create_scheduled_meetings && features.scheduledMeetings>
				<a class="btn-like new" href="<@routes.create_scheduled_meeting_record studentCourseDetails.urlSafeId relationshipType />" title="Schedule a meeting"><i class="icon-time"></i> Schedule</a>
				<#if showIntro("scheduled-meetings", "anywhere")>
					<#assign introText>
						<p>You can now schedule meetings in advance.</p>
					</#assign>
					<a href="#"
					   id="scheduled-meetings-intro"
					   class="use-introductory auto"
					   data-hash="${introHash("scheduled-meetings", "anywhere")}"
					   data-title="Schedule Meetings"
					   data-placement="bottom"
					   data-html="true"
					   data-content="${introText}"><i class="icon-question-sign"></i></a>
				</#if>
			</#if>

		</div>
		<#if can_read_meetings>
			<#if meetings??>
				<a class="toggle-all-details btn-like open-all-details" title="Expand all meetings"><i class="icon-plus"></i> Expand all</a>
				<a class="toggle-all-details btn-like close-all-details hide" title="Collapse all meetings"><i class="icon-minus"></i> Collapse all</a>
				<#list meetings as meeting>
					<#assign deletedClasses><#if meeting.deleted>deleted muted</#if></#assign>
					<#assign pendingAction = meeting.pendingActionBy(viewer) />
					<#assign pendingActionClasses><#if pendingAction>well</#if></#assign>

					<#if (openMeeting?? && openMeeting.id == meeting.id) || pendingAction>
						<#assign openClass>open</#assign>
						<#assign openAttribute>open="open"</#assign>
					<#else>
						<#assign openClass></#assign>
						<#assign openAttribute></#assign>
					</#if>

					<details class="meeting ${deletedClasses} ${pendingActionClasses} ${openClass!} <#if meeting.scheduled>scheduled<#else>normal</#if>" ${openAttribute!}>
						<summary>
							<span class="date">
								<#if meeting.scheduled>
									<@fmt.date date=meeting.meetingDate includeTime=true />
								<#else>
									<@fmt.date date=meeting.meetingDate includeTime=false />
								</#if>
							</span>
							<span class="title">${meeting.title!}</span>

							<#if meeting.scheduled>
								<#assign can_update_scheduled_meeting = can.do("Profiles.ScheduledMeetingRecord.Update", meeting) />
								<#local editUrl><@routes.edit_scheduled_meeting_record meeting studentCourseDetails.urlSafeId relationshipType /></#local>
							<#else>
								<#local editUrl><@routes.edit_meeting_record studentCourseDetails.urlSafeId meeting /></#local>
							</#if>
							<#if ((meeting.scheduled && can_update_scheduled_meeting) || (!meeting.scheduled && viewer.universityId == meeting.creator.universityId && !meeting.approved))>
								<div class="meeting-record-toolbar">
									<a href="${editUrl}" class="btn-like edit-meeting-record" title="Edit record"><i class="icon-edit" ></i></a>
									<a href="<@routes.delete_meeting_record meeting />" class="btn-like delete-meeting-record" title="Delete record"><i class="icon-trash"></i></a>
									<a href="<@routes.restore_meeting_record meeting />" class="btn-like restore-meeting-record" title="Restore record"><i class="icon-repeat"></i></a>
									<a href="<@routes.purge_meeting_record meeting />" class="btn-like purge-meeting-record" title="Purge record"><i class="icon-remove"></i></a>
									<i class="icon-spinner icon-spin"></i>
								</div>
							</#if>
						</summary>
						<div class="meeting-body">
							<#if meeting.description??>
								<div class="description">
									<#noescape>${meeting.escapedDescription}</#noescape>
								</div>
							</#if>

							<#if meeting.attachments?? && meeting.attachments?size gt 0>
								<@fmt.download_attachments meeting.attachments "/${relationshipType.urlPart}/meeting/${meeting.id}/" "for this meeting record" "${meeting.title?url}" />
							</#if>

							<#if meeting.scheduled>
								<@scheduledMeetingState meeting studentCourseDetails/>
							<#else>
								<@meetingState meeting studentCourseDetails/>
							</#if>
						</div>
					</details>
				</#list>
			</#if>
		</#if>
	</section>
</#macro>

<#macro meetingState meeting studentCourseDetails>
	<#if meeting.pendingApproval && !meeting.pendingApprovalBy(viewer)>
	<small class="muted">Pending approval. Submitted by ${meeting.creator.fullName}, <@fmt.date meeting.creationDate /></small>
	<div class="alert alert-info">
		This meeting record needs to be approved by <#list meeting.pendingApprovers as approver>${approver.fullName}<#if approver_has_next>, </#if></#list>.
	</div>
	<#elseif meeting.pendingApprovalBy(viewer)>
	<small class="muted">Pending approval. Submitted by ${meeting.creator.fullName}, <@fmt.date meeting.creationDate /></small>
	<div class="pending-action alert alert-warning">
		This record needs your approval. Please review, then approve or reject it.
		If you reject it, please explain why.
		<#if meetingApprovalWillCreateCheckpoint[meeting.id]>
			<br />
			Approving this meeting record will mark a monitoring point as attended.
		</#if>
	</div>
	<!-- not a spring form as we don't want the issue of binding multiple sets of data to the same command -->
	<form method="post" class="approval" id="meeting-${meeting.id}" action="<@routes.save_meeting_approval meeting />" >
		<@form.row>
			<@form.field>
				<label class="radio inline">
					<input type="radio" name="approved" value="true">
					Approve
				</label>
				<label class="radio inline">
					<input class="reject" type="radio" name="approved" value="false">
					Reject
				</label>
			</@form.field>
		</@form.row>
		<div class="rejection-comment" style="display:none">
			<@form.row>
				<@form.field>
					<textarea class="big-textarea" name="rejectionComments"></textarea>
				</@form.field>
			</@form.row>
		</div>
		<button type="submit" class="btn btn-primary">Submit</button>
	</form>
	<#elseif meeting.rejectedBy(viewer)>
	<small class="muted">Pending revision. Submitted by ${meeting.creator.fullName}, <@fmt.date meeting.creationDate /></small>
	<div class="alert alert-error">
		<div class="rejection">
			<p>You rejected this record. The other party will review the record and submit it for approval again.</p>
		</div>
	</div>
	<#elseif meeting.pendingRevisionBy(viewer)>
	<small class="muted">Pending approval. Submitted by ${meeting.creator.fullName}, <@fmt.date meeting.creationDate /></small>
	<div class="pending-action alert alert-error">
		<#list meeting.rejectedApprovals as rejectedApproval>
			<div class="rejection">
				<p>This record has been rejected by ${rejectedApproval.approver.fullName} because:</p>
				<blockquote class="reason">${rejectedApproval.comments}</blockquote>
				<p>Please edit the record and submit it for approval again.</p>
			</div>
		</#list>
	</div>
	<div class="submit-buttons">
		<a class="edit-meeting-record btn btn-primary" href="<@routes.edit_meeting_record studentCourseDetails.urlSafeId meeting/>">Edit</a>
	</div>
	<#else>
	<small class="muted">${(meeting.format.description)!"Unknown format"} between ${(meeting.relationship.agentName)!meeting.relationship.relationshipType.agentRole} and ${(meeting.relationship.studentMember.fullName)!"student"}. Created by ${meeting.creator.fullName}, <@fmt.date meeting.lastUpdatedDate /></small>
	</#if>
</#macro>

<#macro scheduledMeetingState meeting studentCourseDetails>
	<#if meeting.pendingAction && !meeting.pendingActionBy(viewer)>
	<small class="muted">Pending confirmation. Submitted by ${meeting.creator.fullName}, <@fmt.date meeting.creationDate /></small>
	<div class="alert alert-info">
		${meeting.creator.fullName} needs to confirm that this meeting took place.
	</div>
	<#elseif meeting.pendingActionBy(viewer)>
		<small class="muted">Pending confirmation. ${(meeting.format.description)!"Unknown format"} between ${(meeting.relationship.agentName)!meeting.relationship.relationshipType.agentRole} and ${(meeting.relationship.studentMember.fullName)!"student"}. Created by ${meeting.creator.fullName}, <@fmt.date meeting.lastUpdatedDate /></small>
		<div class="alert alert-warning">
			Please confirm whether this scheduled meeting took place.
		</div>
		<form method="post" class="scheduled-action" id="meeting-${meeting.id}" action="<@routes.choose_action_scheduled_meeting_record meeting studentCourseDetails.urlSafeId meeting.relationship.relationshipType />" >
			<@form.row>
				<@form.field>
					<label class="radio">
						<input checked type="radio" name="action" value="confirm" data-formhref="<@routes.confirm_scheduled_meeting_record meeting studentCourseDetails.urlSafeId meeting.relationship.relationshipType />">
						Confirm
					</label>
					<label class="radio">
						<input type="radio" name="action" value="reschedule" />
						Reschedule
					</label>
					<label class="radio">
						<input type="radio" name="action" value="missed" class="reject" data-formhref="<@routes.missed_scheduled_meeting_record meeting meeting.relationship.relationshipType />">
						Record that the meeting did not take place
					</label>
				</@form.field>
			</@form.row>
			<div class="rejection-comment" style="display:none">
				<@form.row>
					<@form.field>
						<textarea class="big-textarea" name="missedReason"></textarea>
					</@form.field>
				</@form.row>
			</div>
			<div class="ajaxErrors alert alert-error" style="display: none;"></div>
			<button type="submit" class="btn btn-primary">Submit</button>
		</form>
	<#elseif meeting.missed>
		<div class="alert alert-error rejection">
			<#if meeting.missedReason?has_content>
				<p> This meeting did not take place because: </p>
				<blockquote class="reason">${meeting.missedReason}</blockquote>
			<#else>
				<p> This meeting did not take place (no reason given) </p>
			</#if>
		</div>
		<small class="muted">${(meeting.format.description)!"Unknown format"} between ${(meeting.relationship.agentName)!meeting.relationship.relationshipType.agentRole} and ${(meeting.relationship.studentMember.fullName)!"student"}. Created by ${meeting.creator.fullName}, <@fmt.date meeting.lastUpdatedDate /></small>
	<#else>
		<small class="muted">${(meeting.format.description)!"Unknown format"} between ${(meeting.relationship.agentName)!meeting.relationship.relationshipType.agentRole} and ${(meeting.relationship.studentMember.fullName)!"student"}. Created by ${meeting.creator.fullName}, <@fmt.date meeting.lastUpdatedDate /></small>
	</#if>
</#macro>

</#escape>

