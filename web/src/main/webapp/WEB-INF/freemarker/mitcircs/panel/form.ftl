<#import "/WEB-INF/freemarker/_profile_link.ftl" as pl />

<#escape x as x?html>

  <#macro submissionTable submissions panel=false>
    <#if submissions?has_content>
      <table class="mitcircs-panel-form__submissions table table-condensed">
        <thead>
        <tr>
          <th>Reference</th>
          <th>Student</th>
          <th>Affected dates</th>
          <th>Last updated</th>
          <#if panel><th>Current panel</th></#if>
          <th></th>
        </tr>
        </thead>
        <tbody>
        <#list submissions as submission>
          <tr>
            <td>
              <a href="<@routes.mitcircs.reviewSubmission submission />">MIT-${submission.key}</a>
              <@f.hidden path="submissions" value="${submission.key}" />
            </td>
            <td>
              <@pl.profile_link submission.student.universityId />
              ${submission.student.universityId}
              ${submission.student.firstName}
              ${submission.student.lastName}
            </td>
            <td>
              <@fmt.date date=submission.startDate includeTime=false relative=false />
              &mdash;
              <#if submission.endDate??>
                <@fmt.date date=submission.endDate includeTime=false relative=false />
              <#else>
                <span class="very-subtle">(ongoing)</span>
              </#if>
            </td>
            <td>
              <@fmt.date date=submission.lastModified />
              <#if submission.unreadByOfficer>
                <span class="tabula-tooltip" data-title="There are unread change(s)"><i class="far fa-envelope text-info"></i></span>
              </#if>
            </td>
            <#if panel><td>${submission.panel.name}</td></#if>
            <td>
              <button class="remove btn btn-sm">Remove</button>
            </td>
          </tr>
        </#list>
        </tbody>
      </table>
    <#else>
      <div class="form-control-static">No submissions</div>
    </#if>
  </#macro>


  <div id="profile-modal" class="modal fade profile-subset"></div>

  <#function route_function dept>
    <#local result><@routes.mitcircs.adminhome dept /></#local>
    <#return result />
  </#function>
  <@fmt.id7_deptheader "Create a mitigating circumstances panel" route_function "for" />

  <@f.form method="POST" modelAttribute="createCommand" class="mitcircs-panel-form dirty-check double-submit-protection" enctype="multipart/form-data">

    <@bs3form.labelled_form_group path="name" labelText="Name">
      <@f.input path="name" cssClass="form-control" />
    </@bs3form.labelled_form_group>

    <div class="row">
      <div class="col-xs-4">
        <@bs3form.labelled_form_group path="date" labelText="Date of panel">
          <div class="input-group">
            <@f.input path="date" autocomplete="off" cssClass="form-control date-picker" />
            <span class="input-group-addon"><i class="fa fa-calendar"></i></span>
          </div>
        </@bs3form.labelled_form_group>
      </div>
      <div class="col-xs-4">
        <@bs3form.labelled_form_group path="start" labelText="Start time of panel">
          <div class="input-group">
            <@f.input path="start" autocomplete="off" cssClass="form-control time-picker" />
            <span class="input-group-addon"><i class="fa fa-clock-o"></i></span>
          </div>
        </@bs3form.labelled_form_group>
      </div>
      <div class="col-xs-4">
        <@bs3form.labelled_form_group path="end" labelText="End time of panel">
          <div class="input-group">
            <@f.input path="end" autocomplete="off" cssClass="form-control time-picker" />
            <span class="input-group-addon"><i class="fa fa-clock-o"></i></span>
          </div>
        </@bs3form.labelled_form_group>
      </div>
    </div>

    <@bs3form.labelled_form_group path="location" labelText="Location">
      <@f.hidden path="locationId" />
      <@f.input path="location" cssClass="form-control location-picker" />
    </@bs3form.labelled_form_group>

    <@bs3form.labelled_form_group path="members" labelText="Panel members">
      <@bs3form.flexipicker path="members" placeholder="User name" list=true object=true multiple=true auto_multiple=false />
    </@bs3form.labelled_form_group>

    <#if hasPanel?has_content>
      <@bs3form.labelled_form_group path="" labelText="Submissions being moved to this panel">
        <p>The following submissions have already been added to another panel. They will be moved to this panel.</p>
        <@submissionTable submissions=hasPanel panel=true />
      </@bs3form.labelled_form_group>
    </#if>

    <@bs3form.labelled_form_group path="submissions" labelText="Submissions being added to this panel">
      <@submissionTable noPanel />
    </@bs3form.labelled_form_group>

    <div class="fix-footer">
      <button type="submit" class="btn btn-primary" name="submit">Create panel</button>
      <a class="btn btn-default dirty-check-ignore" href="<@routes.mitcircs.adminhome department academicYear/>">Cancel</a>
    </div>

  </@f.form>
</#escape>