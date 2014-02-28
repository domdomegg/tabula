<#assign spring=JspTaglibs["/WEB-INF/tld/spring.tld"]>
<#assign f=JspTaglibs["/WEB-INF/tld/spring-form.tld"]>
<#escape x as x?html>

	<div class="modal-header">
		<button type="button" class="close" data-dismiss="modal">×</button>
		<h3>Modify extension for ${userFullName}</h3>
	</div>
	<@f.form method="post" action="${url('/coursework/admin/module/${module.code}/assignments/${assignment.id}/extensions/edit/${universityId}')}" commandName="modifyExtensionCommand" cssClass="double-submit-protection">
		<#include "_extension_fields.ftl" />
		<div class="modal-footer submit-buttons">
			<input type="submit" class="btn btn-primary" value="Modify">
			<a href="#" class="close-model btn" data-dismiss="modal">Cancel</a>
		</div>
	</@f.form>
</#escape>