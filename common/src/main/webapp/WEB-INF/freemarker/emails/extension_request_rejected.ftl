Your extension request has been rejected for the assignment '${assignment.name}' for ${module.code?upper_case} ${module.name}.

<#if originalAssignmentDate??>
Your deadline for the assignment is ${originalAssignmentDate}. Any submissions made after the deadline will be subject to the usual late penalties.
</#if>

<#if extension.reviewerComments?has_content>
The administrator left the following comments:

${extension.reviewerComments}

</#if>