<#import "*/coursework_components.ftl" as components />
<#escape x as x?html>

<h1>Assignments for marking</h1>

<@components.marker_assignment_list id="marker-action" title="Action required" assignments=markerInformation.actionRequiredAssignments verb="Mark" />
<@components.marker_assignment_list id="marker-noaction" title="No action required" assignments=markerInformation.noActionRequiredAssignments verb="Review" expand_by_default=(!markerInformation.actionRequiredAssignments?has_content) />
<@components.marker_assignment_list id="marker-upcoming" title="Upcoming" assignments=markerInformation.upcomingAssignments verb="Mark" expand_by_default=(!markerInformation.actionRequiredAssignments?has_content && !markerInformation.noActionRequiredAssignments?has_content) />
<@components.marker_assignment_list id="marker-completed" title="Completed" assignments=markerInformation.completedAssignments verb="Review" expand_by_default=(!markerInformation.actionRequiredAssignments?has_content && !markerInformation.noActionRequiredAssignments?has_content && !markerInformation.upcomingAssignments?has_content) />

</#escape>