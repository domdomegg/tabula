<#escape x as x?html>
	<#macro member_note_list memberNotes>
		<#local canDeletePurgeMemberNote = can.do("MemberNotes.Delete", profile) />
		<#local canEditMemberNote = can.do("MemberNotes.Update", profile) />
		<#local canCreateMemberNote = can.do("MemberNotes.Create", profile) />

		<div class="list-controls">
			<#if canCreateMemberNote>
				<a class="btn-like create"
					data-toggle="modal"
					data-target="#note-modal"
					href="#note-modal"
					data-url="<@routes.create_member_note profile/>"
					title="Create new note"
				>
						<i class="icon-edit"></i>
						New administrative note
				</a>
			</#if>
			<a class="toggle-all-details btn-like open-all-details" title="Expand all notes"><i class="icon-plus"></i> Expand all</a>
			<a class="toggle-all-details btn-like close-all-details hide" title="Collapse all notes"><i class="icon-minus"></i> Collapse all</a>
		</div>


		<#list memberNotes as note>

			<#local deleteTools =" disabled" />
			<#local deleted ="" />
			<#local nonDeleteTools ="" />
			<#local attachments = note.attachments?? && note.attachments?size gt 0 />

			<#if note.deleted>
				<#local deleteTools = "" />
				<#local deleted ="deleted muted" />
				<#local nonDeleteTools =" disabled" />
			</#if>

			<details class="${deleted}">
				<summary>
					<div class="detail-arrow-fix">
						<#if canEditMemberNote>
							<div class="member-note-toolbar">
								<a data-toggle="modal" data-target="#note-modal" href="#note-modal" data-url="<@routes.edit_member_note note />" class="btn-like edit${nonDeleteTools}" title="Edit note"><i class="icon-edit" ></i></a>
								<#if canDeletePurgeMemberNote>
									<a href="<@routes.delete_member_note note />" class="btn-like delete${nonDeleteTools}" title="Delete note"><i class="icon-trash"></i></a>
									<a href="<@routes.restore_member_note note />" class="btn-like restore${deleteTools}" title="Restore note"><i class="icon-repeat"></i></a>
									<a href="<@routes.purge_member_note note />" class="btn-like purge${deleteTools}" title="Purge note"><i class="icon-remove"></i></a>
								</#if>
								<i class="icon-spinner icon-spin"></i>
							</div>
						</#if>

						<div class="date-title">
							<div class="date"><@fmt.date date=note.creationDate includeTime=false shortMonth=true /><div style="clear: both;"></div></div>
							<div class="title">${note.title!} <#if attachments><i class="icon-paper-clip"></i></#if></div>
						</div>
					</div>
				</summary>

				<div class="description">
					<#if note.note??>
						<#noescape>${note.escapedNote}</#noescape>
					</#if>
					<#if attachments >
						<@fmt.display_deleted_attachments note.attachments note.deleted?string("","hidden") />
						<div class="deleted-files ${note.deleted?string('hidden','')}"><@fmt.download_attachments note.attachments "/profiles/notes/${note.id}/" /></div>
					</#if>
					<small class="muted clearfix">Student note created by ${note.creator.fullName}, <@fmt.date note.lastUpdatedDate /></small>
				</div>
			</details>
		</#list>
	</#macro>
</#escape>
