<#ftl strip_text=true />
<#--
Just a handy place to create macros for generating URLs to various places, to save time
if we end up changing any of them.

TODO grab values from the Routes object in code, as that's pretty equivalent and
	we're repeating ourselves here. OR expose Routes directly.

-->

<#macro _u page context=component.context?default('/coursework')>
	<@url context=context page=page />
</#macro>

<#macro home><@_u page="/" /></#macro>
<#macro departmenthome department><@_u page="/admin/department/${department.code}/" /></#macro>
<#macro modulehome module><@_u page="/admin/module/${module.code}/" /></#macro>
<#macro depthome module><@_u page="/admin/department/${module.department.code}/#module-${module.code}" /></#macro>
<#macro moduleperms module><@_u page="/module/${module.code}/permissions" context="/admin" /></#macro>

<#macro marksmanagementdepts><@_u page="/admin/marksmanagement/departments" /></#macro>

<#macro ratefeedback feedback><#compress>
    <#assign assignment=feedback.assignment />
    <#assign module=assignment.module />
    <@_u page="/module/${module.code}/${assignment.id}/rate" />
</#compress></#macro>
<#macro assignmentdelete assignment><@_u page="/admin/module/${assignment.module.code}/assignments/${assignment.id}/delete"/></#macro>
<#macro assignmentedit assignment><@_u page="/admin/module/${assignment.module.code}/assignments/${assignment.id}/edit"/></#macro>

<#macro feedbackSummary assignment studentid><@_u page="/admin/module/${assignment.module.code}/assignments/${assignment.id}/feedback/summary/${studentid}"/></#macro>
<#macro feedbackAudit assignment studentid><@_u page="/admin/module/${assignment.module.code}/assignments/${assignment.id}/audit/${studentid}"/></#macro>
<#macro plagiarismInvestigation assignment><@_u page='/admin/module/${assignment.module.code}/assignments/${assignment.id}/submissionsandfeedback/mark-plagiarised'/></#macro>
<#macro onlinefeedback assignment><@_u page="/admin/module/${assignment.module.code}/assignments/${assignment.id}/feedback/online"/></#macro>
<#macro feedbackAdjustment assignment><@_u page="/admin/module/${assignment.module.code}/assignments/${assignment.id}/feedback/adjustments"/></#macro>
<#macro feedbackAdjustmentForm assignment studentid><@_u page="/admin/module/${assignment.module.code}/assignments/${assignment.id}/feedback/adjustments/${studentid}"/></#macro>

<#macro feedbackBulkAdjustment assignment><@_u page="/admin/module/${assignment.module.code}/assignments/${assignment.id}/feedback/bulk-adjustment"/></#macro>
<#macro feedbackBulkAdjustmentTemplate assignment><@_u page="/admin/module/${assignment.module.code}/assignments/${assignment.id}/feedback/bulk-adjustment/template"/></#macro>

<#macro genericfeedback assignment><@_u page="/admin/module/${assignment.module.code}/assignments/${assignment.id}/feedback/generic"/></#macro>
<#macro markerOnlinefeedback assignment marker><@_u page="/admin/module/${assignment.module.code}/assignments/${assignment.id}/marker/${marker.warwickId}/feedback/online"/></#macro>
<#macro onlinefeedbackform assignment studentid><@_u page="/admin/module/${assignment.module.code}/assignments/${assignment.id}/feedback/online/${studentid}"/></#macro>
<#macro markerOnlinefeedbackform assignment studentid marker><@_u page="/admin/module/${assignment.module.code}/assignments/${assignment.id}/marker/${marker.warwickId}/feedback/online/${studentid}"/></#macro>
<#macro markerModerationform assignment studentid marker><@_u page="/admin/module/${assignment.module.code}/assignments/${assignment.id}/marker/${marker.warwickId}/feedback/online/moderation/${studentid}"/></#macro>
<#macro generateGradesForMarks assignment><@_u page="/admin/module/${assignment.module.code}/assignments/${assignment.id}/generate-grade"/></#macro>
<#macro uploadToSits assignment><@_u page="/admin/module/${assignment.module.code}/assignments/${assignment.id}/upload-to-sits"/></#macro>

<#macro genericfeedback assignment><@_u page="/admin/module/${assignment.module.code}/assignments/${assignment.id}/feedback/generic"/></#macro>
<#macro markerFeedbackFiles assignment markerFeedback><@_u page="/admin/module/${assignment.module.code}/assignments/${assignment.id}/marker/feedback/download/${markerFeedback.id}/feedback-${markerFeedback.feedback.universityId}.zip"/></#macro>
<#macro markerFeedbackFilesDownload markerFeedback><@_u page="/admin/module/${markerFeedback.feedback.assignment.module.code}/assignments/${markerFeedback.feedback.assignment.id}/marker/${markerFeedback.markerUser.warwickId}/feedback/download/${markerFeedback.id}/" /></#macro>

<#macro adminFeedbackZip assignment feedback attachmentExtension><@_u page='/admin/module/${assignment.module.code}/assignments/${assignment.id}/feedback/download/${feedback.id}/feedback-${feedback.universityId}.${attachmentExtension}'/></#macro>

<#macro markingCompleted assignment marker><@_u page="/admin/module/${assignment.module.code}/assignments/${assignment.id}/marker/${marker.warwickId}/marking-completed" /></#macro>
<#macro markingCompleted assignment marker nextRoleName><@_u page="/admin/module/${assignment.module.code}/assignments/${assignment.id}/marker/${marker.warwickId}/marking-completed?nextStageRole=${nextRoleName}" /></#macro>
<#macro markingUncompleted assignment marker previousRoleName><@_u page="/admin/module/${assignment.module.code}/assignments/${assignment.id}/marker/${marker.warwickId}/marking-uncompleted?previousStageRole=${previousRoleName}" /></#macro>
<#macro bulkApproval assignment marker><@_u page="/admin/module/${assignment.module.code}/assignments/${assignment.id}/marker/${marker.warwickId}/moderation/bulk-approve" /></#macro>

<#macro enrolment module academicYear><@_u page="/admin/module/${module.code}/assignments/enrolment/${academicYear.startYear?c}"/></#macro>

<#macro archiveAssignment assignment><@_u page="/admin/module/${assignment.module.code}/assignments/${assignment.id}/archive" /></#macro>

<#macro createAssignment module><@_u page="/admin/module/${module.code}/assignments/new" /></#macro>
<#macro copyModuleAssignments module><@_u page="/admin/module/${module.code}/copy-assignments" /></#macro>
<#macro archiveModuleAssignments module><@_u page="/admin/module/${module.code}/archive-assignments" /></#macro>

<#macro addMarks assignment><@_u page="/admin/module/${assignment.module.code}/assignments/${assignment.id}/marks" /></#macro>
<#macro addFeedback assignment><@_u page="/admin/module/${assignment.module.code}/assignments/${assignment.id}/feedback/batch" /></#macro>

<#macro listmarkersubmissions assignment marker><@_u page="/admin/module/${assignment.module.code}/assignments/${assignment.id}/marker/${marker.warwickId}/list"/></#macro>
<#macro downloadmarkersubmissions assignment marker><@_u page="/admin/module/${assignment.module.code}/assignments/${assignment.id}/marker/${marker.warwickId}/submissions.zip"/></#macro>
<#macro downloadMarkerSubmissionsAsPdf assignment marker><@_u page="/admin/module/${assignment.module.code}/assignments/${assignment.id}/marker/${marker.warwickId}/submissions.pdf"/></#macro>
<#macro downloadfirstmarkerfeedback assignment marker><@_u page="/admin/module/${assignment.module.code}/assignments/${assignment.id}/marker/${marker.warwickId}/firstmarker/feedback.zip"/></#macro>
<#macro downloadsecondmarkerfeedback assignment marker><@_u page="/admin/module/${assignment.module.code}/assignments/${assignment.id}/marker/${marker.warwickId}/secondmarker/feedback.zip"/></#macro>
<#macro uploadmarkerfeedback assignment marker><@_u page="/admin/module/${assignment.module.code}/assignments/${assignment.id}/marker/${marker.warwickId}/feedback"/></#macro>
<#macro downloadMarkerFeedback assignment feedback marker><@_u page='/admin/module/${assignment.module.code}/assignments/${assignment.id}/marker/${marker.warwickId}/feedback/download/${feedback.id}/feedback-${feedback.feedback.universityId}.zip'/></#macro>
<#macro markeraddmarks assignment marker><@_u page="/admin/module/${assignment.module.code}/assignments/${assignment.id}/marker/${marker.warwickId}/marks"/></#macro>

<#macro assignmentsubmissionsandfeedback assignment><@_u page="/admin/module/${assignment.module.code}/assignments/${assignment.id}/list"/></#macro>
<#macro assignmentsubmissionsandfeedbacktable assignment><@_u page="/admin/module/${assignment.module.code}/assignments/${assignment.id}/table"/></#macro>
<#macro assignmentsubmissionsandfeedbacksummary assignment><@_u page="/admin/module/${assignment.module.code}/assignments/${assignment.id}/summary"/></#macro>

<#macro downloadSingleSubmission submission filename><@_u page="/admin/module/${submission.assignment.module.code}/assignments/${submission.assignment.id}/submissions/download/${submission.id}/${filename?url}?single=true"/></#macro>

<#macro assignMarkers assignment><@_u page="/admin/module/${assignment.module.code}/assignments/${assignment.id}/assign-markers" /></#macro>
<#macro assignMarkersSmallGroups assignment><@url context="/groups" page="/admin/marker-allocation/${assignment.id}" /></#macro>
<#macro releaseForMarking assignment><@_u page="/admin/module/${assignment.module.code}/assignments/${assignment.id}/submissionsandfeedback/release-submissions" /></#macro>
<#macro returnForMarking assignment><@_u page="/admin/module/${assignment.module.code}/assignments/${assignment.id}/submissionsandfeedback/return-submissions" /></#macro>

<#macro onlinemarking assignment><@_u page="/admin/module/${assignment.module.code}/assignments/${assignment.id}/feedback/online" /></#macro>
<#macro onlinemarkingform assignment student><@_u page="/admin/module/${assignment.module.code}/assignments/${assignment.id}/feedback/online/${student.universityId}" /></#macro>

<#macro feedbackadd assignment><@_u page="/admin/module/${assignment.module.code}/assignments/${assignment.id}/feedback/add" /></#macro>
<#macro feedbackdelete assignment><@_u page="/admin/module/${assignment.module.code}/assignments/${assignment.id}/feedback/delete" /></#macro>
<#macro markstemplate assignment><@_u page="/admin/module/${assignment.module.code}/assignments/${assignment.id}/marks-template" /></#macro>
<#macro markermarkstemplate assignment marker><@_u page="/admin/module/${assignment.module.code}/assignments/${assignment.id}/marker/${marker.warwickId}/marks-template" /></#macro>
<#macro extensions assignment><@_u page="/admin/module/${assignment.module.code}/assignments/${assignment.id}/extensions" /></#macro>
<#macro extensionreviewattachment assignment userid filename><@_u page="/admin/module/${assignment.module.code}/assignments/${assignment.id}/extensions/review-request/${userid}/supporting-file/${filename?url}" /></#macro>
<#macro extensionrequestattachment assignment filename><@_u page="/module/${assignment.module.code}/${assignment.id}/extension/supporting-file/${filename?url}" /></#macro>

<#macro extensionsettings department><@_u page="/admin/department/${department.code}/settings/extensions" /></#macro>
<#macro manage_extensions department><@_u page="/admin/department/${department.code}/manage/extensions" /></#macro>
<#macro extensionreviewrequest assignment uniId><@_u page="/admin/module/${assignment.module.code}/assignments/${assignment.id}/extensions/review-request/${uniId}" /></#macro>
<#macro extensionadd assignment uniId><@_u page="/admin/module/${assignment.module.code}/assignments/${assignment.id}/extensions/add?universityId=${uniId}" /></#macro>
<#macro extensionedit assignment uniId><@_u page="/admin/module/${assignment.module.code}/assignments/${assignment.id}/extensions/edit/${uniId}" /></#macro>
<#macro extensiondelete assignment uniId><@_u page="/admin/module/${assignment.module.code}/assignments/${assignment.id}/extensions/delete/${uniId}" /></#macro>
<#macro extensiondetail assignment uniId><@_u page="/admin/module/${assignment.module.code}/assignments/${assignment.id}/extensions/detail/${uniId}" /></#macro>

<#macro feedbacktemplates department><@_u page="/admin/department/${department.code}/settings/feedback-templates" /></#macro>
<#macro feedbacktemplateedit department feedbacktemplate><@_u page="/admin/department/${department.code}/settings/feedback-templates/edit/${feedbacktemplate.id}" /></#macro>
<#macro feedbacktemplatedownload department feedbacktemplate><@_u page="/admin/department/${department.code}/settings/feedback-templates/download/${feedbacktemplate.id}/${feedbacktemplate.attachment.name}" /></#macro>
<#macro feedbacktemplatedelete department feedbacktemplate><@_u page="/admin/department/${department.code}/settings/feedback-templates/delete/${feedbacktemplate.id}" /></#macro>

<#macro markingworkflowlist department><@_u page="/admin/department/${department.code}/markingworkflows" /></#macro>
<#macro markingworkflowadd department><@markingworkflowlist department />/add</#macro>
<#macro markingworkflowedit scheme><@markingworkflowlist scheme.department />/edit/${scheme.id}</#macro>
<#macro markingworkflowreplace scheme><@markingworkflowlist scheme.department />/edit/${scheme.id}/replace</#macro>
<#macro markingworkflowdelete scheme><@markingworkflowlist scheme.department />/delete/${scheme.id}</#macro>

<#macro feedbackreport department><@_u page="/admin/department/${department.code}/reports/feedback" /></#macro>

<#macro setupSitsAssignments department><@_u page="/admin/department/${department.code}/setup-assignments" /></#macro>
<#macro copyDepartmentsAssignments department><@_u page="/admin/department/${department.code}/copy-assignments" /></#macro>
<#macro archiveDepartmentsAssignments department><@_u page="/admin/department/${department.code}/archive-assignments" /></#macro>

<#macro displaysettings department><@_u page="/department/${department.code}/settings/display" context="/admin" /></#macro>
<#macro notificationsettings department><@_u page="/department/${department.code}/settings/notification" context="/admin" /></#macro>

<#macro assignment_in_profile assignment student><@_u page="/module/${assignment.module.code}/${assignment.id}/${student.universityId}"/></#macro>

<#-- non admin -->
<#macro assignment assignment><@_u page="/module/${assignment.module.code}/${assignment.id}"/></#macro>
<#macro extensionRequest assignment><@_u page="/module/${assignment.module.code}/${assignment.id}/extension"/></#macro>
<#macro assignmentreceipt assignment><@_u page="/module/${assignment.module.code}/${assignment.id}/resend-receipt"/></#macro>
<#macro assignmentrequestaccess assignment><@_u page="/module/${assignment.module.code}/${assignment.id}/request-access"/></#macro>
<#macro feedbackPdf assignment feedback><@_u page="/module/${assignment.module.code}/${assignment.id}/${feedback.universityId}/feedback.pdf"/></#macro>
<#macro submissionReceiptPdf submission><@_u page="/module/${submission.assignment.module.code}/${submission.assignment.id}/submission-receipt.pdf"/></#macro>
<#macro submissionReceiptPdf_in_profile assignment><@_u page="/module/${submission.assignment.module.code}/${submission.assignment.id}/${submission.universityId}/submission-receipt.pdf"/></#macro>

<#macro feedbackZip feedback><@_u page="/module/${feedback.assignment.module.code}/${feedback.assignment.id}/all/feedback.zip" /></#macro>
<#macro feedbackZip_in_profile feedback><@_u page="/module/${feedback.assignment.module.code}/${feedback.assignment.id}/${feedback.universityId}/all/feedback.zip" /></#macro>
<#macro feedbackAttachment feedback attachment><@_u page="/module/${feedback.assignment.module.code}/${feedback.assignment.id}/get/${attachment.name?url}"/></#macro>
<#macro feedbackAttachment_in_profile feedback attachment><@_u page="/module/${feedback.assignment.module.code}/${feedback.assignment.id}/${feedback.universityId}/get/${attachment.name?url}"/></#macro>

<#macro submissionAttachment submission attachment><@_u page="/module/${submission.assignment.module.code}/${submission.assignment.id}/attachment/${attachment.name?url}" /></#macro>
<#macro submissionAttachment_in_profile submission attachment><@_u page="/module/${submission.assignment.module.code}/${submission.assignment.id}/${submission.universityId}/attachment/${attachment.name?url}" /></#macro>