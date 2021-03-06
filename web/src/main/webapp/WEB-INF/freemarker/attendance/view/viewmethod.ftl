<#escape x as x?html>

  <#function route_function dept>
    <#local result><@routes.attendance.viewHomeForYear dept academicYear /></#local>
    <#return result />
  </#function>
  <@fmt.id7_deptheader title="View and record attendance for ${academicYear.toString}" route_function=route_function preposition="in" />

  <#if hasSchemes>
    <ul>
      <li><h3><a href="<@routes.attendance.viewStudents department academicYear />">View by student and report to SITS e:Vision</a></h3></li>
      <li><h3><a href="<@routes.attendance.viewPoints department academicYear />">View by point</a></h3></li>
      <#if can.do("MonitoringPoints.View", department)>
        <#list department.displayedStudentRelationshipTypes as relationshipType>
          <li><h3><a href="<@routes.attendance.viewAgents department academicYear relationshipType />">View by ${relationshipType.agentRole}</a></h3></li>
        </#list>
      </#if>
    </ul>
  <#else>
    <p class="alert alert-info">There are no monitoring point schemes for this department for ${academicYear.toString}.</p>
  </#if>

</#escape>