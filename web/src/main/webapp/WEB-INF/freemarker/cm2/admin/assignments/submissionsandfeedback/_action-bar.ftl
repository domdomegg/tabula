<#import "*/modal_macros.ftl" as modal />

<div class="clearfix">
	<div class="pull-right view-selector">
		<form class="form-inline">
			<label class="radio">View as:</label>
			<label class="radio">
				<input type="radio" name="view" value="summary" data-href="<@routes.cm2.assignmentsubmissionsandfeedbacksummary assignment />" <#if currentView == 'summary'>checked="checked"</#if> />
				Summary
			</label>
			<label class="radio">
				<input type="radio" name="view" value="table" data-href="<@routes.cm2.assignmentsubmissionsandfeedbacktable assignment />" <#if currentView == 'table'>checked="checked"</#if> />
				Table
			</label>
		</form>
	</div>
	<script type="text/javascript">
		jQuery(function($) {
			$('.view-selector input[name="view"]').on('change', function() {
				var $this = $(this);

				if ($this.is(':checked')) {
					var $form = $('<form></form>').attr({method:'POST',action:$this.data('href')}).hide();

					var $inputs = $this.closest(".btn-toolbar").parent().find(".filter-form :input");
					$form.append($inputs.clone());

					$(document.body).append($form);
					$form.submit();
				}
			});
		});
	</script>

	<#if results.students??>
		<div class="btn-toolbar">
			<div class="btn-group">
				<#if assignment.collectSubmissions>
					<div class="btn-group">
						<button class="btn btn-default dropdown-toggle" data-toggle="dropdown">
							Submission
							<span class="caret"></span>
						</button>
						<ul class="dropdown-menu">
							<li class="must-have-selected">
								<#assign download_url><@routes.cm2.submissionsZip assignment/></#assign>
								<@fmt.permission_button
									permission='Submission.Read'
									scope=assignment
									action_descr='download submissions'
									classes='form-post'
									href=download_url
									tooltip='Download the submission files for the selected students as a ZIP file'>
										Download submissions
								</@fmt.permission_button>
							</li>
							<li class="must-have-selected">
								<#assign download_url><@routes.cm2.submissionsPdf assignment/></#assign>
								<@fmt.permission_button
									permission='Submission.Read'
									scope=assignment
									action_descr='download submissions'
									classes='download-pdf'
									href=download_url
									tooltip='Download the submission files for the selected students as a PDF file'>
										Download submissions as PDF
								</@fmt.permission_button>
							</li>
							<li class="must-have-selected">
								<#assign deletesubmissionurl><@routes.cm2.deleteSubmissions assignment/></#assign>
								<@fmt.permission_button
									permission='Submission.Delete'
									scope=assignment
									action_descr='delete submissions'
									classes="form-post"
									href=deletesubmissionurl
									tooltip='Delete submissions'>
										Delete submissions
								</@fmt.permission_button>
							</li>
						</ul>
					</div>
				<#else>
					<div class="btn-group">
						<a class="btn btn-default dropdown-toggle disabled use-tooltip" title="This assignment does not collect submissions" data-container="body">
							Submission
							<span class="caret"></span>
						</a>
					</div>
				</#if>

				<#if department.plagiarismDetectionEnabled && assignment.collectSubmissions>
					<div class="btn-group">
						<a class="btn btn-default dropdown-toggle" data-toggle="dropdown">
							Plagiarism
							<span class="caret"></span>
						</a>
						<ul class="dropdown-menu">
							<#if features.turnitin>
								<#if features.turnitinSubmissions>
									<li class="must-have-selected">
										<#assign checkplagiarism_url><@routes.cm2.submitToTurnitin assignment/></#assign>
										<@fmt.permission_button permission='Submission.CheckForPlagiarism' scope=assignment action_descr='check for plagiarism' href=checkplagiarism_url tooltip='Check for plagiarism'>
											Check for plagiarism
										</@fmt.permission_button>
									</li>
								<#else>
									<li class="disabled">
										<@fmt.permission_button permission='Submission.CheckForPlagiarism' scope=assignment action_descr='check for plagiarism - temporarily disabled' tooltip='Check for plagiarism - temporarily disabled'>
											Check for plagiarism
										</@fmt.permission_button>
									</li>
								</#if>
							</#if>
							<li class="must-have-selected">
								<#assign markplagiarised_url><@routes.cm2.plagiarismInvestigation assignment/></#assign>
								<@fmt.permission_button permission='Submission.ManagePlagiarismStatus' scope=assignment action_descr='mark plagiarised' href=markplagiarised_url id="mark-plagiarised-selected-button" tooltip="Toggle whether the selected student submissions are possibly plagiarised" data_attr='data-container=body'>
									Mark plagiarised
								</@fmt.permission_button>
							</li>
						</ul>
					</div>
				<#elseif assignment.collectSubmissions>
					<div class="btn-group">
						<a class="btn btn-default dropdown-toggle disabled use-tooltip" title="Your department does not use plagiarism detection in Tabula" data-container="body">
							Plagiarism
							<span class="caret"></span>
						</a>
					</div>
				<#else>
					<div class="btn-group">
						<a class="btn btn-default dropdown-toggle disabled use-tooltip" title="This assignment does not collect submissions" data-container="body">
							Plagiarism
							<span class="caret"></span>
						</a>
					</div>
				</#if>
			<#if assignment.collectSubmissions && features.markingWorkflows>
					<#if results.mustReleaseForMarking?default(false)>
						<div class="btn-group">
							<a class="btn btn-default dropdown-toggle" data-toggle="dropdown">
								Marking
								<span class="caret"></span>
							</a>
							<!--FIXME Integrate cm2 marking links-->
							<ul class="dropdown-menu">
								<#if assignment.cm2MarkingWorkflow??>
									<li>
										<#assign markers_url><@routes.cm2.assignmentmarkers assignment 'edit'/></#assign>
										<@fmt.permission_button
											permission='Assignment.Update'
											scope=assignment
											action_descr='assign markers'
											href=markers_url>
												Assign markers
										</@fmt.permission_button>
									</li>
								<#elseif assignment.markingWorkflow??>
									<li>
										<#assign markers_url><@routes.coursework.assignMarkers assignment /></#assign>
										<@fmt.permission_button
										permission='Assignment.Update'
										scope=assignment
										action_descr='assign markers'
										href=markers_url>
											Assign markers
										</@fmt.permission_button>
									</li>
								<#else>
									<li class="disabled"><a>Assign markers</a></li>
								</#if>

								<li class="must-have-selected">
									<#if assignment.markingWorkflow??>
										<#assign releaseForMarking_url><@routes.coursework.releaseForMarking assignment /></#assign>
										<@fmt.permission_button
											permission='Submission.ReleaseForMarking'
											scope=assignment
											action_descr='release for marking'
											classes='form-post'
											href=releaseForMarking_url
											id="release-submissions-button"
											tooltip="Release the submissions for marking. First markers will be able to download their submissions."
											data_attr='data-container=body'>
												Release selected for marking
										</@fmt.permission_button>
									<#elseif assignment.cm2MarkingWorkflow??>
										<#assign releaseForMarking_url><@routes.cm2.releaseForMarking assignment /></#assign>
										<@fmt.permission_button
											permission='Submission.ReleaseForMarking'
											scope=assignment
											action_descr='release for marking'
											classes='form-post'
											href=releaseForMarking_url
											id="release-submissions-button"
											tooltip="Release the submissions for marking. First markers will be able to download their submissions."
											data_attr='data-container=body'>
												Release selected for marking
										</@fmt.permission_button>
									</#if>
								</li>
								<li class="must-have-selected">
									<#if assignment.markingWorkflow??>
										<#assign returnForMarking_url><@routes.coursework.returnForMarking assignment /></#assign>
										<@fmt.permission_button
										permission='Submission.ReleaseForMarking'
										scope=assignment
										action_descr='return for marking'
										classes='form-post'
										href=returnForMarking_url
										id="return-submissions-button"
										tooltip="Return the submissions for marking. The last marker in the workflow will be able to update their feedback. You can only return feedback that has not been published."
										data_attr='data-container=body'>
											Return selected for marking
										</@fmt.permission_button>
									<#else>
										<!--FIXME CM2 related link-->
									</#if>
								</li>
							</ul>
						</div>
					<#else>
						<div class="btn-group">
							<a class="btn btn-default dropdown-toggle disabled use-tooltip" title="This assignment does not use a marking workflow that requires assignments to be released for marking" data-container="body">
								Marking
								<span class="caret"></span>
							</a>
						</div>
					</#if>
				</#if>

			<div class="btn-group">
					<a class="btn btn-default dropdown-toggle" data-toggle="dropdown">
						Feedback
						<span class="caret"></span>
					</a>
					<ul class="dropdown-menu">

						<#if assignment.hasWorkflow>
							<li>
								<#assign onlinefeedback_url><@routes.cm2.genericfeedback assignment /></#assign>
								<@fmt.permission_button
								permission='AssignmentFeedback.Manage'
								scope=assignment
								action_descr='add general feedback for all students'
								tooltip='Add general feedback that will be sent to all students'
								href=onlinefeedback_url>
									Generic feedback
								</@fmt.permission_button>
							</li>
						<#else>
							<#if features.feedbackTemplates && assignment.hasFeedbackTemplate>
								<li>
									<a class="long-running use-tooltip"
										 href="<@route.cm2.downloadFeedbackTemplates assignment/>"
										 title="Download feedback templates for all students as a ZIP file."
										 data-container="body">Download templates
									</a>
								</li>
								<li class="divider"></li>
							</#if>
							<li>
								<#assign marks_url><@routes.cm2.addMarks assignment /></#assign>
								<@fmt.permission_button
								permission='AssignmentFeedback.Manage'
								scope=assignment
								action_descr='add marks'
								href=marks_url>
									Add marks
								</@fmt.permission_button>
							</li>
							<li>
								<#assign onlinefeedback_url><@routes.cm2.onlinefeedback assignment /></#assign>
								<@fmt.permission_button
									permission='AssignmentFeedback.Read'
									scope=assignment
									action_descr='manage online feedback'
									href=onlinefeedback_url>
									Online feedback
								</@fmt.permission_button>
							</li>
							<li>
								<#assign feedback_url><@routes.cm2.addFeedback assignment /></#assign>
								<@fmt.permission_button
								permission='AssignmentFeedback.Manage'
								scope=assignment
								action_descr='upload feedback'
								classes='feedback-link'
								href=feedback_url>
									Upload feedback
								</@fmt.permission_button>
							</li>
						</#if>
						<#if assignment.collectMarks>
							<li class="must-have-selected">
								<#assign onlinefeedback_url><@routes.cm2.feedbackAdjustment assignment /></#assign>
								<@fmt.permission_button
									permission='AssignmentFeedback.Manage'
									scope=assignment
									action_descr='make adjustments to feedback'
									classes='form-post'
									tooltip='Apply penalties or make adjustments to mark and grade'
									href=onlinefeedback_url>
										Adjustments
								</@fmt.permission_button>
							</li>
						<#else>
							<li class="disabled"><a class="use-tooltip" data-container="body" title="You cannot adjust marks on an assignment that does not collect marks">Adjustments</a></li>
						</#if>

						<#-- Download / Publish / Delete always available -->
						<li class="must-have-selected">
							<a class="long-running use-tooltip form-post"
								 href="<@routes.cm2.assignmentFeedbackZip assignment />"
								 title="Download the feedback files for the selected students as a ZIP file."
								 data-container="body">Download feedback
							</a>
						</li>
						<#if assignment.canPublishFeedback>
							<li>
								<#assign publishfeedbackurl><@routes.cm2.publishFeedback assignment/></#assign>
								<@fmt.permission_button permission='AssignmentFeedback.Publish' scope=assignment type='a' action_descr='release feedback to students' tooltip="Release feedback to students" href=publishfeedbackurl>
									Publish feedback
								</@fmt.permission_button>
							</li>
						<#else>
							<li class="disabled"><a class="use-tooltip" data-container="body" title="No current feedback to publish, or the assignment is not yet closed.">Publish feedback</a></li>
						</#if>
						<li class="must-have-selected">
							<#assign deletefeedback_url><@routes.cm2.deleteFeedback assignment/></#assign>
							<@fmt.permission_button permission='AssignmentFeedback.Manage' scope=assignment action_descr='delete feedback' classes="form-post" href=deletefeedback_url tooltip='Delete feedback'>
								Delete feedback
							</@fmt.permission_button>
						</li>

						<#if features.queueFeedbackForSits && department.uploadCourseworkMarksToSits>
							<li>
								<#assign uploadToSitsUrl><@routes.cm2.uploadToSits assignment /></#assign>
								<@fmt.permission_button
									permission='AssignmentFeedback.Publish'
									scope=assignment
									action_descr='upload feedback to SITS'
									tooltip='Upload mark and grade to SITS'
									classes='form-post'
									href=uploadToSitsUrl
								>
									Upload to SITS
								</@fmt.permission_button>
							</li>
						</#if>
					</ul>
				</div>
	</div>
			<div class="btn-group">
			<div class="btn-group">
					<a class="btn btn-default dropdown-toggle" data-toggle="dropdown">
						Save As
						<span class="caret"></span>
					</a>
					<ul class="dropdown-menu">
						<li>
							<a class="long-running form-post include-filter" title="Export submissions info as XLSX, for advanced users." href="<@routes.cm2.exportXlsx assignment/>">Excel</a>
						</li>
						<li>
							<a class="long-running form-post include-filter" title="Export submissions info as CSV, for advanced users." href="<@routes.cm2.exportCsv assignment/>">Text (CSV)</a>
						</li>
						<li>
							<a class="long-running form-post include-filter" title="Export submissions info as XML, for advanced users." href="<@routes.cm2.exportXml assignment/>">Text (XML)</a>
						</li>
					</ul>
				</div>
			</div>
		</div>
	</#if>
</div>

<div id="download-pdf-modal" class="modal fade">
	<@modal.wrapper>
		<@modal.header>
			<h3 class="modal-title">Download submissions as PDF</h3>
		</@modal.header>
		<@modal.body>
			<p>There are <span class="count"></span> submissions that have files that are not PDFs (shown below). The download will not include these files.</p>
			<p><a class="form-post btn btn-primary"
						data-href="<@routes.cm2.submissionsPdf assignment/>?download" href="">Download submissions as PDF</a>
			</p>
			<ul class="submissions"></ul>
		</@modal.body>
	</@modal.wrapper>
</div>