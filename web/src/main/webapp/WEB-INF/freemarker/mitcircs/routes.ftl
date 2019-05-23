<#ftl strip_text=true />
<#--
Just a handy place to create macros for generating URLs to various places, to save time
if we end up changing any of them.
TODO grab values from the Routes object in code, as that's pretty equivalent and
   we're repeating ourselves here. OR expose Routes directly.
-->
<#macro _u page context='/mitcircs'><@url context=context page=page /></#macro>

<#macro home><@_u page="/" /></#macro>
<#macro adminhome department academicYear="">
  <#if academicYear?has_content>
    <@_u page="/admin/${department.code}/${academicYear.startYear?c}" />
  <#else>
    <@_u page="/admin/${department.code}" />
  </#if>
</#macro>
<#macro studenthome student><@_u context='/profiles' page="/view/${student.universityId}/personalcircs"/></#macro>

<#macro newSubmission student><@_u context='/profiles' page="/view/${student.universityId}/personalcircs/mitcircs/new" /></#macro>
<#macro editSubmission submission><@_u context='/profiles' page="/view/${submission.student.universityId}/personalcircs/mitcircs/edit/${submission.key?c}" /></#macro>
<#macro pendingEvidence submission><@_u context='/profiles' page="/view/${submission.student.universityId}/personalcircs/mitcircs/pendingevidence/${submission.key?c}" /></#macro>

<#-- Helper for new/edit -->
<#macro affectedAssessments student><@_u context='/profiles' page="/view/${student.universityId}/personalcircs/affected-assessments" /></#macro>

<#-- View submission (as the student) -->
<#macro viewSubmission submission><@_u context='/profiles' page="/view/${submission.student.universityId}/personalcircs/mitcircs/view/${submission.key?c}" /></#macro>

<#-- Review submission (as the MCO) -->
<#macro reviewSubmission submission><@_u page = "/submission/${submission.key?c}" /></#macro>
<#macro sensitiveEvidence submission><@_u page="/submission/${submission.key?c}/sensitiveevidence" /></#macro>
<#macro readyForPanel submission><@_u page="/submission/${submission.key?c}/ready" /></#macro>
<#macro notes submission><@_u page="/submission/${submission.key?c}/notes" /></#macro>
<#macro renderNoteAttachment note file><@_u page="/submission/${note.submission.key?c}/notes/${note.id}/supporting-file/${file.name}" /></#macro>
<#macro deleteNote note><@_u page="/submission/${note.submission.key?c}/notes/${note.id}/delete" /></#macro>

<#-- Manage panels (as the MCO) -->
<#macro createPanel department academicYear="">
  <#if academicYear?has_content>
    <@_u page="/admin/${department.code}/${academicYear.startYear?c}/panel/create" />
  <#else>
    <@_u page="/admin/${department.code}/panel/create" />
  </#if>
</#macro>

<#-- These get posted to from both the view (as the student) and the review (as the MCO) -->
<#macro messages submission><@_u page="/submission/${submission.key?c}/messages" /></#macro>
<#macro renderMessageAttachment message file><@_u page="/submission/${message.submission.key?c}/messages/${message.id}/supporting-file/${file.name}" /></#macro>
<#macro renderAttachment submission file><@_u page="/submission/${submission.key?c}/supporting-file/${file.name}" /></#macro>

<#macro dummyDataGeneration department><@_u page="/admin/${department.code}/data-generation" /></#macro>