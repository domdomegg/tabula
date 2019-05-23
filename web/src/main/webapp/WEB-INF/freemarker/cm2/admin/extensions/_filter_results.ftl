<#import "/WEB-INF/freemarker/_profile_link.ftl" as pl />
<#import "*/cm2_macros.ftl" as cm2 />
<#assign currentPage = command.page />
<#assign totalPages = (results.total / command.extensionsPerPage)?ceiling />

<div id="profile-modal" class="modal fade profile-subset"></div>

<#function sortClass field>
  <#list command.sortOrder as order>
    <#if order.propertyName == field>
      <#if order.ascending>
        <#return "headerSortDown" />
      <#else>
        <#return "headerSortUp" />
      </#if>
    </#if>
  </#list>
  <#return "" />
</#function>

<div id="filter-results">
<div class="row extension-metadata">
  <div class="col-md-7">
    <p>Found <@fmt.p results.total "extension" />.</p>
  </div>
  <div class="col-md-5">
    <p class="alert alert-info">
      Students will automatically be notified by email when you approve, modify or revoke an extension.
    </p>
  </div>
  <div class="col-md-12">
    <@cm2.pagination currentPage totalPages />
  </div>
</div>

<table id="student-extension-management" class="students table table-striped sticky-table-headers expanding-table">
  <thead>
    <tr>
      <th class="student-col ${sortClass("member.firstName")}" data-field="member.firstName">First name</th>
      <th class="student-col ${sortClass("member.lastName")}" data-field="member.lastName">Last name</th>
      <th class="student-col ${sortClass("_universityId")}" data-field="_universityId">University ID</th>
      <th class="${sortClass("module.code")}" data-field="module.code">Module</th>
      <th class="${sortClass("assignment.name")}" data-field="assignment.name">Assignment</th>
      <th class="status-col ${sortClass("_state")}" data-field="_state">Status</th>
      <th class="duration-col ${sortClass("lengthDays")}" data-field="lengthDays">Extension length</th>
      <th class="deadline-col ${sortClass("expiryDateOrAssignmentCloseDate")}" data-field="expiryDateOrAssignmentCloseDate">Submission due</th>
    </tr>
  </thead>
  <tbody>
  <#list results.extensions as graph>
    <tr class="itemContainer"

        data-contentid="extension${graph.extension.id}"
        data-detailurl="<@routes.cm2.extensionDetail graph.extension />"
    >
      <#-- TAB-2063 - The extension manager will need to know who is doing the asking, so we should always show names -->
      <td class="student-col toggle-cell toggle-icon">${graph.user.firstName}</td>
      <td class="student-col toggle-cell">${graph.user.lastName}</td>
      <td class="id toggle-cell">
        <#assign identifier = graph.user.warwickId!graph.user.userId />
        ${identifier} <@pl.profile_link identifier />
      </td>
      <td><@fmt.module_name graph.extension.assignment.module false /></td>
      <td><a href="<@routes.cm2.assignmentextensions graph.extension.assignment />">${graph.extension.assignment.name}</a></td>

      <td class="status-col toggle-cell content-cell">
        <dl style="margin: 0; border-bottom: 0;">
          <dt>
            ${graph.extension.state.description}
          </dt>
          <dd style="display: none;" class="table-content-container" data-contentid="extension${graph.extension.id}">
            <div id="content-extension${graph.extension.id}" class="content-container" data-contentid="extension${graph.extension.id}">
              <p>No extension data is currently available.</p>
            </div>
          </dd>
        </dl>
      </td>

      <td class="duration-col toggle-cell<#if graph.hasApprovedExtension> approved<#else> very-subtle</#if>" data-datesort="<#if (graph.duration > 0)>${graph.duration}<#elseif (graph.requestedExtraDuration > 0) >${graph.requestedExtraDuration}<#else>0</#if>">
        <#if (graph.duration > 0)>
          ${graph.duration} days
        <#elseif (graph.requestedExtraDuration > 0) >
          ${graph.requestedExtraDuration} days requested
        <#else>
          N/A
        </#if>
      </td>
      <td data-datesort="${graph.deadline.millis?c!''}"
          class="deadline-col <#if graph.hasApprovedExtension>approved<#else>very-subtle</#if>"><#if graph.deadline?has_content><@fmt.date date=graph.deadline /></#if></td>
    </tr>
  </#list>
  </tbody>
</table>
</div>

<script type="text/javascript">
  (function ($) {
    // add a custom parser for the date column
    $.tablesorter.addParser({
      id: 'customdate',
      is: function (s, table, cell, $cell) {
        return false; /*return false so this parser is not auto detected*/
      },
      format: function (s, table, cell, cellIndex) {
        var $cell = $(cell);
        return $cell.attr('data-datesort') || s;
      },
      parsed: false,
      type: 'numeric'
    });

    $('.expanding-table').expandingTable({
      contentUrlFunction: function ($row) {
        return $row.data('detailurl');
      },
      useIframe: true,
      <#if totalPages gt 1>
        allowTableSort: false,
      <#else>
        tableSorterOptions: {
          sortList: [[1, 0], [0, 0]],
          headers: {
            6: {sorter: 'customdate'},
            7: {sorter: 'customdate'}
          }
        },
      </#if>
      preventContentIdInUrl: true
    });

    <#if totalPages gt 1>
      var doRequest = function ($form, preventPageReset) {
        if (typeof history.pushState !== 'undefined')
          history.pushState(null, null, $form.attr('action') + '?' + $form.serialize());

        if ($form.data('request')) {
          $form.data('request').abort();
          $form.data('request', null);
        }

        if (!preventPageReset) {
          $form.find('input[name="page"]').val('1');
        }

        $('#filter-results').addClass('loading');
        $('.filter-container .placeholder').addClass('loading');
        $form.data('request', $.post($form.attr('action'), $form.serialize(), function (data) {
          $('#filter-results').html(data);

          $form.data('request', null);
          $('#filter-results').removeClass('loading');
          $('.filter-container .placeholder').removeClass('loading');

          // callback for hooking in local changes to results
          $(document).trigger("tabula.filterResultsChanged");
        }));
      };
      window.doRequest = doRequest;

      // CUSTOM TABLE SORTING
      $(".expanding-table").addClass('tablesorter')
        .find('th:not(:first-child)').addClass('header')
        .on('click', function (e) {
          var $th = $(this);

          if ($th.hasClass('headerSortDown')) {
            $('#sortOrder').val('desc(' + $th.data('field') + ')');
            $th.closest('thead').find('th').removeClass('headerSortUp').removeClass('headerSortDown');
            $th.addClass('headerSortUp');
          } else {
            $('#sortOrder').val('asc(' + $th.data('field') + ')');
            $th.closest('thead').find('th').removeClass('headerSortUp').removeClass('headerSortDown');
            $th.addClass('headerSortDown');
          }

          if (typeof (window.doRequest) === 'function') {
            window.doRequest($('#command'), true);
          } else {
            $('#command').submit();
          }
        });
      </#if>
  })(jQuery);
</script>