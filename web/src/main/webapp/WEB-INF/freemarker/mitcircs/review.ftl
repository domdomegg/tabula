<#import "*/mitcircs_components.ftl" as components />
<#import "/WEB-INF/freemarker/modal_macros.ftl" as modal />

<#escape x as x?html>
  <h1>MIT-${submission.key}</h1>
  <section class="mitcircs-details">
    <div class="row">
      <div class="col-sm-6 col-md-7">
        <@components.detail label="State" condensed=true>${submission.state.description}</@components.detail>

        <#-- Identity information about the student -->
        <#assign student = submission.student />
        <@components.detail label="Name" condensed=true>${student.fullName}</@components.detail>
        <@components.detail label="University ID" condensed=true>${student.universityId}</@components.detail>
        <#if student.email??><@components.detail label="Email" condensed=true>${student.email}</@components.detail></#if>

        <#if student.mostSignificantCourseDetails??>
          <#assign studentCourseDetails = student.mostSignificantCourseDetails />
          <@components.detail label="Course" condensed=true>${studentCourseDetails.course.name}</@components.detail>

          <#if studentCourseDetails.latestStudentCourseYearDetails??>
            <#assign studentCourseYearDetails = studentCourseDetails.latestStudentCourseYearDetails />

            <#if studentCourseYearDetails.yearOfStudy??>
              <@components.detail label="Year of study" condensed=true>${studentCourseYearDetails.yearOfStudy}</@components.detail>
            </#if>

            <#if studentCourseYearDetails.modeOfAttendance??>
              <@components.detail label="Mode of study" condensed=true>${studentCourseYearDetails.modeOfAttendance.fullNameAliased}</@components.detail>
            </#if>
          </#if>
        </#if>

        <@components.detail label="Issue type" condensed=true><@components.enumListWithOther submission.issueTypes submission.issueTypeDetails!"" /></@components.detail>
        <@components.detail label="Start date" condensed=true><@fmt.date date=submission.startDate includeTime=false /></@components.detail>
        <@components.detail label="End date" condensed=true>
          <#if submission.endDate??><@fmt.date date=submission.endDate includeTime=false /><#else><span class="very-subtle">Issue ongoing</span></#if>
        </@components.detail>
        <#if submission.relatedSubmission??>
          <@components.detail label="Related submission" condensed=true>
            <a href="<@routes.mitcircs.viewSubmission submission.relatedSubmission />">
              MIT-${submission.relatedSubmission.key}
              <@components.enumListWithOther submission.relatedSubmission.issueTypes submission.relatedSubmission.issueTypeDetails!"" />
            </a>
          </@components.detail>
        </#if>
        <#if submission.contacted>
          <@components.detail "Discussed submission with">
            <@components.enumListWithOther submission.contacts submission.contactOther!"" />
          </@components.detail>
        <#else>
          <@components.detail "Reason for not discussing submission">
            ${submission.noContactReason}
          </@components.detail>
        </#if>
      </div>
      <div class="col-sm-6 col-md-4">
        <div class="row form-horizontal">
          <div class="col-sm-4 control-label">Actions</div>
          <div class="col-sm-8">
            <p><a href="<@routes.mitcircs.adminhome submission.department />" class="btn btn-default btn-block"><i class="fal fa-long-arrow-left"></i> Return to list of submissions</a></p>
            <p><a href="<@routes.mitcircs.sensitiveEvidence submission />" class="btn btn-default btn-block">Confirm sensitive evidence</a></p>
            <#if submission.state.entryName == "Submitted">
              <p><a href="<@routes.mitcircs.readyForPanel submission />" class="btn btn-default btn-block" data-toggle="modal" data-target="#readyModal">Ready for panel</a></p>
            <#elseif submission.state.entryName == "Ready For Panel">
              <p><a href="<@routes.mitcircs.readyForPanel submission />" class="btn btn-default btn-block" data-toggle="modal" data-target="#readyModal">Not ready for panel</a></p>
            </#if>
            <div class="modal fade" id="readyModal" tabindex="-1" role="dialog"><@modal.wrapper></@modal.wrapper></div>
          </div>
        </div>
      </div>
    </div>
    <@components.section "Details">
      <#noescape>${submission.formattedReason}</#noescape>
    </@components.section>
    <@components.section "Affected assessments">
      <#if submission.affectedAssessments?has_content>
        <table class="table table-default">
          <thead>
          <tr>
            <th class="col-sm-2">Type</th>
            <th class="col-sm-3">Module</th>
            <th class="col-sm-5">Name</th>
            <th class="col-sm-2">Deadline / exam date</th>
          </tr>
          </thead>
          <tbody>
          <#list submission.affectedAssessments as assessment>
            <tr>
              <td><#if assessment.assessmentType.code == "A">Assignment<#else>Exam</#if></td>
              <td>
                <span class="mod-code">
                  ${assessment.module.code?upper_case}</span> <span class="mod-name">${assessment.module.name} (${assessment.academicYear.toString})
                </span>
              </td>
              <td>${assessment.name}</td>
              <td><#if assessment.deadline??><@fmt.date date=assessment.deadline includeTime=false /><#else><span class="very-subtle">Unknown</span></#if></td>
            </tr>
          </#list>
          </tbody>
        </table>
      <#else>
        This issue doesn't affect any assessments
      </#if>
    </@components.section>
    <#if submission.attachments?has_content>
      <@components.section "Evidence">
        <@components.attachments submission />
      </@components.section>
    </#if>
    <#if submission.evidencePending>
      <@components.section "Pending evidence">
        <p>Due date: <@fmt.date date=submission.pendingEvidenceDue includeTime = false /></p>
        <#noescape>${submission.formattedPendingEvidence}</#noescape>
      </@components.section>
    </#if>
    <#if submission.sensitiveEvidenceComments?has_content>
      <@components.section "Sensitive evidence">
        <p>Seen by: ${submission.sensitiveEvidenceSeenBy.fullName} on <@fmt.date date=submission.pendingEvidenceDue includeTime = false /></p>
        <#noescape>${submission.formattedSensitiveEvidenceComments}</#noescape>
      </@components.section>
    </#if>
    <#assign notesUrl><@routes.mitcircs.notes submission /></#assign>
    <@components.asyncSection "notes" "Notes" notesUrl />
    <#assign messageUrl><@routes.mitcircs.messages submission /></#assign>
    <@components.asyncSection "messages" "Messages" messageUrl />
  </section>
</#escape>