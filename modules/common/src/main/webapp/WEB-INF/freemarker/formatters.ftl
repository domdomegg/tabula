<#ftl strip_text=true />
<#escape x as x?html>

<#--
deptheader macro creates a one or two line department-based heading for a page,
automating a dropdown for any related departments (parent and/or subdepartments).

eg. <@deptheader "Do a thing" "in" dept routes "deptHome" /> with CS dept, and no subdepartments gives:

<h1>Do a thing</h1>
<h4><span class="muted">in</span> Computer Science</h4>

title: Text to use in the <h1> element. Mandatory, unless preposition is empty, in which case, defaults to department name
preposition: Muted text to relate the title to the department name in the second line, eg. for, with, in. If empty string, header will be one-liner
department: department (from model)
routes: routes freemarker object (from prelude.ftl)
routemacro: a macro string identifier (from within routes) to fetch an appropriate page for related departments
cssClass (optional): a class to apply to the h1 (typically used for 'with-settings')
-->
<#macro deptheader title preposition department routes routemacro cssClass="">
	<#local use_h4 = preposition?has_content />

	<#if use_h4>
		<div class="deptheader">
			<#if cssClass?has_content>
				<h1 class="${cssClass}">${title}</h1>
			<#else>
				<h1>${title}</h1>
			</#if>

			<#if department.parent?? || department.children?has_content>
				<h4 class="with-related"><span class="muted">${preposition}</span> ${department.name}</h4>
			<#else>
				<h4><span class="muted">for</span> ${department.name}</h4>
			</#if>
		<#-- <div> closed below -->
	<#else>
		<div class="deptheader">
			<#local h1Class = (cssClass + " with-related")?trim />
			<#if title?has_content>
				<#local h1Title = title />
			<#else>
				<#local h1Title = department.name />
			</#if>

			<h1 class="${h1Class}">${h1Title}</h1>
		<#-- <div> closed below -->
	</#if>

	<#-- <div> opened above -->
		<#if department.parent?? || department.children?has_content>
			<a class="use-tooltip" title="Related departments" data-toggle="dropdown" data-container="body" data-target=".dropdown">
				<i class="icon-caret-down<#if !use_h4> icon-large</#if>"></i>
			</a>
			<#-- cross-app singleton introductory text -->
			<#if showIntro("related-depts", "anywhere")>
				<#assign introText>
					<p>To access sub-departments and parent departments, click the down arrow next to the department name.</p>
				</#assign>
				<a href="#"
				   id="related-depts-intro"
				   class="use-introductory auto"
				   data-hash="${introHash("related-depts", "anywhere")}"
				   data-title="Related departments"
				   data-placement="bottom"
				   data-html="true"
				   data-content="${introText}"><i class="icon-question-sign"></i></a>
			</#if>
			<#-- the dropdown itself -->
			<div class="dropdown">
				<ul class="dropdown-menu">
					<#if department.parent??>
						<li><a href="<@routes[routemacro] department.parent />">${department.parent.name}</a></li>
					</#if>
					<li class="disabled"><a>${department.name}</a></li>
					<#list department.children as child>
						<li><a href="<@routes[routemacro] child />">${child.name}</a></li>
					</#list>
				</ul>
			</div>
		</#if>
	</div>
</#macro>

<#macro module_name module withFormatting=true><#compress>
	<#if withFormatting>
		<span class="mod-code">${module.code?upper_case}</span> <span class="mod-name">${module.name}</span>
	<#else>
		${module.code?upper_case} ${module.name}
	</#if>
</#compress></#macro>

<#macro assignment_name assignment withFormatting=true><#compress>
	<#if withFormatting>
		<@module_name assignment.module /> <span class="ass-name">${assignment.name}</span>
	<#else>
		<@module_name assignment.module false /> ${assignment.name}
	</#if>
</#compress></#macro>

<#macro assignment_link assignment>
	<@module_name assignment.module />
	<a href="<@url context='/coursework' page='/module/${assignment.module.code}/${assignment.id}/' />">
		<span class="ass-name">${assignment.name}</span>
	</a>
</#macro>

<#macro admin_assignment_link assignment>
	<@module_name assignment.module />
	<a href="<@url context='/coursework' page='/admin/module/${assignment.module.code}/assignments/${assignment.id}/list' />">
		<span class="ass-name">${assignment.name}</span>
	</a>
</#macro>

<#macro route_name route withFormatting=false>
	<#if withFormatting>
		<span class="route-code">${route.code?upper_case}</span> <span class="route-name">${route.name}</span>
	<#else>
		${route.code?upper_case} ${route.name}
	</#if>
</#macro>

<#macro date date at=false timezone=false seconds=false capitalise=true relative=true split=false shortMonth=false includeTime=true><#--
	--><#noescape><#--
		-->${dateBuilder(date, seconds, at, timezone, capitalise, relative, split, shortMonth, includeTime)}<#--
	--></#noescape><#--
--></#macro>

<#-- This macro assumes that time is a LocalTime -->
<#macro time time twentyFourHour=true seconds=false><#--
	--><#noescape><#--
		-->${timeBuilder(time, twentyFourHour, seconds)}<#--
	--></#noescape><#--
--></#macro>

<#-- Format week ranges for a SmallGroupEvent -->
<#macro weekRanges object><#--
	--><#noescape><#--
		-->${weekRangesFormatter(object)}<#--
	--></#noescape><#--
--></#macro>

<#macro wholeWeekFormat startWeek endWeek academicYear dept short=false><#--
	--><#noescape><#--
		-->${wholeWeekFormatter(startWeek, endWeek, academicYear, dept, short)}<#--
	--></#noescape><#--
--></#macro>

<#macro wholeWeekDateFormat startWeek endWeek academicYear short=false><#--
	--><#noescape><#--
		-->${wholeWeekFormatter(startWeek, endWeek, academicYear, short)}<#--
	--></#noescape><#--
--></#macro>

<#macro singleWeekFormat week academicYear dept short=false><#--
	--><#noescape><#--
		-->${wholeWeekFormatter(week, week, academicYear, dept, short)}<#--
	--></#noescape><#--
--></#macro>

<#macro monitoringPointWeeksFormat validFromWeek requiredFromWeek academicYear dept stripHtml=false><#--
	--><#noescape><#--
		--><#local result = wholeWeekFormatter(validFromWeek, requiredFromWeek, academicYear, dept, false) /><#--
		--><#if stripHtml>${result?replace('<sup>','')?replace('</sup>','')}<#else>${result}</#if><#--
	--></#noescape><#--
--></#macro>

<#macro monitoringPointFormat point stripHtml=false><#--
	--><#noescape><#--
		--><#local result = wholeWeekFormatter(point.validFromWeek, point.requiredFromWeek, point.pointSet.academicYear, point.pointSet.route.department, false) /><#--
		--><#if stripHtml>${result?replace('<sup>','')?replace('</sup>','')}<#else>${result}</#if><#--
	--></#noescape><#--
--></#macro>

<#macro monitoringPointDateFormat point stripHtml=false><#--
	--><#noescape><#--
		--><#local result = wholeWeekFormatter(point.validFromWeek, point.requiredFromWeek, point.pointSet.academicYear, false) /><#--
		--><#if stripHtml>${result?replace('<sup>','')?replace('</sup>','')}<#else>${result}</#if><#--
	--></#noescape><#--
--></#macro>

<#macro weekRangeSelect event><#--
	--><#assign weeks=weekRangeSelectFormatter(event) /><#--
	--><#if weeks?has_content><#--
		--><#noescape><#--
			--><select name="week" class="weekSelector"><#--
				--><#list weekRangeSelectFormatter(event) as week><#--
					--><option value="${week.weekToDisplay}">week ${week.weekToStore}</option><#--
				--></#list><#--
			--></select><#--
		--></#noescape><#--
	--></#if><#--
--></#macro>

<#macro p number singular plural="${singular}s" one="1" zero="0" shownumber=true><#--
--><#if shownumber><#if number=1>${one}<#elseif number=0>${zero}<#else>${number}</#if><#--
--> </#if><#if number=1>${singular}<#else>${plural}</#if></#macro>

<#macro interval start end=""><#--
--><#noescape><#--
	--><#if end?has_content>${intervalFormatter(start, end)}<#--
	--><#else>${intervalFormatter(start)}</#if><#--
--></#noescape><#--
--></#macro>

<#macro tense date future past><#if date.afterNow>${future}<#else>${past}</#if></#macro>

<#macro usergroup_summary ug>
<div class="usergroup-summary">
<#if ug.baseWebgroup??>
	Webgroup "${ug.baseWebgroup}" (${ug.baseWebgroupSize} members)
	<#if ug.allIncludedIds?size gt 0>
	+${ug.allIncludedIds?size} extra users
	</#if>
	<#if ug.allExcludedIds?size gt 0>
	-${ug.allExcludedIds?size} excluded users
	</#if>
<#else>
	<#if ug.allIncludedIds?size gt 0>
	${ug.allIncludedIds?size} users
	</#if>
</#if>
</div>
</#macro>

<#-- comma separated list of users by name -->
<#macro user_list_csv ids>
<@userlookup ids=ids>
	<#list returned_users?keys?sort as id>
		<#local returned_user=returned_users[id] />
		<#if returned_user.foundUser>
			${returned_user.fullName}<#if id_has_next>,</#if>
		<#else>
			${id}<#if id_has_next>,</#if>
		</#if>
	</#list>
	</@userlookup>
</#macro>

<#macro profile_name profile>${profile.fullName}</#macro>
<#macro profile_description profile><span class="profile-description">${profile.description!""}</span></#macro>

<#macro nationality nationality><#--
--><#if nationality = 'British (ex. Channel Islands & Isle of Man)' || nationality = 'British [NO LONGER IN USE: change to 2826]' || nationality = 'NAT code 000 should be used for British'><#--
	--><span class="use-tooltip" data-placement="right" title="${nationality}">British</span><#--
--><#elseif nationality?starts_with('(Obsolete) Formerly ')><#--
	--><span class="use-tooltip" data-placement="right" title="${nationality}">${nationality?substring(20)}</span><#--
--><#else><#--
	-->${nationality}<#--
--></#if></#macro>

<#--	Macro for handling singleton & multiple attachments using a common filepath root

		attachments: either a FileAttachment, or Seq[FileAttachment] please
		page: the URL 'folder' path, for passing to a <@url> macro
		context: string to append to the 'download files' message
		zipFilename: filename (excluding extension) to use for zip downloads

		In the controller, ensure that there are @RequestMappings for the specified 'page', with suffixes of
		/attachment/{filename} for individual files, and
		/attachments/{zipfile}.zip (note trailing 's') for collated files
-->
<#macro download_attachments attachments page context="" zipFilename="download">
	<#if !page?ends_with("/")>
		<#-- ensure page is slash-terminated -->
		<#local page = page + "/" />
	</#if>

	<#local attachment = "" />

	<#if !attachments?is_enumerable>
		<#-- assume it's a FileAttachment -->
		<#local attachment = attachments />
	<#elseif attachments?size == 1>
		<#-- take the first and continue as above -->
		<#local attachment = attachments?first />
	</#if>

	<#if attachment?has_content>
		<#local title>Download file ${attachment.name}<#if context?has_content> ${context}</#if></#local>
		<div class="attachment">
			<@download_link filePath="${page}attachment/${attachment.name?url}" mimeType=attachment.mimeType title="${title}" text="Download ${attachment.name}" />
		</div>
	<#elseif attachments?size gt 1>
		<details class="attachment">
			<summary>
				<#local title>Download a zip file of attachments<#if context?has_content> ${context}</#if></#local>
				<@download_link filePath="${page}attachments/${zipFilename}.zip" mimeType="application/zip" title="${title}" text="Download files as zip" />
			</summary>

			<#list attachments as attachment>
				<#local title>Download file ${attachment.name}<#if context?has_content> ${context}</#if></#local>
				<div class="attachment">
					<@download_link filePath="${page}attachment/${attachment.name?url}" mimeType=attachment.mimeType title="${title}" text="Download ${attachment.name}" />
				</div>
			</#list>
		</details>
	</#if>
</#macro>

<#macro download_link filePath mimeType title="Download file" text="">
	<#if mimeType?matches("^audio/(mpeg|mp3|mp4|ogg|wav)$")>
		<audio controls="controls">
			<source src="${filePath}" type="${mimeType}" />
		</audio>
	<#elseif mimeType?matches("^video/(mp4|webm|ogv)$")>
		<video controls="controls">
			<source src="${filePath}" type="${mimeType}" />
		</video>
	</#if>
	<a class="long-running use-tooltip" href="${filePath}" title="${title}"><i class="icon-download"></i><#if text?has_content> ${text}</#if></a>
</#macro>

<#macro role_definition_description role_definition><#compress>
	${role_definition.description?lower_case}
</#compress></#macro>


<#macro display_deleted_attachments attachments visible="">
	<ul class="deleted-files ${visible}">
		<#list attachments as files>
			<li class="muted deleted"><i class="icon-file-alt"></i> ${files.name}</li>
		</#list>
	</ul>
</#macro>

<#macro course_year_span studentCourseDetails>
		(${(studentCourseDetails.beginYear?string("0000"))!} - ${(studentCourseDetails.endYear?string("0000"))!})
</#macro>

<#macro course_description studentCourseDetails>
		${(studentCourseDetails.course.name)!} (${(studentCourseDetails.course.code?upper_case)!})
</#macro>

<#macro status_on_route studentCourseDetails>
		${(studentCourseDetails.statusOnRoute.fullName?lower_case?cap_first)!}
</#macro>

<#macro status_on_course studentCourseDetails>
		${(studentCourseDetails.statusOnCourse.fullName?lower_case?cap_first)!}
</#macro>

<#macro enrolment_status studentCourseYearDetails>
		${(studentCourseYearDetails.enrolmentStatus.fullName?lower_case?cap_first)!}
</#macro>

<#macro lightbox_link enabled url>
	<#if enabled>
		<a href="${url}" rel="lightbox"><#nested /></a>
	<#else>
		<#nested />
	</#if>
</#macro>

<#macro member_photo member resize="thumbnail" lightbox=true >
	<div class="photo size-${resize}">
		<#if (member.universityId)??>
			<#local fullsize_img><@routes.photo member /></#local>
			<@lightbox_link lightbox fullsize_img>
			<img src="<@routes.photo member />?size=${resize}"/>
			</@lightbox_link>
		<#else>
			<img src="<@url resource="/static/images/no-photo${resize}.jpg" />" />
		</#if>
	</div>
</#macro>

<#macro relation_photo member relationship resize="thumbnail" lightbox=true >
	<div class="photo size-${resize}">
		<#if (member.universityId)??>
			<#local fullsize_img><@routes.relationshipPhoto profile relationship /></#local>
			<@lightbox_link lightbox fullsize_img>
			<img src="<@routes.relationshipPhoto profile relationship />?size=${resize}" />
			</@lightbox_link>
		<#else>
			<img src="<@url resource="/static/images/no-photo${resize}.jpg" />" />
		</#if>
	</div>
</#macro>

<#macro permission_button permission scope action_descr id="" href="" tooltip="" classes="" type="a" data_attr="data-container=body" >
	<#local class></#local>
	<#local title></#local>

	<#if tooltip?has_content>
		<#local title>title='${tooltip}'</#local>
		<#local classes='${classes} use-tooltip'?trim >
	</#if>
    <#if href??><#local href><#noescape>href='${href}'</#noescape></#local></#if>

	<#if !can.do(permission,scope)>
		<#local classes='${classes} disabled use-tooltip'?trim >
		<#local title>title='You do not have permission to ${action_descr}.'</#local>
		<#local data_attr='${data_attr}'?replace("data-toggle=modal","") >
	</#if>

	<#local id_attr></#local>
	<#if id?has_content><#local id_attr>id='${id}'</#local></#if>
	<#if classes??><#local class>class='${classes}'</#local></#if>
	<${type} ${href} ${id_attr} ${class} ${title} ${data_attr}><#noescape><#nested></#noescape></${type}>
</#macro>

<#macro bulk_email emails title subject limit=50>
	<#local separator = ";" />
	<#if user?? && userSetting('bulkEmailSeparator')?has_content>
		<#assign separator = userSetting('bulkEmailSeparator') />
	</#if>

	<#if emails?size gt 0>
		<a class="btn <#if emails?size gt limit>use-tooltip disabled</#if>"
			<#if emails?size gt limit>
		   		title="Emailing is disabled for groups of more than ${limit} students"
			<#else>
				href="mailto:<#list emails as email>${email}<#if email_has_next>${separator}</#if></#list><#if subject?? && subject?length gt 0>?subject=${subject?url}</#if>"
			</#if> >
			<i class="icon-envelope-alt"></i> ${title}
		</a>
	</#if>
</#macro>

<#macro bulk_email_students students title="Email these students" subject="">
	<#local emails = [] />
	<#list students as student>
		<#if student.email??>
			<#local emails = emails + [student.email] />
		</#if>
	</#list>

	<@bulk_email emails title subject />
</#macro>

<#macro bulk_email_student_relationships relationships title="Email these students" subject="">
	<#local emails = [] />
	<#list relationships as rel>
		<#if rel.studentMember?? && rel.studentMember.email??>
			<#local emails = emails + [rel.studentMember.email] />
		</#if>
	</#list>

	<@bulk_email emails title subject />
</#macro>

<#macro help_popover id title="" content="">
	<a class="use-popover"
	   id="popover-${id}"
	   <#if title?has_content>data-title="${title}"</#if>
	   data-content="${content}"
	>
		<i class="icon-question-sign"></i>
	</a>

</#macro>

</#escape>

