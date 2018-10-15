

Note:
<#if assignment.collectSubmissions>
- <@fmt.p number=numAllocated!0 singular="student" plural="students" /> <@fmt.p number=numAllocated!0 singular="is" plural="are" shownumber=false /> allocated to you for marking
  <#list studentsAtStagesCount as info>
    <#if info.count gt 0>
 - ${info.stageName}: <@fmt.p number=info.count!0 singular="student" plural="students" />
     </#if>
  </#list>
- <@fmt.p number=numReleasedFeedbacks!0 singular="student" plural="students" /> allocated to you <@fmt.p number=numReleasedFeedbacks!0 singular="has" plural="have" shownumber=false /> been released for marking
 - <@fmt.p number=numReleasedSubmissionsFeedbacks!0 singular="student" plural="students" /> <@fmt.p number=numReleasedSubmissionsFeedbacks!0 singular="has" plural="have" shownumber=false /> submitted work
 - <@fmt.p number=numReleasedNoSubmissionsFeedbacks!0 singular="student" plural="students" /> <@fmt.p number=numReleasedNoSubmissionsFeedbacks!0 singular="has" plural="have" shownumber=false /> not submitted work
<#else>
- <@fmt.p number=numReleasedFeedbacks!0 singular="student" plural="students" /> <@fmt.p number=numReleasedFeedbacks!0 singular="is" plural="are" shownumber=false /> allocated to you for marking
- This assignment does not require students to submit work to Tabula
</#if>