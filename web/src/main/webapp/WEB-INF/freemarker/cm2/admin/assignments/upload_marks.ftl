<#import "*/cm2_macros.ftl" as cm2 />
<#import "*/marking_macros.ftl" as marking_macros />
<#escape x as x?html>

  <@cm2.assignmentHeader "Submit marks and feedback" assignment />
  <div>
    <!-- Nav tabs -->
    <ul class="nav nav-tabs" role="tablist">
      <li role="presentation" class="active"><a href="#upload" aria-controls="upload" role="tab" data-toggle="tab">Upload</a></li>
      <li role="presentation"><a href="#webform" aria-controls="webform" role="tab" data-toggle="tab">Web Form</a></li>
    </ul>
    <!-- Tab panes -->
    <div class="tab-content">
      <div role="tabpanel" class="tab-pane active" id="upload">
        <p>You can upload marks and feedback in a spreadsheet, which must be an XLSX file (i.e. created in Microsoft Office 2007 or later). The spreadsheet
          should have the following column headings: <strong>University ID</strong>, <strong>Mark</strong>, <strong>Grade</strong> and <strong>Feedback</strong>.
          You can use this <a href="${templateUrl}">generated spreadsheet</a> as a template.</p>
        <p>Enter the results in the following columns (all other columns are locked):</p>
        <ul>
          <li>Column B - Mark</li>
          <li>Column C - Grade</li>
          <li>Column D - Feedback</li>
        </ul>
        <p>Note that you can upload any of the following: marks only; marks and grades only; marks, grades and feedback.</p>
        <@f.form method="post" enctype="multipart/form-data" action="${formUrl}" modelAttribute="command">
          <h3>Select file</h3>
          <p id="multifile-column-description">
            <#include "/WEB-INF/freemarker/multiple_upload_help.ftl" />
          </p>
          <@bs3form.labelled_form_group path="file" labelText="Files">
            <input type="file" name="file.upload" multiple />
          </@bs3form.labelled_form_group>

          <input type="hidden" name="isfile" value="true" />

          <div class="buttons form-group">
            <button type="submit" class="btn btn-primary">Upload</button>
            <a class="btn btn-default" href="${cancelUrl}">Cancel</a>
          </div>
        </@f.form>
      </div>
      <div role="tabpanel" class="tab-pane" id="webform">
        <p>Click the add button below to enter marks and feedback for a student.</p>
        <@f.form cssClass="marks-web-form" method="post" action="${formUrl}" modelAttribute="command">
          <div class="row hidden-xs hidden-sm">
            <div class="col-md-2">
              <label>University ID</label>
            </div>
            <#assign feedbackColumnWidth=6/>
            <#if assignment.showSeatNumbers>
              <#assign feedbackColumnWidth=5/>
              <div class="col-md-1">
                <label>Seat</label>
              </div>
            </#if>
            <div class="col-md-2">
              <label>Mark</label>
            </div>
            <div class="col-md-2">
              <label>Grade</label>
            </div>
            <div class="col-md-${feedbackColumnWidth}">
              <label>Feedback</label>
            </div>
          </div>
          <#list command.existingMarks as markItem>
            <div class="row mark-row">
              <div class="col-md-2">
                <div class="form-group">
                  <input class="form-control" value="${markItem.id}" name="marks[${markItem_index}].id" type="text" readonly="readonly" />
                </div>
              </div>
              <#if assignment.showSeatNumbers>
                <div class="col-md-1">
                  <div class="form-group">
                    <input class="form-control" value="${assignment.getSeatNumber(markItem.user(assignment))!""}" type="text" readonly="readonly" />
                  </div>
                </div>
              </#if>
              <div class="col-md-2">
                <div class="form-group">
                  <div class="input-group">
                    <input class="form-control" name="marks[${markItem_index}].actualMark" value="<#if markItem.actualMark??>${markItem.actualMark}</#if>"
                           type="number" />
                    <div class="input-group-addon">%</div>
                  </div>
                </div>
              </div>
              <div class="col-md-2">
                <div class="form-group">
                  <#if isGradeValidation>
                    <#assign generateUrl><@routes.cm2.generateGradesForMarks command.assignment /></#assign>
                    <div class="input-group">
                      <input id="auto-grade-${markItem.id}" class="form-control auto-grade" name="marks[${markItem_index}].actualGrade"
                             value="<#if markItem.actualGrade??>${markItem.actualGrade}</#if>" type="text" />
                      <select name="marks[${markItem_index}].actualGrade" class="form-control" disabled style="display:none;"></select>
                    </div>
                    <@marking_macros.autoGradeOnlineScripts "marks[${markItem_index}].actualMark" markItem.id generateUrl />
                  <#else>
                    <div class="form-group">
                      <div class="input-group">
                        <input name="marks[${markItem_index}].actualGrade" class="form-control"
                               value="<#if markItem.actualGrade??>${markItem.actualGrade}</#if>" type="text" placeholder="Grade" />
                      </div>
                    </div class="form-group">
                  </#if>
                </div>
              </div>
              <div class="col-md-${feedbackColumnWidth}">
                <div class="form-group">
                  <textarea class="small-textarea form-control" name="marks[${markItem_index}].feedbackComment"
                            placeholder="Feedback"><#if markItem.feedbackComment??>${markItem.feedbackComment}</#if></textarea>
                </div>
              </div>
            </div>
          </#list>
          <input type="hidden" name="isfile" value="false" />
          <div class="form-group">
            <button class="add-mark-row btn btn-default">+ Add</button>
          </div>
          <div class="buttons form-group">
            <button type="submit" class="btn btn-primary">Save</button>
            <a class="btn btn-default" href="${cancelUrl}">Cancel</a>
          </div>
        </@f.form>
        <div class="hidden mark-row-template">
          <div class="row mark-row">
            <div class="col-md-2">
              <div class="form-group">
                <input name="id" class="form-control" type="text" placeholder="ID">
              </div>
            </div>
            <div class="col-md-2">
              <div class="form-group">
                <div class="input-group">
                  <input name="actualMark" class="form-control" type="number" placeholder="Mark">
                  <div class="input-group-addon">%</div>
                </div>
              </div>
            </div>
            <div class="col-md-2">
              <div class="form-group">
                <input name="actualGrade" class="form-control" type="text" placeholder="Grade">
              </div>
            </div>
            <div class="col-md-6">
              <div class="form-group">
                <textarea class="small-textarea form-control" name="feedbackComment" placeholder="Feedback"></textarea>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>

  <script type="text/javascript">
    (function ($) {
      var $rowTemplate = $('.mark-row-template .mark-row');

      $('.add-mark-row').on('click', function (e) {
        e.preventDefault();
        var numExistingRows = $('.marks-web-form .mark-row').length;
        var $newRow = $rowTemplate.clone();
        $newRow.find('input,textarea').each(function (i, field) {
          var newName = 'marks[' + numExistingRows + '].' + $(field).attr('name');
          $(field).attr('name', newName);
        });
        $(this).before($newRow);
      });
    })(jQuery);
  </script>
</#escape>