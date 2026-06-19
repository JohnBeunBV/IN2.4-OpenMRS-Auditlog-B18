<ul id="menu">
	<li class="first">
		<a href="${pageContext.request.contextPath}/admin"><spring:message code="admin.title.short" /></a>
	</li>

	<c:set var="currentUri" value="${pageContext.request.requestURI}" />
	<li <c:if test="${fn:contains(currentUri, '/viewAuditLog')}">class="active"</c:if>>
		<a href="${pageContext.request.contextPath}/module/${moduleId}/viewAuditLog.htm">
			<spring:message	code="${moduleId}.viewAuditLog" />
		</a>
	</li>
	
	<%-- Add further links here --%>
</ul>

<h2><spring:message code="${moduleId}.title" /></h2>