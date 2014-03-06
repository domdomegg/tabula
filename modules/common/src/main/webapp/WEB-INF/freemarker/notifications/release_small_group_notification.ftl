You have been allocated the following small teaching groups:

<#list groups as group>
${group.groupSet.name} ${group.groupSet.format.description} for ${group.groupSet.module.code?upper_case} - ${group.groupSet.module.name}
${group.name} - <@fmt.p number=group.students.members?size singular="student"/>

<#list group.events as event><#if !event.unscheduled>
<@fmt.time time=event.startTime /> ${event.day.name}, ${event.location!}, <@fmt.weekRanges event />
</#if></#list>

</#list>