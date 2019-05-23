<#import "*/mitcircs_components.ftl" as components />
<#import "/WEB-INF/freemarker/_profile_link.ftl" as pl />

<#escape x as x?html>
  <#if submissions?has_content>
    <table class="table table-condensed">
      <thead>
        <tr>
          <th><input type="checkbox" class="check-all" title="Select all/none"></th>
          <th>Reference</th>
          <th>Student</th>
          <th>Affected dates</th>
          <th>Progress</th>
          <th>Last updated</th>
        </tr>
      </thead>
      <tbody>
      <#list submissions as info>
        <#assign submission = info.submission />
        <tr>
          <td><input type="checkbox" name="submissions" value="${submission.key}"></td>
          <td><a href="<@routes.mitcircs.reviewSubmission submission />">MIT-${submission.key}</a></td>
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
          <td><@components.stage_progress_bar info.stages?values /></td>
          <td>
            <@fmt.date date=submission.lastModified />
            <#if submission.unreadByOfficer>
              <span class="tabula-tooltip" data-title="There are unread change(s)"><i class="far fa-envelope text-info"></i></span>
            </#if>
          </td>
        </tr>
      </#list>
      <tbody>
    </table>
  <#else>
    <p>There are no mitigating circumstances submissions for ${department.name}.</p>
  </#if>

  <script type="text/javascript">
    (function ($) {
      $('a.ajax-modal').ajaxModalLink();

      // We probably just grew a scrollbar, so let's trigger a window resize
      $(window).trigger('resize.ScrollToFixed');
    })(jQuery);
  </script>
</#escape>