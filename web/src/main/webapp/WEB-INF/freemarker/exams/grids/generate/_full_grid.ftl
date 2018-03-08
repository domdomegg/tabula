<#escape x as x?html>

<#macro showMarks entity markType>
	<#list perYearColumns?keys?sort as year>
		<#if gridOptionsCommand.showComponentMarks>
		<th><span class="use-tooltip" title="${markType.description}">${markType.label}</span></th>
		</#if>
		<#list mapGet(perYearColumns, year) as column>
		<td>
			<#assign hasValue = mapGet(perYearColumnValues, column)?? && mapGet(mapGet(perYearColumnValues, column), entity)?? && mapGet(mapGet(mapGet(perYearColumnValues, column), entity), year)?? />
			<#if hasValue>
				<#assign values = mapGet(mapGet(mapGet(mapGet(perYearColumnValues, column), entity), year), markType) />
				<#list values as value><#noescape>${value.toHTML}</#noescape><#if value_has_next>,</#if></#list>
			</#if>
		</td>
		</#list>
		<#if !mapGet(perYearColumns, year)?has_content><td class="spacer">&nbsp;</td></#if>
		<#if !year_has_next><td class="spacer">&nbsp;</td></#if>
	</#list>
</#macro>

<table class="table table-condensed grid <#if !gridOptionsCommand.showComponentMarks>with-hover</#if>">
	<tbody>
	<#-- Year row -->
	<tr class="year">
		<#list studentInformationColumns as column><td class="borderless">&nbsp;</td></#list>
		<#if !gridOptionsCommand.showComponentMarks><td class="spacer">&nbsp;</td></#if>
		<#list perYearColumns?keys?sort as year>
			<#if gridOptionsCommand.showComponentMarks><td class="spacer">&nbsp;</td></#if>
			<th colspan="${mapGet(perYearColumns, year)?size}">Year ${year}</th>
			<#if !year_has_next><td class="spacer">&nbsp;</td></#if>
		</#list>
		<#list summaryColumns as column><td class="borderless">&nbsp;</td></#list>
	</tr>
	<#-- Category row -->
	<tr class="category">
		<#list studentInformationColumns as column><td class="borderless">&nbsp;</td></#list>
		<#if !gridOptionsCommand.showComponentMarks><td class="spacer">&nbsp;</td></#if>
		<#list perYearColumns?keys?sort as year>
			<#if gridOptionsCommand.showComponentMarks><td class="spacer">&nbsp;</td></#if>
			<#assign currentCategory = '' />
			<#list mapGet(perYearColumns, year) as column>
				<#if column.category?has_content>
					<#if currentCategory != column.category>
						<#assign currentCategory = column.category />
						<th class="rotated" colspan="${mapGet(perYearColumnCategories, year)[column.category]?size}"><div class="rotate">${column.category}</div></th>
					</#if>
				<#else>
					<td>&nbsp;</td>
				</#if>
			</#list>
			<#if !mapGet(perYearColumns, year)?has_content><td class="spacer">&nbsp;</td></#if>
			<#if !year_has_next><td class="spacer">&nbsp;</td></#if>
		</#list>
		<#assign currentCategory = '' />
		<#list summaryColumns as column>
			<#if column.category?has_content>
				<#if currentCategory != column.category>
					<#assign currentCategory = column.category />
					<th class="rotated" colspan="${chosenYearColumnCategories[column.category]?size}"><div class="rotate">${column.category}</div></th>
				</#if>
			<#else>
				<td>&nbsp;</td>
			</#if>
		</#list>
	</tr>
	<#-- Header row -->
	<tr class="header">
		<#list studentInformationColumns as column>
			<th <#if !column.secondaryValue?has_content>rowspan="2"</#if>>${column.title}</th>
		</#list>
		<#if !gridOptionsCommand.showComponentMarks><td class="spacer">&nbsp;</td></#if>
		<#list perYearColumns?keys?sort as year>
			<#if gridOptionsCommand.showComponentMarks><td class="spacer">&nbsp;</td></#if>
			<#list mapGet(perYearColumns, year) as column>
				<th class="rotated <#if column.boldTitle>bold</#if> <#if column.category?has_content>has-category</#if>" <#if !column.secondaryValue?has_content>rowspan="2"</#if>><div class="rotate">${column.title}</div></th>
			</#list>
			<#if !mapGet(perYearColumns, year)?has_content><td class="spacer">&nbsp;</td></#if>
			<#if !year_has_next><td class="spacer">&nbsp;</td></#if>
		</#list>
		<#list summaryColumns as column>
			<th class="rotated <#if column.boldTitle>bold</#if> <#if column.category?has_content>has-category</#if>" <#if !column.secondaryValue?has_content>rowspan="2"</#if>><div class="rotate">${column.title}</div></th>
		</#list>
	</tr>
	<#-- Secondary value row -->
	<tr class="secondary">
		<#if !gridOptionsCommand.showComponentMarks><td class="spacer">&nbsp;</td></#if>
		<#list perYearColumns?keys?sort as year>
			<#if gridOptionsCommand.showComponentMarks><td class="spacer">&nbsp;</td></#if>
			<#list mapGet(perYearColumns, year) as column>
				<#if column.secondaryValue?has_content><th class="<#if column.boldTitle>bold</#if> <#if column.category?has_content>has-category</#if>">${column.secondaryValue}</th></#if>
			</#list>
			<#if !year_has_next><td class="spacer">&nbsp;</td></#if>
		</#list>
	</tr>

	<#-- Entities -->
	<#list entities as entity>
		<tr class="student <#if entity_index%2 == 1>odd</#if>">
			<#list studentInformationColumns as column>
				<td <#if gridOptionsCommand.showComponentMarks>rowspan="3"</#if>>
					<#assign hasValue = mapGet(chosenYearColumnValues, column)?? && mapGet(mapGet(chosenYearColumnValues, column), entity)?? />
					<#if hasValue>
						<#noescape>${mapGet(mapGet(chosenYearColumnValues, column), entity).toHTML}</#noescape>
					</#if>
				</td>
			</#list>

			<#if !gridOptionsCommand.showComponentMarks><td class="spacer">&nbsp;</td></#if>

			<@showMarks entity ExamGridColumnValueType.Overall />

			<#list summaryColumns as column>
				<td <#if gridOptionsCommand.showComponentMarks>rowspan="3"</#if>>
					<#assign hasValue = mapGet(chosenYearColumnValues, column)?? && mapGet(mapGet(chosenYearColumnValues, column), entity)?? />
					<#if hasValue>
						<#noescape>${mapGet(mapGet(chosenYearColumnValues, column), entity).toHTML}</#noescape>
					</#if>
				</td>
			</#list>
		</tr>

		<#if gridOptionsCommand.showComponentMarks>
			<tr class="assignments <#if entity_index%2 == 1>odd</#if>">
				<@showMarks entity ExamGridColumnValueType.Assignment />
			</tr>
			<tr class="exams <#if entity_index%2 == 1>odd</#if>">
				<@showMarks entity ExamGridColumnValueType.Exam />
			</tr>
		</#if>
	</#list>
	</tbody>
</table>
</#escape>