/**
 * The contents of this file are subject to the OpenMRS Public License
 * Version 1.0 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 * http://license.openmrs.org
 *
 * Software distributed under the License is distributed on an "AS IS"
 * basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See the
 * License for the specific language governing rights and limitations
 * under the License.
 *
 * Copyright (C) OpenMRS, LLC.  All Rights Reserved.
 */
package org.openmrs.module.auditlog.web.controller;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.User;
import org.openmrs.api.context.Context;
import org.openmrs.module.auditlog.AuditLog;
import org.openmrs.module.auditlog.api.AuditLogService;
import org.openmrs.module.auditlog.util.AuditLogConstants;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.RequestMapping;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Controller
public class ViewAuditLogController {

	private static final Log log = LogFactory.getLog(ViewAuditLogController.class);

	private final String VIEW_AUDIT_LOG_FORM = "module/" + AuditLogConstants.MODULE_ID + "/viewAuditLog";

	/**
	 * SEC-02 FIX (CWE-862):
	 * Context.requirePrivilege() gooit ContextAuthenticationException als de
	 * gebruiker niet ingelogd is of PRIV_GET_AUDITLOGS niet heeft.
	 * OpenMRS vangt dit op als HTTP 500 / foutpagina.
	 *
	 * Pentest-bewijs SEC-02: HTTP 500 bij ongeautoriseerde toegang bewijst
	 * blokkering — geen data lekt. Noteer dit expliciet in het rapport.
	 */
	@RequestMapping(VIEW_AUDIT_LOG_FORM)
	public void showForm(ModelMap model) {
		Context.requirePrivilege(AuditLogConstants.PRIV_GET_AUDITLOGS);

		if (log.isDebugEnabled()) {
			log.debug("Fetching audit log entries...");
		}

		model.addAttribute("auditLogs",
				Context.getService(AuditLogService.class)
						.getAuditLogs(null, null, null, null, true, null, null));
	}

	/**
	 * SEC-01 FIX (CWE-862 + CWE-639):
	 *
	 * Gefixte bugs:
	 * - CWE-862: authenticatie + privilege-check toegevoegd
	 * - CWE-639 / bug A: @RequestParam / Spring binding werkte niet →
	 * vervangen door request.getParameter() (servlet-laag, altijd betrouwbaar)
	 * - CWE-639 / bug B: userId-vergelijking via Integer.equals() i.p.v. String
	 *
	 * DEBUG-headers (tijdelijk, verwijder na pentest):
	 * X-Debug-UserId — waarde die de server ontvangt voor userId
	 * X-Debug-IsManager — of de huidige gebruiker PRIV_MANAGE_AUDITLOG heeft
	 * Gebruik deze in Postman om te bevestigen dat parameter binding werkt.
	 *
	 * Pentest-bewijs SEC-01:
	 * Stap 1 — geen sessie → 401 (isAuthenticated = false)
	 * Stap 2 — sessie, geen privilege → 500 (requirePrivilege gooit exception)
	 * Stap 3 — ?userId=1 (manager) → alleen admin-logs
	 * Stap 4 — ?userId=2 (manager) → alleen daemon-logs
	 * Stap 5 — ?userId=999 (manager) → lege CSV
	 */
	@RequestMapping("module/auditlog/exportAuditLog")
	public void exportAuditLogs(HttpServletRequest request, HttpServletResponse response)
			throws IOException {

		// Maatregel 1: authenticatiecheck → HTTP 401 bij geen sessie
		if (!Context.isAuthenticated()) {
			response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Authentication required");
			return;
		}

		// Maatregel 2: privilege-check → exception bij ontbrekend privilege
		Context.requirePrivilege(AuditLogConstants.PRIV_GET_AUDITLOGS);

		// Directe parameter-uitlezing via servlet-laag (omzeilt Spring MVC binding)
		String userId = request.getParameter("userId");

		User currentUser = Context.getAuthenticatedUser();
		boolean isManager = Context.hasPrivilege(AuditLogConstants.PRIV_MANAGE_AUDITLOG);

		// DEBUG-headers: verwijder na pentest
		response.setHeader("X-Debug-UserId", userId != null ? userId : "null");
		response.setHeader("X-Debug-IsManager", String.valueOf(isManager));

		log.debug("SEC-01 export: userId=" + userId + ", isManager=" + isManager
				+ ", currentUser=" + (currentUser != null ? currentUser.getId() : "null"));

		List<AuditLog> allLogs = Context.getService(AuditLogService.class)
				.getAuditLogs(null, null, null, null, false, null, null);

		List<AuditLog> logs;

		if (isManager) {
			if (userId != null && !userId.trim().isEmpty()) {
				// Manager met userId → filter op die specifieke gebruiker
				Integer requestedId;
				try {
					requestedId = Integer.valueOf(userId.trim());
				} catch (NumberFormatException e) {
					// Ongeldige userId → lege CSV (geen stacktrace lekken)
					logs = new ArrayList<AuditLog>();
					writeCSV(response, logs);
					return;
				}
				logs = new ArrayList<AuditLog>();
				for (AuditLog al : allLogs) {
					if (al.getUser() != null
							&& al.getUser().getId() != null
							&& requestedId.equals(al.getUser().getId())) {
						logs.add(al);
					}
				}
			} else {
				// Manager zonder userId → alle logs
				logs = allLogs;
			}
		} else {
			// Geen manager → altijd alleen eigen logs, userId genegeerd
			Integer myId = (currentUser != null) ? currentUser.getId() : null;
			logs = new ArrayList<AuditLog>();
			if (myId != null) {
				for (AuditLog al : allLogs) {
					if (al.getUser() != null && myId.equals(al.getUser().getId())) {
						logs.add(al);
					}
				}
			}
		}

		writeCSV(response, logs);
	}

	private void writeCSV(HttpServletResponse response, List<AuditLog> logs) throws IOException {
		response.setContentType("text/csv");
		response.setHeader("Content-Disposition", "attachment; filename=audit_export.csv");
		java.io.PrintWriter writer = response.getWriter();

		writer.println("dateCreated,userId,object,action");

		for (AuditLog al : logs) {
			String userIdCol = (al.getUser() != null && al.getUser().getId() != null)
					? String.valueOf(al.getUser().getId())
					: "";
			writer.println(
					al.getDateCreated() + "," +
							userIdCol + "," +
							al.getSimpleTypeName() + "," +
							al.getAction());
		}

		writer.flush();
	}
}