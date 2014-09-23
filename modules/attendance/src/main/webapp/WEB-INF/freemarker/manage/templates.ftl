<#if command.schemes?size == 1>

	<h1>Use a template for scheme: ${command.schemes?first.displayName}</h1>

	<p>
		Use an AQA-approved template to add points. Which template do you want to use?
	</p>

<#else>

	<h1>Use template</h1>

	<p>
		Use an AQA-approved template to add points to
		<a href="#" class="use-popover"
		   data-content="
			<ul>
				<#list schemes as scheme>
					<li>${scheme.displayName}</li>
				</#list>
			</ul>"
		   data-html="true"
		   data-placement="right"
		><@fmt.p schemes?size "scheme" /></a>.

		Which template do you want to use?
	</p>

</#if>

<#if errors?has_content>
<div class="alert alert-error">
	<#list errors.allErrors as error>
		<p><@spring.message code=error.code arguments=error.arguments/></p>
	</#list>
</div>
</#if>

<@f.form action="" method="POST" commandName="command" class="form-horizontal">

	<#list schemes as scheme>
		    <@f.hidden path="schemes"/>
	</#list>

	<input type="hidden" name="returnTo" value="${returnTo}" />

	<@form.labelled_row "templateScheme" "Template:">
		<@f.select path="templateScheme" id="templateSchemeSelect">
			<option value="" style="display: none;">Please select one&hellip;</option>
			<#list templates as template>
				<@f.option value="${template.id}" label="${template.templateName}"/>
			</#list>
		</@f.select>
		<@fmt.help_popover id="templateScheme" content="The list of templates available to select from depends on whether this scheme is using term weeks or calendar dates" />
	</@form.labelled_row>

	<div id="templatePoints"></div>

	<div class="submit-buttons fix-footer">
		<button class="btn btn-primary" type="submit" value="submit">Apply</button>
		<a class="btn" href="<@routes.manageAddPoints department academicYear />">Cancel</a>
	</div>

</@f.form>

<script>
	//TODO make this better.
	(function ($) {

		loadTemplatePoints($("#templateSchemeSelect").val());

		$('#templateSchemeSelect').change(function(){
			var templateSchemeId = $(this).val();

			$('.fix-footer').removeAttr('style').attr('class', 'submit-buttons fix-footer');
			$('.footer-shadow').remove();

			loadTemplatePoints(templateSchemeId);
		})

		function loadTemplatePoints(templateSchemeId) {
			if(templateSchemeId != '') {
				$.get(window.location.pathname + '/' + templateSchemeId,
						function(data){
							$("#templatePoints").html(data);
							$('body').fixHeaderFooter();
						})
			} else {
				$("#templatePoints").html("");
			}
		}

	})(jQuery);
</script>