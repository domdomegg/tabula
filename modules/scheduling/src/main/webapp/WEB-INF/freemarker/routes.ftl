<#ftl strip_text=true />
<#--
Just a handy place to create macros for generating URLs to various places, to save time
if we end up changing any of them. 

TODO grab values from the Routes object in code, as that's pretty equivalent and 
	we're repeating ourselves here. OR expose Routes directly.

-->
<#macro home><@url context="/" page="/" /></#macro>