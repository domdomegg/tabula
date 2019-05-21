<#import "/WEB-INF/freemarker/modal_macros.ftl" as modal />

<#escape x as x?html>
  <#assign formAction><@routes.mitcircs.readyForPanel submission /></#assign>
  <@f.form id="readyForPanelForm" action="${formAction}" method="POST" modelAttribute="command" class="dirty-check double-submit-protection" enctype="multipart/form-data">
    <#if submission.state.entryName == "Ready For Panel">
      <@modal.header>
        <h3 class="modal-title">Confirm that MIT-${submission.key} is no longer ready for panel</h3>
      </@modal.header>
      <@modal.body>
        Please confirm that this submission is incomplete and isn't ready to be considered at a mitigating circumstances panel.
        Further changes to the submission will then be possible.
        <@f.hidden path="ready" value="false"/>
      </@modal.body>
    <#else>
      <@modal.header>
        <h3 class="modal-title">Confirm that MIT-${submission.key} is ready for panel</h3>
      </@modal.header>
      <@modal.body>
        <p>
          Please check that this submission is complete and has enough information and evidence to be considered at a mitigating circumstances panel.
          Further changes to the submission won't be possible.
        </p>
        <ul class="fa-ul">
          <li><span class="fa-li" ><i class="fas fa-check"></i></span> The student has provided sufficient detail about the nature of their mitigating circumstances</li>
          <li><span class="fa-li" ><i class="fas fa-check"></i></span> The student has outlined how the mitigating circumstances have affected their assessments</li>
          <li><span class="fa-li" ><i class="fas fa-check"></i></span> Any affected assessments have been listed</li>
        </ul>
        <p>The following evidence has been submitted ...</p>
        <ul class="fa-ul">
          <#list submission.issueTypes as issueType>
            <li><span class="fa-li" ><i class="fas fa-check"></i></span> <strong>${issueType.description}</strong> - ${issueType.evidenceGuidance}</li>
          </#list>
        </ul>

        <div class="checkbox">
          <label>
            <@f.checkbox path="confirm" /> I confirm that this submission has sufficient information and evidence to be considered at a mitigating circumstances panel.
          </label>
          <@bs3form.errors path="confirm" />
        </div>
        <@f.hidden path="ready" value="true"/>
      </@modal.body>
    </#if>
    <@modal.footer>
      <button type="submit" class="btn btn-primary">Confirm</button>
      <a class="btn btn-default dirty-check-ignore" href="<@routes.mitcircs.reviewSubmission submission />">Cancel</a>
    </@modal.footer>
  </@f.form>
</#escape>