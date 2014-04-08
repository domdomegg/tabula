<#escape x as x?html>

<#if !ajax && user.loggedIn && user.firstName??>
	<h5>Hello, ${user.firstName}</h5>
<#elseif !ajax>
	<h5>Hello</h5>
</#if>	

<#if !user.loggedIn>
	<p>
		You're currently not signed in. <a class="sso-link" href="<@sso.loginlink />">Sign in</a>
		to see a personalised view.
	</p>
<#else>
	<ul id="home-list">
		<#-- TODO can we guard this, or would it be too expensive to be worth the bother? -->
		<li><h2><a href="<@url page="/" context="/coursework" />">Coursework Management</a></h2>
			<#if user.staff>
				<span class="hint">Create assignments, give feedback and add marks</span>
			<#else>
				<span class="hint">Submit coursework, view feedback and see your marks</span>
			</#if>
		</li>
		
		<#if features.smallGroupTeaching>
			<li><h2><a href="<@url page="/" context="/groups" />">Small Group Teaching</a></h2>
				<#if user.staff>
					<span class="hint">Create seminars, tutorials and lab groups</span>
				<#else>
					<span class="hint">View your seminars, tutorials and lab groups</span>
				</#if>
			</li>
		</#if>
	
		<#if user.staff>
			<li><h2><a href="<@url page="/" context="/profiles" />">Student Profiles</a></h2>
				<span class="hint">View student information and edit personal tutors</span>
			</li>
		<#elseif user.student>
			<li><h2><a href="<@url page="/" context="/profiles" />">My Student Profile</a></h2>
				<span class="hint">View your student information</span>
			</li>
		</#if>
		
		<#if features.attendanceMonitoring>
			<#if user.staff>
				<li>
					<h2><a href="<@url page="/" context="/attendance" />">Attendance Monitoring</a></h2>
					<span class="hint">View and record attendance at specified monitoring points</span>
				</li>
			<#elseif user.student>
				<li>
					<h2><a href="<@url page="/profile" context="/attendance" />">My Attendance</a></h2>
					<span class="hint">View your attendance at specified monitoring points</span>
				</li>
			</#if>
		</#if>
		
		<#if canAdmin>
			<li>
				<h2><a href="<@url page="/" context="/admin" />">Administration and Permissions</a></h2>
				<span class="hint">Manage department, module and route settings and permissions</span>
			</li>
		</#if>
	</ul>

	<#if features.activityStreams>
		<#import "*/activity_macros.ftl" as activity />
		<div class="home-page-activity">
		<h3>Activity stream</h3>
		<@activity.activity_stream max=5 />
		</div>
	</#if>
</#if>

<#if !ajax && (activeSpringProfiles!"") == "sandbox">
	<div class="alert alert-block">
		<h4><i class="icon-sun"></i> Tabula Sandbox</h4>
		
		<p>This instance of Tabula is a sandbox, used for testing Tabula's features and functionality without affecting
		any real data.</p>
		
		<p>There are some important differences in this version of Tabula:</p>
		
		<ul>
		  <li>Features and functionality may be enabled that are not enabled on the live system
		  <li>No staff data exists on the system at all
		  <li>Student data is automatically generated, using fake names
		  <li>No emails will be sent by the system, so feel free to play around without worrying
		</ul>
		
		<p>Please make sure you do not upload any sensitive data to this system, such as anything relating to real students.</p>
		
		<p>To get access to administration on this system, please send us an email:</p>
		
		<button type="button" class="btn btn-primary" id="request-sandbox-access">Request access</button>
		
		<script type="text/javascript">
			jQuery(function($) {
				$('#request-sandbox-access').on('click', function(e) {
					e.stopPropagation();
					e.preventDefault();
					$('#app-feedback-link').click();
				});
			});
		</script>
	</div>
</#if>

</#escape>