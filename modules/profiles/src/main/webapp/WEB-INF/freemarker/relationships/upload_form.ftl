<#assign introText>
	<p>The spreadsheet must be in <samp>.xlsx</samp> format (created in Microsoft Excel 2007 or newer, or another compatible spreadsheet application). You can download a template spreadsheet which is correctly formatted, ready for completion.<p>
	<p>The spreadsheet must contain two columns, headed:<p>
	<ul>
	<li><b>student_id</b> - contains the student's University ID number (also known as the library card number)</li>
	<li><b>agent_id</b> - contains the ${relationshipType.agentRole}'s University ID number</li>
	</ul>
	<p>You may need to <a href='http://office.microsoft.com/en-gb/excel-help/format-numbers-as-text-HA102749016.aspx?CTT=1'>format these columns</a> as text to avoid Microsoft Excel removing 0s from the start of ID numbers.</p>
	<p>The spreadsheet may also contain other columns and information for your own reference (these will be ignored by Tabula).</p>
</#assign>

<p>You can set ${relationshipType.agentRole}s for many students at once by uploading a spreadsheet.
	<a href="#"
		id="agent-intro"
		class="use-introductory"
		data-hash="${introHash("agent-intro")}"
		data-title="${relationshipType.agentRole} spreadsheet"
		data-trigger="click"
		data-placement="bottom"
		data-html="true"
		data-content="${introText}"><i class="icon-question-sign"></i></a></p>

<ol>
	<li>
		<button type="submit" class="btn" name="template" value="true"><i class="icon-download"></i> Download a template spreadsheet</button>
		<br />
		This will be prefilled with the names and University ID numbers of students and their ${relationshipType.agentRole} (if they have one) in ${department.name}. In Excel you may need to <a href="http://office.microsoft.com/en-gb/excel-help/what-is-protected-view-RZ101665538.aspx?CTT=1&section=7">exit protected view</a> to edit the spreadsheet.
		<br /><br />
		<div class="alert alert-info">
			<p>This will include any changes made in the drag and drop tab. You can also <a href="<@routes.relationship_template department relationshipType />">download a template without these changes</a>.</p>
			<p>Any students with multiple existing tutors will <strong>not</strong> be included in the spreadsheet, as only 1 tutor can be assigned per student using this method.</p>
		</div>
	</li>
	<li><strong>Allocate students</strong> to ${relationshipType.agentRole}s using the dropdown menu in the <strong>${relationshipType.agentRole?cap_first} name</strong> column or by typing a ${relationshipType.agentRole}'s University ID into the <strong>agent_id</strong> column. The <strong>agent_id</strong> field will be updated with the University ID for that ${relationshipType.agentRole} if you use the dropdown.
		Any students with an empty <strong>agent_id</strong> field will have their ${relationshipType.agentRole} removed, if they have one.</li>
	<li><strong>Save</strong> your updated spreadsheet.</li>
	<li><@form.labelled_row "file.upload" "Choose your updated spreadsheet" "step-action" ><input type="file" name="file.upload"  /> </@form.labelled_row></li>
</ol>


<div class="submit-buttons">
	<button class="btn btn-primary btn-large" name="doPreviewSpreadsheetUpload"><i class="icon-upload icon-white"></i> Upload</button>
</div>