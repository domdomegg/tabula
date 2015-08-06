<@f.form method="post" action="${url('/coursework/admin/module/${module.code}/assignments/${assignment.id}/archive')}" commandName="command" cssClass="form-vertical">
<#assign assignment=command.assignment />

<#if assignment.archived>

	<h3>Unarchive this assignment</h3>

	<p>You should only unarchive an assignment you've archived by mistake.
	If you want to do anything new, you should create a fresh assignment.</p>

	<input type="hidden" name="unarchive" value="true" />
	<input class="btn" type="submit" value="Unarchive"> <a class="btn cancel-link" href="#">Cancel</a>

<#else>

	<h3>Archive this assignment</h3>

	<p>Archiving an assignment will hide it from most lists of things. Students
	will still be able to access their feedback and/or marks from an archived
	assignment.</p>

	<input class="btn" type="submit" value="Archive"> <a class="btn cancel-link" href="#">Cancel</a>

</#if>

</@f.form>