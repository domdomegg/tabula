<#assign f=JspTaglibs["/WEB-INF/tld/spring-form.tld"]>
<#assign spring=JspTaglibs["/WEB-INF/tld/spring.tld"]>
<#compress>
<#escape x as x?html>

<#-- Return the textual value of the given command property -->
<#function getvalue path>
	<@spring.bind path=path>
		<#return status.value />
	</@spring.bind>
</#function>

<#-- Return whether this given command property is set -->
<#function hasvalue path>
	<@spring.bind path=path>
		<#return status.actualValue?? && status.value != "" /><#-- use actualValue as value will usually be empty string for an empty value -->
	</@spring.bind>
</#function>

<#macro row path="" error=false>
<#if path="">
<div class="control-group"><#nested/></div>
<#elseif error>
<div class="control-group error"><#nested/></div>
<#else>
<@spring.bind path=path>
<div class="control-group<#if status.error> error</#if>"><#nested/></div>
</@spring.bind>
</#if>
</#macro>

<#macro field>
<div class="controls"><#nested/></div>
</#macro>

<#macro label path="" for="" checkbox=false>
<#local clazz="control-label">
<#if checkbox>
	<#local clazz="checkbox" />
</#if>
<#if path!="">
  <@f.label path=path cssClass=clazz><#nested/></@f.label>
<#elseif for!="">
  <label for="${for}" class="${clazz}"><#nested /></label>
<#else>
  <label class="${clazz}"><#nested /></label>
</#if>
</#macro>
<#assign _label=label /><#-- to resolve naming conflicts -->

<#macro selector_check_all>
<div class="check-all checkbox">
	<label><span class="very-subtle">select all</span>
		<input type="checkbox" class="collection-check-all">
	</label>
</div>
</#macro>

<#macro selector_check_row name value>
<div class="checkbox">
	<input type="checkbox" class="collection-checkbox" name="${name}" value="${value}">
</div>
</#macro>

<#macro errors path><@f.errors path=path cssClass="error help-inline" /></#macro>

<#macro labelled_row path label>
<@row path=path>
	<@_label path=path>
	${label}
	</@_label>
	<@field>
	<#nested />
	<@errors path=path />
	</@field>
</@row>
</#macro>

<#--

Render a text field with user picker.

To bind with Spring:
<@userpicker path="yourBindPath" />

To not bind:
<@userpicker name="paramName" />

-->
<#macro userpicker path="" name="" list=false multiple=false>
<#if name="">
	<@spring.bind path=path>
	<#-- This handles whether we're binding to a list or not but I think
		it might still be more verbose than it needs to be. -->
	<#assign ids=[] />
	<#if list>
		<#assign ids=status.value />
	<#elseif status.value??>
		<#assign ids=[status.value] />
	</#if>
	<@render_userpicker expression=status.expression value=ids />
	</@spring.bind>
<#else>
	<@render_userpicker expression=name value=[] />
</#if>
</#macro>

<#macro render_userpicker expression value>
	<#if value?? && value?size gt 0>
	<#list value as id>
		<div class="input-prepend input-append"><span class="add-on"><i class="icon-user"></i></span><#--
		--><input type="text" class="user-code-picker span2" name="${expression}" value="${id}">
		</div>
	</#list>
	<#else>
		<div class="input-prepend input-append"><span class="add-on"><i class="icon-user"></i></span><#--
		--><input type="text" class="user-code-picker span2" name="${expression}">
		</div>
	</#if>
</#macro>

<#--
	Create a file(s) upload widget, binding to an UploadedFile object.
	For multiple file support, it will use the HTML5 attribute if available,
	otherwise it will degrade to other mechanisms.
	
	basename - bind path to the UploadedFile.
	multiple - whether it should be possible to upload more than one file.
-->
<#macro filewidget types basename multiple=true max=10 >
	<#-- <#local command=.vars[Request[commandVarName]] /> -->
	<#local elementId="file-upload-${basename?replace('[','')?replace(']','')?replace('.','-')}"/>
	<@row path=basename>
	<@label path="${basename}.upload">
	File
	</@label>
	<@field>
	<@errors path="${basename}" />
	<@errors path="${basename}.upload" />
	<@errors path="${basename}.attached" />
	<@spring.bind path="${basename}">
	<#local f=status.actualValue />
	<div id="${elementId}">
	<#if f.exists>
		<#list f.attached as attached>
			<#assign uploadedId=attached.id />
			<div class="hidden-attachment" id="attachment-${uploadedId}">
			<input type="hidden" name="${basename}.attached" value="${uploadedId}">
			${attached.name} <a id="remove-attachment-${uploadedId}" href="#">Remove attachment</a>
			</div>
			<div id="upload-${uploadedId}" style="display:none">
			
			</div>
		</#list>
	</#if>
	
	<#if multiple>
		<input type="file" name="${basename}.upload" multiple>
		<noscript>
			<#list (2..max) as i>
				<br><input type="file" name="${basename}.upload">
			</#list>
		</noscript>
	<#else>
		<input type="file" name="${basename}.upload" >
	</#if>
	</div>
	
	<div class="subtle help-block">
		<#if max=1>One attachment allowed.<#else>Up to <@fmt.p max "attachment" /> allowed.</#if>
		<#if types?size &gt; 0>
			File types allowed: <#list types as type>${type}<#if type_has_next>, </#if></#list>
		</#if>
	</div>
	
	<#if multiple>
		<script><!--
		
		jQuery(function($){
			var $container = $('#${elementId}'), 
			    $file = $container.find('input[type=file]'),
			    $addButton; 
			if (Supports.multipleFiles) {
				// nothing, already works
			} else {
				// Add button which generates more file inputs
			    $addButton = $('<a>').addClass('btn btn-mini').append($('<i class="icon-plus"></i>').attr('title','Add another attachment'));
			    $addButton.click(function(){
			    	$addButton
			    		.before($('<br/>'))
			    		.before($('<input type="file">').attr('name',"${basename}.upload"));
			    	if ($container.find('input[type=file]').length >= ${max}) {
			    	    $addButton.hide(); // you've got enough file input thingies now.
			    	}
			    });
				$file.after($addButton);
			}
			
			$container.find('.hidden-attachment a').click(function(ev){
			    ev.preventDefault();
			    $(this).parent('.hidden-attachment').remove(); 
			    if ($addButton && $container.find('input[type=file],input[type=hidden]').length < ${max}) {
			       $addButton.show();
			    }
			    return false;
			});
		});
		
		//--></script>
	</#if>
	
	</@spring.bind>
	</@field>
	</@row>
</#macro>

</#escape>
</#compress>