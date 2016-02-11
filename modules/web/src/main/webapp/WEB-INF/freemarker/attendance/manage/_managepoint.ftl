<@form.labelled_row "name" "Name">
	<@f.input path="name" cssClass="input-block-level"/>
</@form.labelled_row>

<div class="dateTimePair">
	<#if command.pointStyle.dbValue == "week">

		<@form.labelled_row "startWeek" "Start">
			<@f.select path="startWeek" cssClass="startDateTime selectOffset">
				<#list 1..52 as week>
					<@f.option value="${week}"><@fmt.monitoringPointWeeksFormat week week command.academicYear command.department /></@f.option>
				</#list>
			</@f.select>
			<@fmt.help_popover id="startWeek" content="You cannot mark a point as attended or missed (unauthorised) before its start date" />
		</@form.labelled_row>

		<@form.labelled_row "endWeek" "End">
			<@f.select path="endWeek" cssClass="endDateTime selectOffset">
				<#list 1..52 as week>
					<@f.option value="${week}"><@fmt.monitoringPointWeeksFormat week week command.academicYear command.department /></@f.option>
				</#list>
			</@f.select>
			<@fmt.help_popover id="endWeek" content="A warning will appear for unrecorded attendance after its end date" />
		</@form.labelled_row>

	<#else>

		<@form.labelled_row "startDate" "Start">
			<@f.input type="text" path="startDate" cssClass="input-medium date-picker startDateTime" placeholder="Pick the start date" />
			<input class="endoffset" type="hidden" data-end-offset="0" />
			<@fmt.help_popover id="startDate" content="You cannot mark a point as attended or missed (unauthorised) before its start date" />
		</@form.labelled_row>

		<@form.labelled_row "endDate" "End">
			<@f.input type="text" path="endDate" cssClass="input-medium date-picker endDateTime" placeholder="Pick the end date" />
			<@fmt.help_popover id="endDate" content="A warning will appear for unrecorded attendance after its end date" />
		</@form.labelled_row>

	</#if>
</div>

<@form.labelled_row "pointType" "Type">
	<@form.label clazz="radio" checkbox=true>
		<@f.radiobutton path="pointType" value="standard" />
		Standard
		<@fmt.help_popover id="pointType-standard" content="This is a basic monitoring point which will create a register for you to mark monitored attendance" />
	</@form.label>
	<@form.label clazz="radio" checkbox=true>
		<@f.radiobutton path="pointType" value="meeting" />
		Meeting
		<@fmt.help_popover id="pointType-meeting" content="This monitoring point will be marked as 'attended' if there is a record in Tabula of a meeting taking place between the start and end dates" />
	</@form.label>
	<#if features.attendanceMonitoringSmallGroupPointType>
		<@form.label clazz="radio" checkbox=true>
			<@f.radiobutton path="pointType" value="smallGroup" />
			Teaching event
			<@fmt.help_popover id="pointType-smallGroup" content="This monitoring point will be marked as 'attended' if the student attends a small group teaching event recorded in Tabula between the start and end dates" />
		</@form.label>
	</#if>
	<#if features.attendanceMonitoringAssignmentSubmissionPointType>
		<@form.label clazz="radio" checkbox=true>
			<@f.radiobutton path="pointType" value="assignmentSubmission" />
			Coursework
			<@fmt.help_popover id="pointType-assignmentSubmission" content="This monitoring point will be marked as 'attended' if the student submits coursework via Tabula to an assignment with a close date between the start and end dates" />
		</@form.label>
	</#if>
</@form.labelled_row>

<#if features.attendanceMonitoringMeetingPointType>

	<#assign meetingRelationshipsStrings = extractParam(command.meetingRelationships, 'urlPart') />
	<#assign meetingFormatsStrings = extractParam(command.meetingFormats, 'code') />
	<div class="pointTypeOption meeting row-fluid" <#if ((command.pointType.dbValue)!'null') != 'meeting'>style="display:none"</#if>>
		<div class="span5">
			<@form.labelled_row "meetingRelationships" "Meeting with">
				<#list command.department.displayedStudentRelationshipTypes as relationship>
					<@form.label checkbox=true>
						<input type="checkbox" name="meetingRelationships" id="meetingRelationships-${relationship.urlPart}" value="${relationship.urlPart}" <#if meetingRelationshipsStrings?seq_contains(relationship.urlPart)>checked</#if> />
						${relationship.agentRole?capitalize}
						<@fmt.help_popover id="meetingRelationships-${relationship.urlPart}" content="This monitoring point will be marked as 'attended' when a meeting record is created or approved by the student's ${relationship.agentRole?capitalize}" />
					</@form.label>
				</#list>
			</@form.labelled_row>

			<@form.labelled_row "meetingQuantity" "Number of meetings">
				<@f.input path="meetingQuantity" cssClass="input-mini"/>
				<@fmt.help_popover id="meetingQuantity" content="The student must have this many meetings between the start and end dates in order to meet this monitoring point" />
			</@form.labelled_row>
		</div>

		<div class="span6">
			<@form.labelled_row path="meetingFormats" label="Meeting formats" helpPopover="Only selected meeting formats will count towards this monitoring point">
				<#list allMeetingFormats as format>
					<@form.label checkbox=true>
						<input type="checkbox" name="meetingFormats" id="meetingFormats-${format.code}" value="${format.code}" <#if meetingFormatsStrings?seq_contains(format.code)>checked</#if> />
						${format.description}
					</@form.label>
				</#list>
			</@form.labelled_row>
		</div>
	</div>

</#if>

<#if features.attendanceMonitoringSmallGroupPointType>

<div class="pointTypeOption smallGroup row-fluid" <#if ((command.pointType.dbValue)!'null') != 'smallGroup'>style="display:none"</#if>>

	<div class="module-choice">
		<@form.labelled_row "smallGroupEventModules" "Modules">
			<@form.label clazz="radio" checkbox=true>
				<input type="radio" <#if (command.anySmallGroupEventModules)>checked </#if> value="true" name="isAnySmallGroupEventModules"/>
				Any
				<@fmt.help_popover id="isAnySmallGroupEventModules" content="Attendance at any module recorded in Tabula will count towards this monitoring point" />
			</@form.label>

			<@form.label clazz="radio pull-left specific" checkbox=true>
				<input class="specific" type="radio" <#if (!command.anySmallGroupEventModules)>checked </#if> value="false" name="isAnySmallGroupEventModules"/>
				Specific
			</@form.label>
			<div class="module-search input-append">
				<input class="module-search-query smallGroup" type="text" value="" placeholder="Search for a module" />
				<span class="add-on"><i class="icon-search"></i></span>
			</div>
			<@fmt.help_popover id="isAnySmallGroupEventModules" content="Attendance at any of the specified modules recorded in Tabula will count towards this monitoring point" />
			<div class="modules-list">
				<input type="hidden" name="_smallGroupEventModules" value="false" />
				<ol>
					<#list command.smallGroupEventModules![] as module>
						<li>
							<input type="hidden" name="smallGroupEventModules" value="${module.id}" />
							<#if command.moduleHasSmallGroups(module)><i class="icon-fixed-width"></i><#else><i class="icon-fixed-width icon-exclamation-sign" title="This module has no small groups set up in Tabula"></i></#if><span title="<@fmt.module_name module false />"><@fmt.module_name module false /></span><button class="btn btn-danger btn-mini"><i class="icon-remove"></i></button>
						</li>
					</#list>

				</ol>

			</div>

		</@form.labelled_row>

	</div>


	<@form.labelled_row "smallGroupEventQuantityAll" "Number of events">
		<input class="input-mini" type="text" <#if (command.smallGroupEventQuantity?? && command.smallGroupEventQuantity > 0)>value="${command.smallGroupEventQuantity}"</#if> name="smallGroupEventQuantity" />
		<@fmt.help_popover id="smallGroupEventQuantity" content="The student must have attended this many events for any of the specified modules between the start and end dates in order to meet this monitoring point" />
	</@form.labelled_row>

</div>

</#if>

<#if features.attendanceMonitoringAssignmentSubmissionPointType>

<div class="pointTypeOption assignmentSubmission row-fluid" <#if ((command.pointType.dbValue)!'null') != 'assignmentSubmission'>style="display:none"</#if>>

	<@form.labelled_row "assignmentSubmissionType" "Assignments">
		<@form.label clazz="radio" checkbox=true>
			<@f.radiobutton path="assignmentSubmissionType" value="any" />
			Any module or assignment
			<@fmt.help_popover id="isSpecificAssignmentsFalse" content="Submission to any assignment for any module with a close date between the start and end dates will count towards this monitoring point" />
		</@form.label>
		<@form.label clazz="radio" checkbox=true>
			<@f.radiobutton path="assignmentSubmissionType" value="modules" />
			By module
			<@fmt.help_popover id="isSpecificAssignmentsFalse" content="Submission to any assignment for the specified modules with a close date between the start and end dates will count towards this monitoring point" />
		</@form.label>
		<@form.label clazz="radio" checkbox=true>
			<@f.radiobutton path="assignmentSubmissionType" value="assignments" />
			Specific assignments
			<@fmt.help_popover id="isSpecificAssignmentsTrue" content="Submissions to the specified assignments that have a close date between the start and end dates will count towards this monitoring point" />
		</@form.label>
	</@form.labelled_row>

	<div class="assignmentSubmissionType-any" <#if command.assignmentSubmissionType != 'any'>style="display:none"</#if>>
		<@form.labelled_row "assignmentSubmissionTypeAnyQuantity" "Number of assignments">
			<input class="input-mini" type="text" <#if (command.assignmentSubmissionTypeAnyQuantity?? && command.assignmentSubmissionTypeAnyQuantity > 0)>value="${command.assignmentSubmissionTypeAnyQuantity}"</#if> name="assignmentSubmissionTypeAnyQuantity" />
			<@fmt.help_popover id="assignmentSubmissionTypeAnyQuantity" content="The student must submit coursework to this many assignments for any module with a close date between the start and end dates in order to meet this monitoring point" />
		</@form.labelled_row>
	</div>

	<div class="assignmentSubmissionType-modules" <#if command.assignmentSubmissionType != 'modules'>style="display:none"</#if>>
		<div class="module-choice">
			<@form.labelled_row "assignmentSubmissionModules" "">
				<div class="module-search input-append">
					<input class="module-search-query assignment" type="text" value="" placeholder="Search for a module"/>
					<span class="add-on"><i class="icon-search"></i></span>
				</div>
				<div class="modules-list">
					<input type="hidden" name="_assignmentSubmissionModules" value="false" />
					<ol>
						<#list command.assignmentSubmissionModules![] as module>
							<li>
								<input type="hidden" name="assignmentSubmissionModules" value="${module.id}" />
								<#if command.moduleHasAssignments(module)><i class="icon-fixed-width"></i><#else><i class="icon-fixed-width icon-exclamation-sign" title="This module has no assignments set up in Tabula"></i></#if><span title="<@fmt.module_name module false />"><@fmt.module_name module false /></span><button class="btn btn-danger btn-mini"><i class="icon-remove"></i></button>
							</li>
						</#list>
					</ol>
				</div>
			</@form.labelled_row>
		</div>

		<@form.labelled_row "assignmentSubmissionTypeModulesQuantity" "Number of assignments">
			<input class="input-mini" type="text" <#if (command.assignmentSubmissionTypeModulesQuantity?? && command.assignmentSubmissionTypeModulesQuantity > 0)>value="${command.assignmentSubmissionTypeModulesQuantity}"</#if> name="assignmentSubmissionTypeModulesQuantity" />
			<@fmt.help_popover id="assignmentSubmissionTypeModulesQuantity" content="The student must submit coursework to this many assignments for any of the specified modules with a close date between the start and end dates in order to meet this monitoring point" />
		</@form.labelled_row>
	</div>

	<div class="assignmentSubmissionType-assignments" <#if command.assignmentSubmissionType != 'assignments'>style="display:none"</#if>>
		<div class="assignment-choice">
			<@form.labelled_row "assignmentSubmissionAssignments" "">
				<div class="assignment-search input-append">
					<input class="assignment-search-query" type="text" value="" placeholder="Search for an assignment"/>
					<span class="add-on"><i class="icon-search"></i></span>
				</div>
				<div class="assignments-list">
					<input type="hidden" name="_assignmentSubmissionAssignments" value="false" />
					<ol>
						<#list command.assignmentSubmissionAssignments![] as assignment>
							<li>
								<input type="hidden" name="assignmentSubmissionAssignments" value="${assignment.id}" />
								<span title="<@fmt.assignment_name assignment false />"><@fmt.assignment_name assignment false /></span><button class="btn btn-danger btn-mini"><i class="icon-remove"></i></button>
							</li>
						</#list>
					</ol>
				</div>
			</@form.labelled_row>

			<@form.labelled_row "assignmentSubmissionDisjunction" "">
				<@form.label clazz="radio" checkbox=true>
					<input name="isAssignmentSubmissionDisjunction" type="radio" value="true" <#if command.isAssignmentSubmissionDisjunction()>checked</#if>>
					Any
					<@fmt.help_popover id="isAssignmentSubmissionDisjunctionTrue" content="The student must submit coursework to any specified assignment with a close date between the start and end dates in order to meet this monitoring point" />
				</@form.label>
				<@form.label clazz="radio" checkbox=true>
					<input name="isAssignmentSubmissionDisjunction" type="radio" value="false" <#if !command.isAssignmentSubmissionDisjunction()>checked</#if>>
					All
					<@fmt.help_popover id="isAssignmentSubmissionDisjunctionFalse" content="The student must submit coursework to all of the specified assignments with a close date between the start and end dates in order to meet this monitoring point" />
				</@form.label>
			</@form.labelled_row>
		</div>
	</div>

</div>

<@spring.bind path="command">
	<#if status.error && status.errorCodes?seq_contains("attendanceMonitoringPoint.overlaps")>
		<div class="alert alert-warning">
			There is already a monitoring point that will be met by these criteria.
			If you create this point, it is possible that the same event will mark two points as 'attended'.
			We recommend that you change the settings for this point.
		</div>
		<#assign hasOverlap = true />
	</#if>
</@spring.bind>

<script>
	jQuery(function($) {
		// Show relavant extra options when changing assignment type
		var $specificAssignmentInput = $('form input[name=assignmentSubmissionType]');
		if ($specificAssignmentInput.length > 0) {
			var showAssignmentOptions = function() {
				var value = $('form input[name=assignmentSubmissionType]:checked').val();
				$('.pointTypeOption.assignmentSubmission [class^=assignmentSubmissionType-]').hide();
				$('.pointTypeOption.assignmentSubmission .assignmentSubmissionType-' + value).show();

			};
			$specificAssignmentInput.on('click', showAssignmentOptions);
			showAssignmentOptions();
		}

		// Show relavant extra options when changing point type
		var $pointTypeInput = $('form input[name=pointType]');
		if ($pointTypeInput.length > 0) {
			var showOptions = function() {
				var value = $('form input[name=pointType]:checked').val();
				$('.pointTypeOption').hide();
				if (value != undefined && value.length > 0) {
					$('.pointTypeOption.' + value).show();
				}
			};
			$pointTypeInput.on('click', showOptions);
			showOptions();
		}

		Attendance.bindModulePickers();
		Attendance.bindAssignmentPickers();
	});
</script>

</#if>