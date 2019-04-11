<#escape x as x?html>
  <#import "/WEB-INF/freemarker/_profile_link.ftl" as pl />
  <div id="profile-modal" class="modal fade profile-subset"></div>

  <div class="deptheader">
    <h1 class="with-settings">Mitigating Circumstances Submissions</h1>
    <h4 class="with-related">in ${department.name}</h4>
  </div>

  <#if submissions?has_content>
    <table class="table table-condensed">
      <thead>
      <tr>
        <th>Reference</th>
        <th>University Id</th>
        <th>Issue type</th>
        <th>Start date</th>
        <th>End date</th>
        <th>Last modified</th>
      </tr>
      </thead>
      <tbody>
      <#list submissions as submission>
        <tr>
          <td><a href="">MIT-${submission.key}</a></td>
          <td>${submission.student.universityId} <@pl.profile_link submission.student.universityId /></td>
          <td>${submission.issueType.description}</td>
          <td><@fmt.date date=submission.startDate includeTime=false /></td>
          <td>
            <#if submission.endDate??>
              <@fmt.date date=submission.endDate includeTime=false />
            <#else>
              <span class="very-subtle">(not set)</span>
            </#if>
          </td>
          <td><@fmt.date date=submission.lastModified /></td>
        </tr>
      </#list>
      <tbody>
    </table>
  <#else>
    <p>There are no mitigating circumstances submissions for ${department.name}.</p>
  </#if>

</#escape>