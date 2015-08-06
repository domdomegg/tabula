<#escape x as x?html>

<h1>Delete student relationship type: ${relationshipType.description}</h1>

<@f.form method="post" action="${url('/sysadmin/relationships/${relationshipType.urlPart}/delete')}" commandName="deleteStudentRelationshipTypeCommand">
	<!-- global errors -->
	<@f.errors cssClass="error" />

	<#if relationshipType.empty>
		<p>
			You can delete this student relationship type if it's been created in error.
		</p>

		<@f.errors path="confirm" cssClass="error" />
		<@form.label checkbox=true>
			<@f.checkbox path="confirm" id="confirmCheck" />
			<strong> I definitely will not need this type again and wish to delete it entirely.</strong>
		</@form.label>

		<div class="submit-buttons">
			<input type="submit" value="Delete" class="btn btn-danger">
			<a href="<@url page="/sysadmin/relationships" />" class="btn">Cancel</a>
		</div>
	<#else>
		<p>It's not possible to delete this relationship type because there are relationships with this type.</p>

		<div class="submit-buttons">
			<a href="<@url page="/sysadmin/relationships" />" class="btn">Cancel</a>
		</div>
	</#if>
</@f.form>

<script type="text/javascript">
	jQuery(function($){
		$('#confirmCheck').change(function(){
			$('.submit-buttons input[type=submit]').attr('disabled', !this.checked).toggleClass('disabled', !this.checked);
		});
		$('.submit-buttons input[type=submit]').attr('disabled',true).addClass('disabled');
	})
</script>

</#escape>