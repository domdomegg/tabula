<#import "/WEB-INF/freemarker/_profile_link.ftl" as pl />
<#import "*/coursework_components.ftl" as components />
<#import "*/modal_macros.ftl" as modal />
<#escape x as x?html>
	<#macro markingTabSection order enhancedMarkerFeedbacks readOnly=true>
		<div class="marking-stage">
				<#if order.hasFeedback>
					<#if !readOnly>
						<#if order.headerStage.nextStagesDescription?has_content>
							<a class="btn btn-primary must-have-selected must-have-ready-next-stage form-post" href="${markingCompleted}">Confirm selected and send to ${order.headerStage.nextStagesDescription?lower_case}</a>
						</#if>
						<#if order.headerStage.canFinish(assignment.cm2MarkingWorkflow)>
							<a class="btn btn-primary must-have-selected must-have-ready-next-stage form-post" href="${finishMarking}">Confirm selected and send to admin</a>
						</#if>
						<#if features.bulkModeration && order.headerStage.allowsBulkAdjustments && assignment.module.adminDepartment.assignmentGradeValidation>
							<a class="btn btn-primary" data-toggle="modal" data-target="#bulk-adjustment-modal" href="<@routes.cm2.bulkModeration assignment order.headerStage marker />">Bulk moderate submissions</a>
						</#if>
					</#if>
					<div class="btn-group">
						<button type="button" class="btn btn-default dropdown-toggle" data-toggle="dropdown" aria-haspopup="true" aria-expanded="false">
							Download <span class="caret"></span>
						</button>
						<ul class="dropdown-menu">
							<li class="must-have-selected">
								<a class="form-post" href="<@routes.cm2.downloadMarkerSubmissions assignment marker />">
									Download all selected submissions
								</a>
							</li>
							<li class="must-have-selected">
								<a class="download-pdf" data-target="#download-pdf-modal-${order.headerStage.name}" href="<@routes.cm2.downloadMarkerSubmissionsPdf assignment marker />">
									Download all selected submissions as pdf
								</a>
							</li>
							<#if features.feedbackTemplates && assignment.hasFeedbackTemplate>
								<li>
									<a class="use-tooltip" title="Download feedback templates for all students as a ZIP file." href="<@routes.cm2.markerTemplatesZip assignment marker/>" data-container="body">
										Download feedback templates
									</a>
								</li>
							</#if>
							<#list workflowType.allPreviousStages(order.headerStage) as pStage>
								<li class="must-have-selected">
									<a class="form-post" href="<@routes.cm2.downloadMarkerFeedbackStage assignment marker pStage />">Download feedback from ${pStage.description?lower_case}</a>
								</li>
							</#list>
						</ul>
					</div>
					<#if !readOnly>
						<div class="btn-group">
							<button type="button" class="btn btn-default dropdown-toggle" data-toggle="dropdown" aria-haspopup="true" aria-expanded="false">
								Upload <span class="caret"></span>
							</button>
							<ul class="dropdown-menu">
								<li><a href="<@routes.cm2.markerUploadFeedback assignment marker />">Upload attachments</a></li>
								<li><a href="<@routes.cm2.markerUploadMarks assignment marker />">Upload marks & feedback</a></li>
							</ul>
						</div>
					</#if>
					<table class="table table-striped marking-table table-sortable<#if order.headerStage.summarisePreviousFeedback> preview-marks</#if>">
						<thead>
						<tr>
							<th class="check-col"><@bs3form.selector_check_all /></th>
							<th class="student-col sortable">
								<#if assignment.anonymity.equals(AssignmentAnonymity.FullyAnonymous)>
									ID
								<#else>
									University ID
								</#if>
							</th>
							<#if assignment.showSeatNumbers>
								<th class="student-col sortable">Seat number</th>
							</#if>
							<#if assignment.anonymity.equals(AssignmentAnonymity.NameAndID)>
								<th class="student-col sortable">First name</th>
								<th class="student-col sortable">Last name</th>
							</#if>
							<#if order.headerStage.summariseCurrentFeedback>
									<th class="sortable">Mark</th>
							</#if>
							<#if order.headerStage.summarisePreviousFeedback>
								<#list order.headerStage.previousStages as prevStage>
									<th class="sortable">${prevStage.allocationName}</th>
									<th class="sortable">${prevStage.allocationName} Mark</th>
								</#list>
							</#if>
							<th colspan="2">Progress</th>
						</tr>
						</thead>
						<tbody>
							<#assign maxPrevMarkerCols = 0 />
							<#list enhancedMarkerFeedbacks as emf>
								<#assign mf = emf.markerFeedback />
								<#assign student = emf.workflowStudent.info.user />
								<#assign studentId><#if assignment.anonymity.equals(AssignmentAnonymity.FullyAnonymous)>${mf.feedback.anonymousId}<#else>${student.userId}</#if></#assign>
								<tr
									data-toggle="collapse"
									data-target="#${mf.stage.name}-${studentId}"
									class="clickable collapsed expandable-row <#if mf.readyForNextStage>ready-next-stage</#if>"
								>
									<td class="check-col">
										<@bs3form.selector_check_row name="markerFeedback" value="${mf.id}" />
									</td>
									<td class="toggle-icon-large student-col">
										<#assign colspan = 4>
										<#if assignment.anonymity.equals(AssignmentAnonymity.FullyAnonymous)>
											Student${mf.feedback.anonymousId}
										<#else>
											${mf.feedback.studentIdentifier!""}
										</#if>
									</td>
									<#if assignment.showSeatNumbers>
										<#assign colspan = colspan + 1/>
										<td class="student-col">${assignment.getSeatNumber(student)!""}</td>
									</#if>
									<#if assignment.anonymity.equals(AssignmentAnonymity.NameAndID)>
										<#assign colspan = colspan + 2/>
										<td class="student-col">${student.firstName}</td>
										<td class="student-col">${student.lastName}&nbsp;<#if student.warwickId??><@pl.profile_link student.warwickId /><#else><@pl.profile_link student.userId /></#if></td>
									</#if>
									<#if mf.stage.summariseCurrentFeedback>
										<td><#if mf.mark??>${mf.mark}</#if></td>
										<#assign colspan = colspan + 1>
									</#if>
									<#if mf.stage.summarisePreviousFeedback>
										<#if emf.previousMarkerFeedback?has_content>
											<#list emf.previousMarkerFeedback as prevMf>
											<td><#if prevMf.marker??>${prevMf.marker.fullName}</#if></td>
											<td><#if prevMf.mark??>${prevMf.mark}</#if></td>
												<#assign colspan = colspan + 2>
												<#assign maxPrevMarkerCols = maxPrevMarkerCols + 2>
											</#list>
										<#elseif order.numPreviousMarkers gt 0>
											<td colspan="${order.numPreviousMarkers * 2}"></td>
										</#if>
									</#if>
									<td class="progress-col">
										<@components.individual_stage_progress_bar emf.workflowStudent.stages assignment student />
									</td>
									<td>
										<#if emf.workflowStudent.nextAction?has_content>
										<@components.workflowMessage emf.workflowStudent.nextAction assignment student />
										</#if>
									</td>
								</tr>
								<#assign detailUrl><@routes.cm2.markerOnlineFeedback assignment mf.stage marker student /></#assign>
								<tr id="${mf.stage.name}-${studentId}" data-detailurl="${detailUrl}" class="collapse detail-row">
									<td colspan="${colspan}" class="detailrow-container">
										<i class="fa fa-spinner fa-spin"></i> Loading
									</td>
								</tr>
							</#list>
						</tbody>
					</table>
					<#if !readOnly && order.headerStage.nextStagesDescription?has_content>
						<a class="btn btn-primary must-have-selected must-have-ready-next-stage form-post" href="${markingCompleted}">Confirm selected and send to ${order.headerStage.nextStagesDescription?lower_case}</a>
					</#if>
					<div id="download-pdf-modal-${order.headerStage.name}" class="modal fade">
						<@modal.wrapper>
							<@modal.header>
								<h3 class="modal-title">Download submissions as PDF</h3>
							</@modal.header>
							<@modal.body>
								<p>There are <span class="count"></span> submissions that have files that are not PDFs (shown below). The download will not include these files.</p>
								<p><a class="form-post btn btn-primary"
											data-href="<@routes.cm2.downloadMarkerSubmissionsPdf assignment marker />?download" href="">Download submissions as PDF</a>First
								</p>
								<ul class="submissions"></ul>
							</@modal.body>
						</@modal.wrapper>
					</div>
				<#else>
					No students found
				</#if>
		</div>
	</#macro>


	<div>
		<#if feedbackByOrderAndStage?size gt 1>
			<ul class="nav nav-tabs" role="tablist" style="margin-top: 20px;margin-bottom: 20px;" id="markingStageTabs">
				<#list feedbackByOrderAndStage as order>
					<li role="presentation"<#if activeWorkflowPosition == order.headerStage.order> class="active"</#if>>
						<a href="#${order.headerStage.name}" role="tab" data-toggle="tab" data-order="${order.headerStage.order}">
							${order.headerStage.description}

							<#if order.feedbackByActionability.readyToMark?size != 0>
								<span class="badge">${order.feedbackByActionability.readyToMark?size}</span>
							</#if>
						</a>
					</li>
				</#list>
			</ul>
		</#if>
	<div class="tab-content" id="markingTabContent">
	<#list feedbackByOrderAndStage as order>
		<div class="tab-pane<#if activeWorkflowPosition == order.headerStage.order> active</#if>" role="tabpanel" id="${order.headerStage.name}">
			<#assign markingCompleted><@routes.cm2.markingCompleted assignment order.headerStage.order marker /></#assign>
			<#assign finishMarking><@routes.cm2.finishMarking assignment order.headerStage.order marker /></#assign>

			<#assign headerStage = order.headerStage />

			<#assign moderator = headerStage.roleName == "Moderator" />
			<#assign actionPresent = moderator?string("moderate feedback", "add marks and feedback") />
			<#assign actionPast = moderator?string("moderating feedback", "adding marks and feedback") />
			<#assign markOrModerate = moderator?string("moderate", "mark") />
			<#assign markingOrModeration = moderator?string("moderation", "marking") />
			<#assign moderatedOrMarked = moderator?string("moderated", "marked") />

			<div class="marking-tab-section">
				<h4>Ready to ${markOrModerate}</h4>

				<#if order.feedbackByActionability.readyToMark?size != 0>
					<p>You can ${actionPresent} for these students now.</p>

					<@markingTabSection order order.feedbackByActionability.readyToMark false />
				<#else>
					<#if moderator>
						<p>No feedback is ready for you to moderate.</p>
					<#else>
						<p>No students are ready to mark.</p>
					</#if>
				</#if>
			</div>

			<div class="marking-tab-section">
				<h4>
					<a href="#" data-toggle="collapse" data-target="#${headerStage.name}-notReadyToMarkSubmissions">
						<i class="fa fa-fw fa-chevron-right"></i>Upcoming ${markingOrModeration} (${order.feedbackByActionability.notReadyToMark?size})
					</a>
				</h4>

				<div id="${headerStage.name}-notReadyToMarkSubmissions" class="marking-collapse collapse">
					<#if order.feedbackByActionability.notReadyToMark?size != 0>
						<p>These students are allocated to you for ${markingOrModeration}, but haven't yet been released or are with a previous marker.</p>

						<@markingTabSection order order.feedbackByActionability.notReadyToMark true />
					<#else>
						<p>No students to show.</p>
					</#if>
				</div>
			</div>

			<div class="marking-tab-section">
				<h4>
					<a href="#" data-toggle="collapse" data-target="#${headerStage.name}-markedSubmissions">
						<i class="fa fa-fw fa-chevron-right"></i>${moderatedOrMarked?cap_first} (${order.feedbackByActionability.marked?size})
					</a>
				</h4>

				<div id="${headerStage.name}-markedSubmissions" class="marking-collapse collapse">
					<#if order.feedbackByActionability.marked?size != 0>
						<p>You've finished ${actionPast} for these students, so can't make any further changes.</p>

						<@markingTabSection order order.feedbackByActionability.marked true />
					<#else>
						<p>No students to show.</p>
					</#if>
				</div>
			</div>
		</div>
	</#list>
		<div id="bulk-adjustment-modal" class="modal fade"></div>
		</div>
	</div>

	<script>
		jQuery(function ($) {
			$('.marking-tab-section > h4 > [data-toggle=collapse]').on('click', function(e) {
				e.preventDefault();
			});

			$('.marking-collapse').on('show.bs.collapse', function (e) {
				if (e.target === this) {
					$('[data-toggle=collapse][data-target="#' + e.target.id + '"]').children('.fa').removeClass('fa-chevron-right').addClass('fa-chevron-down');
				}
			});

			$('.marking-collapse').on('hide.bs.collapse', function (e) {
				if (e.target === this) {
					$('[data-toggle=collapse][data-target="#' + e.target.id + '"]').children('.fa').removeClass('fa-chevron-down').addClass('fa-chevron-right');
				}
			});

			$('#markingStageTabs').on('show.bs.tab', function (e) {
				$('#filtersActiveStage').val($(e.target).data('order'));
			});

			$('#markingTabContent').on('shown.bs.collapse', function (e) {
				var tabId = $(e.target).closest('.tab-pane').attr('id');

				$(e.target).closest('.marking-collapse').collapse('show');

				$('#markingStageTabs').find('a[href="#' + tabId + '"]').tab('show');

				e.target.scrollIntoView();
			});

			$('#markingTabContent').on('click', 'a[data-toggle=modal]', function(e){
				e.preventDefault();
				var $this = $(this);
				var target = $this.attr('data-target');
				var url = $this.attr('href');
				$(target).load(url);
			});

			$("#bulk-adjustment-modal").on('submit', function(e){
				e.preventDefault();
				var $this = $(this);
				var $form = $this.find("form");
				jQuery.post($form.attr('action'), $form.serialize(), function(data){
					if(data.status === "successful") {
						$this.modal('toggle');
						window.location = data.redirect;
					} else {
						$this.html(data);
					}
				});
			});
			//# sourceURL=marker_feedback_list.ftl
		})
	</script>
</#escape>