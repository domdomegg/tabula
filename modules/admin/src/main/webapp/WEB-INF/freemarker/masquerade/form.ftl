<h1>Masquerade as a different user</h1>

<#if !user.sysadmin && user.masquerader>
<p>You are not a system admin but are in a group of people able to masquerade freely as another user.</p>
</#if>

<p>Masquerading allows you to see the site exactly as another user would see it. If you do any audited
actions, both your masquerade identity and your true identity will be stored.</p>

<#if actionMessage?default('') = "removed">
<p>You are no longer masquerading.</p>
</#if>

<div>
<@f.form method="post" action="${url('/masquerade')}" command="" cssClass="form-vertical">
	<fieldset>
	<@form.row>
		<@form.field>
		<@form.flexipicker name="usercode" placeholder="Type a name or usercode" cssClass="input-append" />
		<button class="btn" style="margin-top: -10px"><i class="icon-eye-open"></i> Mask</button>
		</@form.field>
	</@form.row>
	</fieldset>
</@f.form>
</div>

<#if user.masquerading>

<p>Masquerading as ${user.apparentId} (${user.apparentUser.fullName}).</p>

<@f.form method="post" action="${url('/masquerade')}">
<input type="hidden" name="action" value="remove" />
<button class="btn"><i class="icon-eye-close"></i> Unmask</button>
</@f.form>

</#if>