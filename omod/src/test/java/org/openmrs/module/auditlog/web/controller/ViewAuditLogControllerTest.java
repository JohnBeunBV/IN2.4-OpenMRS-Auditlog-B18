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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.openmrs.User;
import org.openmrs.api.context.Context;
import org.openmrs.api.context.ContextAuthenticationException;
import org.openmrs.module.auditlog.AuditLog;
import org.openmrs.module.auditlog.AuditLog.Action;
import org.openmrs.module.auditlog.api.AuditLogService;
import org.openmrs.module.auditlog.util.AuditLogConstants;
import org.springframework.ui.ModelMap;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Unit tests for {@link ViewAuditLogController}.
 *
 * The controller relies entirely on the static OpenMRS {@link Context}
 * facade for authentication, privilege checks and service lookup, so it
 * cannot run in a real Spring/Hibernate context without bootstrapping the
 * whole application. Mockito's mockStatic is used instead so each branch
 * (authenticated/unauthenticated, manager/non-manager, with/without a
 * userId filter, invalid userId) can be exercised deterministically.
 *
 * Both showForm() (basic view, SEC-02) and exportAuditLogs() (CSV export
 * with per-user filtering, SEC-01) are covered with happy and unhappy
 * paths, matching the pentest evidence steps documented in the controller
 * itself.
 */
public class ViewAuditLogControllerTest {

    private ViewAuditLogController controller;

    private MockedStatic<Context> mockedContext;

    private AuditLogService auditLogService;

    @Before
    public void setUp() {
        controller = new ViewAuditLogController();
        mockedContext = Mockito.mockStatic(Context.class);
        auditLogService = Mockito.mock(AuditLogService.class);
        mockedContext.when(() -> Context.getService(AuditLogService.class)).thenReturn(auditLogService);
    }

    @After
    public void tearDown() {
        mockedContext.close();
    }

    // =========================================================================
    // showForm()
    // =========================================================================

    @Test
    public void showForm_shouldRequirePrivilegeBeforeFetchingLogs() {
        mockedContext.when(() -> Context.requirePrivilege(AuditLogConstants.PRIV_GET_AUDITLOGS))
                .thenThrow(new ContextAuthenticationException(
                        "Privilege required: " + AuditLogConstants.PRIV_GET_AUDITLOGS));

        ModelMap model = new ModelMap();

        try {
            controller.showForm(model);
            org.junit.Assert.fail("Verwacht ContextAuthenticationException wanneer privilege ontbreekt");
        } catch (ContextAuthenticationException expected) {
            // SEC-02: ontbrekend privilege moet de aanvraag blokkeren voordat data wordt
            // opgehaald
        }

        Mockito.verifyNoInteractions(auditLogService);
        assertTrue("Model mag geen auditLogs bevatten als de privilege-check faalt", model.isEmpty());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void showForm_shouldAddAuditLogsToModelWhenAuthorized() {
        List<AuditLog> logs = new ArrayList<AuditLog>();
        logs.add(new AuditLog());
        Mockito.when(auditLogService.getAuditLogs(
                isNull(List.class), isNull(List.class), isNull(Date.class), isNull(Date.class),
                eq(true), isNull(Integer.class), isNull(Integer.class))).thenReturn(logs);

        ModelMap model = new ModelMap();
        controller.showForm(model);

        mockedContext.verify(() -> Context.requirePrivilege(AuditLogConstants.PRIV_GET_AUDITLOGS));
        assertEquals(logs, model.get("auditLogs"));
    }

    // =========================================================================
    // exportAuditLogs() — authentication / privilege guard (SEC-01, SEC-02)
    // =========================================================================

    @Test
    public void exportAuditLogs_shouldReturn401WhenNotAuthenticated() throws Exception {
        mockedContext.when(Context::isAuthenticated).thenReturn(false);

        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        HttpServletResponse response = Mockito.mock(HttpServletResponse.class);

        controller.exportAuditLogs(request, response);

        Mockito.verify(response).sendError(HttpServletResponse.SC_UNAUTHORIZED, "Authentication required");
        mockedContext.verify(() -> Context.requirePrivilege(Mockito.anyString()), Mockito.never());
        Mockito.verifyNoInteractions(auditLogService);
    }

    @Test
    public void exportAuditLogs_shouldPropagateExceptionWhenAuthenticatedButLacksPrivilege() throws Exception {
        mockedContext.when(Context::isAuthenticated).thenReturn(true);
        mockedContext.when(() -> Context.requirePrivilege(AuditLogConstants.PRIV_GET_AUDITLOGS))
                .thenThrow(new ContextAuthenticationException("Privilege required"));

        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        HttpServletResponse response = Mockito.mock(HttpServletResponse.class);

        try {
            controller.exportAuditLogs(request, response);
            org.junit.Assert.fail("Verwacht ContextAuthenticationException wanneer privilege ontbreekt");
        } catch (ContextAuthenticationException expected) {
            // SEC-01 stap 2: sessie aanwezig maar geen privilege -> blokkade vóór elke
            // data-toegang
        }

        Mockito.verifyNoInteractions(auditLogService);
    }

    // =========================================================================
    // exportAuditLogs() — manager branch (SEC-01 stap 3/4/5)
    // =========================================================================

    @Test
    public void exportAuditLogs_shouldFilterByRequestedUserIdWhenCallerIsManager() throws Exception {
        mockedContext.when(Context::isAuthenticated).thenReturn(true);
        mockedContext.when(() -> Context.hasPrivilege(AuditLogConstants.PRIV_MANAGE_AUDITLOG)).thenReturn(true);

        User targetUser = new User();
        targetUser.setUserId(1);
        User otherUser = new User();
        otherUser.setUserId(2);

        AuditLog logForTargetUser = new AuditLog();
        logForTargetUser.setUser(targetUser);
        logForTargetUser.setAction(Action.CREATED);
        logForTargetUser.setDateCreated(new Date());
        logForTargetUser.setType(User.class);

        AuditLog logForOtherUser = new AuditLog();
        logForOtherUser.setUser(otherUser);
        logForOtherUser.setAction(Action.CREATED);
        logForOtherUser.setDateCreated(new Date());
        logForOtherUser.setType(User.class);

        List<AuditLog> allLogs = new ArrayList<AuditLog>();
        allLogs.add(logForTargetUser);
        allLogs.add(logForOtherUser);
        Mockito.when(auditLogService.getAuditLogs(
                isNull(List.class), isNull(List.class), isNull(Date.class), isNull(Date.class),
                eq(false), isNull(Integer.class), isNull(Integer.class))).thenReturn(allLogs);

        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        Mockito.when(request.getParameter("userId")).thenReturn("1");
        HttpServletResponse response = Mockito.mock(HttpServletResponse.class);
        StringWriter csvOutput = new StringWriter();
        Mockito.when(response.getWriter()).thenReturn(new PrintWriter(csvOutput));

        controller.exportAuditLogs(request, response);

        String csv = csvOutput.toString();
        assertTrue("Manager met userId=1 mag alleen de logs van gebruiker 1 zien", csv.contains(",1,"));
        assertTrue("Manager met userId=1 mag niet de logs van gebruiker 2 zien", !csv.contains(",2,"));
    }

    @Test
    public void exportAuditLogs_shouldReturnAllLogsWhenManagerOmitsUserId() throws Exception {
        mockedContext.when(Context::isAuthenticated).thenReturn(true);
        mockedContext.when(() -> Context.hasPrivilege(AuditLogConstants.PRIV_MANAGE_AUDITLOG)).thenReturn(true);

        User user1 = new User();
        user1.setUserId(1);
        User user2 = new User();
        user2.setUserId(2);

        AuditLog log1 = new AuditLog();
        log1.setUser(user1);
        log1.setAction(Action.CREATED);
        log1.setDateCreated(new Date());
        log1.setType(User.class);
        AuditLog log2 = new AuditLog();
        log2.setUser(user2);
        log2.setAction(Action.UPDATED);
        log2.setDateCreated(new Date());
        log2.setType(User.class);

        List<AuditLog> allLogs = new ArrayList<AuditLog>();
        allLogs.add(log1);
        allLogs.add(log2);
        Mockito.when(auditLogService.getAuditLogs(
                isNull(List.class), isNull(List.class), isNull(Date.class), isNull(Date.class),
                eq(false), isNull(Integer.class), isNull(Integer.class))).thenReturn(allLogs);

        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        Mockito.when(request.getParameter("userId")).thenReturn(null);
        HttpServletResponse response = Mockito.mock(HttpServletResponse.class);
        StringWriter csvOutput = new StringWriter();
        Mockito.when(response.getWriter()).thenReturn(new PrintWriter(csvOutput));

        controller.exportAuditLogs(request, response);

        String csv = csvOutput.toString();
        assertTrue("Manager zonder userId-filter moet alle logs zien (gebruiker 1)", csv.contains(",1,"));
        assertTrue("Manager zonder userId-filter moet alle logs zien (gebruiker 2)", csv.contains(",2,"));
    }

    @Test
    public void exportAuditLogs_shouldReturnEmptyCsvForNonNumericUserId() throws Exception {
        mockedContext.when(Context::isAuthenticated).thenReturn(true);
        mockedContext.when(() -> Context.hasPrivilege(AuditLogConstants.PRIV_MANAGE_AUDITLOG)).thenReturn(true);
        Mockito.when(auditLogService.getAuditLogs(
                isNull(List.class), isNull(List.class), isNull(Date.class), isNull(Date.class),
                eq(false), isNull(Integer.class), isNull(Integer.class))).thenReturn(new ArrayList<AuditLog>());

        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        Mockito.when(request.getParameter("userId")).thenReturn("not-a-number");
        HttpServletResponse response = Mockito.mock(HttpServletResponse.class);
        StringWriter csvOutput = new StringWriter();
        Mockito.when(response.getWriter()).thenReturn(new PrintWriter(csvOutput));

        controller.exportAuditLogs(request, response);

        String csv = csvOutput.toString();
        String[] lines = csv.split("\\r?\\n");
        assertEquals("Ongeldige userId moet alleen de CSV-header opleveren, geen stacktrace of data",
                1, lines.length);
        assertEquals("dateCreated,userId,object,action", lines[0]);
    }

    // =========================================================================
    // exportAuditLogs() — non-manager branch: always restricted to own logs
    // =========================================================================

    @Test
    public void exportAuditLogs_shouldRestrictNonManagerToOwnLogsEvenWithUserIdParam() throws Exception {
        mockedContext.when(Context::isAuthenticated).thenReturn(true);
        mockedContext.when(() -> Context.hasPrivilege(AuditLogConstants.PRIV_MANAGE_AUDITLOG)).thenReturn(false);

        User self = new User();
        self.setUserId(5);
        mockedContext.when(Context::getAuthenticatedUser).thenReturn(self);

        User otherUser = new User();
        otherUser.setUserId(99);

        AuditLog ownLog = new AuditLog();
        ownLog.setUser(self);
        ownLog.setAction(Action.CREATED);
        ownLog.setDateCreated(new Date());
        ownLog.setType(User.class);
        AuditLog otherLog = new AuditLog();
        otherLog.setUser(otherUser);
        otherLog.setAction(Action.CREATED);
        otherLog.setDateCreated(new Date());
        otherLog.setType(User.class);

        List<AuditLog> allLogs = new ArrayList<AuditLog>();
        allLogs.add(ownLog);
        allLogs.add(otherLog);
        Mockito.when(auditLogService.getAuditLogs(
                isNull(List.class), isNull(List.class), isNull(Date.class), isNull(Date.class),
                eq(false), isNull(Integer.class), isNull(Integer.class))).thenReturn(allLogs);

        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        // Probeert via de parameter andermans logs te zien (CWE-639 IDOR-poging)
        Mockito.when(request.getParameter("userId")).thenReturn("99");
        HttpServletResponse response = Mockito.mock(HttpServletResponse.class);
        StringWriter csvOutput = new StringWriter();
        Mockito.when(response.getWriter()).thenReturn(new PrintWriter(csvOutput));

        controller.exportAuditLogs(request, response);

        String csv = csvOutput.toString();
        assertTrue("Niet-manager moet alleen eigen logs zien, ondanks userId=99 in de request",
                csv.contains(",5,"));
        assertTrue("Niet-manager mag nooit andermans logs zien (IDOR-bescherming SEC-01)",
                !csv.contains(",99,"));
    }

    @Test
    public void exportAuditLogs_shouldReturnEmptyCsvForNonManagerWithNoAuthenticatedUser() throws Exception {
        mockedContext.when(Context::isAuthenticated).thenReturn(true);
        mockedContext.when(() -> Context.hasPrivilege(AuditLogConstants.PRIV_MANAGE_AUDITLOG)).thenReturn(false);
        mockedContext.when(Context::getAuthenticatedUser).thenReturn(null);
        Mockito.when(auditLogService.getAuditLogs(
                isNull(List.class), isNull(List.class), isNull(Date.class), isNull(Date.class),
                eq(false), isNull(Integer.class), isNull(Integer.class))).thenReturn(new ArrayList<AuditLog>());

        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        HttpServletResponse response = Mockito.mock(HttpServletResponse.class);
        StringWriter csvOutput = new StringWriter();
        Mockito.when(response.getWriter()).thenReturn(new PrintWriter(csvOutput));

        controller.exportAuditLogs(request, response);

        String[] lines = csvOutput.toString().split("\\r?\\n");
        assertEquals("Zonder bekende authenticated user mag alleen de CSV-header verschijnen",
                1, lines.length);
    }

    // =========================================================================
    // CSV response headers
    // =========================================================================

    @Test
    public void exportAuditLogs_shouldSetCsvContentTypeAndAttachmentHeader() throws Exception {
        mockedContext.when(Context::isAuthenticated).thenReturn(true);
        mockedContext.when(() -> Context.hasPrivilege(AuditLogConstants.PRIV_MANAGE_AUDITLOG)).thenReturn(true);
        Mockito.when(auditLogService.getAuditLogs(
                isNull(List.class), isNull(List.class), isNull(Date.class), isNull(Date.class),
                eq(false), isNull(Integer.class), isNull(Integer.class))).thenReturn(new ArrayList<AuditLog>());

        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        HttpServletResponse response = Mockito.mock(HttpServletResponse.class);
        Mockito.when(response.getWriter()).thenReturn(new PrintWriter(new StringWriter()));

        controller.exportAuditLogs(request, response);

        Mockito.verify(response).setContentType("text/csv");
        Mockito.verify(response).setHeader("Content-Disposition", "attachment; filename=audit_export.csv");
    }
}