<#escape x as x?html>
<#import "../attendance_variables.ftl" as attendance_variables />
<#import "../attendance_macros.ftl" as attendance_macros />
<#import "*/modal_macros.ftl" as modal />

<script>
	(function ($) {
		$(function() {
			$('.fix-area').fixHeaderFooter();

			$('.select-all').change(function(e) {
				$('.attendees').selectDeselectCheckboxes(this);
			});

		});
	} (jQuery));

</script>

<div class="recordCheckpointForm">

	<@attendance_macros.attendanceButtons />

	<div class="fix-area">
		<h1>Record attendance</h1>
		<h6><span class="muted">for</span> ${command.student.fullName}, ${command.pointSet.route.name}</h6>

		<#if command.checkpointMap?keys?size == 0>

			<p><em>There are no monitoring points in this scheme.</em></p>

		<#else>

			<#macro monitoringPointsByTerm term>
				<div class="striped-section">
					<h2 class="section-title">${term}</h2>
					<#if command.nonReportedTerms?seq_contains(term) >
						<div class="striped-section-contents">
							<#list command.monitoringPointsByTerm[term] as point>
								<div class="item-info row-fluid point">
									<div class="span12">
										<div class="pull-right">
											<select
												id="checkpointMap-${point.id}"
												name="checkpointMap[${point.id}]"
												title="${mapGet(command.checkpointDescriptions, point)?default("")}"
											>
												<#assign hasState = mapGet(command.checkpointMap, point)?? />
												<option value="" <#if !hasState >selected</#if>>Not recorded</option>
												<option value="unauthorised" <#if hasState && mapGet(command.checkpointMap, point).dbValue == "unauthorised">selected</#if>>Missed (unauthorised)</option>
												<option value="authorised" <#if hasState && mapGet(command.checkpointMap, point).dbValue == "authorised">selected</#if>>Missed (authorised)</option>
												<option value="attended" <#if hasState && mapGet(command.checkpointMap, point).dbValue == "attended">selected</#if>>Attended</option>
											</select>
											<#if point.pointType?? && point.pointType.dbValue == "meeting">
												<a class="meetings" title="Meetings with this student" href="<@routes.studentMeetings point command.student />"><i class="icon-info-sign icon-fixed-width"></i></a>
											<#else>
												<i class="icon-fixed-width"></i>
											</#if>
										</div>
									${point.name} (<a class="use-tooltip" data-html="true" title="<@fmt.monitoringPointDateFormat point />"><@fmt.monitoringPointFormat point /></a>)
										<@spring.bind path="command.checkpointMap[${point.id}]">
											<#if status.error>
												<div class="text-error"><@f.errors path="command.checkpointMap[${point.id}]" cssClass="error"/></div>
											</#if>
										</@spring.bind>
									</div>
									<script>
										AttendanceRecording.createButtonGroup('#checkpointMap-${point.id}');
									</script>
								</div>
             				</#list>
             			</div>
             		<#else>
             			<i class="icon-ban-circle"></i> This student's attendance for this term has already been uploaded to SITS:eVision.
             		</#if>
				</div>
			</#macro>

			<form id="recordAttendance" action="" method="post">
				<input type="hidden" name="returnTo" value="${returnTo}"/>
				<script>
					AttendanceRecording.bindButtonGroupHandler();
				</script>
				<#list attendance_variables.monitoringPointTermNames as term>
					<#if command.monitoringPointsByTerm[term]??>
						<@monitoringPointsByTerm term />
					</#if>
				</#list>

				<div class="fix-footer save-row">
					<div class="pull-right">
						<input type="submit" value="Save" class="btn btn-primary" data-loading-text="Saving&hellip;" autocomplete="off">
						<a class="btn" href="${returnTo}">Cancel</a>
					</div>
				</div>
			</form>
		</#if>
	</div>
</div>

<div id="modal" class="modal hide fade" style="display:none;">
	<@modal.header>
		<h3>Meetings</h3>
	</@modal.header>
	<@modal.body></@modal.body>
</div>

</#escape>
