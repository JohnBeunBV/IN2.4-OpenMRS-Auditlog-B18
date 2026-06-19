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
package org.openmrs.module.auditlog.web.dwr;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.Serializable;
import java.util.LinkedHashSet;
import java.util.Set;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.openmrs.api.context.Context;
import org.openmrs.api.context.ContextAuthenticationException;
import org.openmrs.module.auditlog.AuditLog;
import org.openmrs.module.auditlog.AuditLog.Action;
import org.openmrs.module.auditlog.api.AuditLogService;
import org.openmrs.module.auditlog.web.util.AuditLogWebConstants;

/**
 * Unit tests voor {@link DWRAuditLogService}.
 *
 * DWRAuditLogService haalt via de statische Context-facade zijn service op en
 * voert privilege-checks uit. Alle afhankelijkheden worden gestubbed met
 * Mockito's mockStatic via anonieme klassen (Java 1.7-compatibel).
 */
public class DWRAuditLogServiceTest {

    private MockedStatic<Context> mockedContext;
    private AuditLogService auditLogService;
    private DWRAuditLogService dwrService;

    @Before
    public void setUp() {
        mockedContext = Mockito.mockStatic(Context.class);
        auditLogService = Mockito.mock(AuditLogService.class);
        mockedContext.when(new MockedStatic.Verification() {
            public void apply() throws Throwable {
                Context.getService(AuditLogService.class);
            }
        }).thenReturn(auditLogService);
        dwrService = new DWRAuditLogService();
    }

    @After
    public void tearDown() {
        mockedContext.close();
    }

    // =========================================================================
    // Privilege-controle
    // =========================================================================

    @Test(expected = ContextAuthenticationException.class)
    public void getAuditLogDetails_shouldEnforcePrivilege() {
        mockedContext.when(new MockedStatic.Verification() {
            public void apply() throws Throwable {
                Context.requirePrivilege(AuditLogWebConstants.PRIV_VIEW_AUDITLOG);
            }
        }).thenThrow(new ContextAuthenticationException("Privilege required"));

        dwrService.getAuditLogDetails("any-uuid");
    }

    // =========================================================================
    // Null/blanco uuid — geen service-aanroep verwacht
    // =========================================================================

    @Test
    public void getAuditLogDetails_shouldReturnNullForNullUuid() {
        AuditLogDetails result = dwrService.getAuditLogDetails(null);

        assertNull("null uuid moet null opleveren", result);
        Mockito.verifyNoInteractions(auditLogService);
    }

    @Test
    public void getAuditLogDetails_shouldReturnNullForBlankUuid() {
        AuditLogDetails result = dwrService.getAuditLogDetails("   ");

        assertNull("Blanco uuid moet null opleveren", result);
        Mockito.verifyNoInteractions(auditLogService);
    }

    @Test
    public void getAuditLogDetails_shouldReturnNullForEmptyUuid() {
        AuditLogDetails result = dwrService.getAuditLogDetails("");

        assertNull("Lege uuid moet null opleveren", result);
        Mockito.verifyNoInteractions(auditLogService);
    }

    // =========================================================================
    // Onbekende uuid — service geeft null terug
    // =========================================================================

    @Test
    public void getAuditLogDetails_shouldReturnNullWhenAuditLogNotFound() {
        Mockito.when(auditLogService.getObjectByUuid(AuditLog.class, "unknown-uuid")).thenReturn(null);

        AuditLogDetails result = dwrService.getAuditLogDetails("unknown-uuid");

        assertNull("Onbekende uuid moet null opleveren", result);
    }

    // =========================================================================
    // DELETED-actie
    // =========================================================================

    @Test
    public void getAuditLogDetails_shouldReturnDetailsForDeletedObject() {
        AuditLog auditLog = new AuditLog();
        auditLog.setAction(Action.DELETED);
        auditLog.setType(String.class);
        auditLog.setIdentifier(42);
        auditLog.setUuid("deleted-uuid");

        Mockito.when(auditLogService.getObjectByUuid(AuditLog.class, "deleted-uuid")).thenReturn(auditLog);

        AuditLogDetails result = dwrService.getAuditLogDetails("deleted-uuid");

        assertNotNull("DELETED auditlog moet een AuditLogDetails opleveren", result);
        assertEquals("DELETED", result.getAction());
        assertEquals("deleted-uuid", result.getUuid());
        assertFalse("objectExists moet false zijn voor een verwijderd object", result.isObjectExists());
    }

    // =========================================================================
    // CREATED-actie — object bestaat niet meer in de database
    // =========================================================================

    @Test
    public void getAuditLogDetails_shouldReturnDetailsForCreatedObjectThatNoLongerExists() {
        AuditLog auditLog = new AuditLog();
        auditLog.setAction(Action.CREATED);
        auditLog.setType(String.class);
        auditLog.setIdentifier(99);
        auditLog.setUuid("created-uuid");

        Mockito.when(auditLogService.getObjectByUuid(AuditLog.class, "created-uuid")).thenReturn(auditLog);
        Mockito.when(auditLogService.getObjectById(String.class, 99)).thenReturn(null);

        AuditLogDetails result = dwrService.getAuditLogDetails("created-uuid");

        assertNotNull(result);
        assertEquals("CREATED", result.getAction());
        assertFalse("objectExists moet false zijn als het object niet meer in de DB staat",
                result.isObjectExists());
    }

    // =========================================================================
    // CREATED-actie — object bestaat nog in de database
    // =========================================================================

    @Test
    public void getAuditLogDetails_shouldSetObjectExistsTrueWhenObjectFoundInDb() {
        AuditLog auditLog = new AuditLog();
        auditLog.setAction(Action.CREATED);
        auditLog.setType(String.class);
        auditLog.setIdentifier(7);
        auditLog.setUuid("exists-uuid");

        Mockito.when(auditLogService.getObjectByUuid(AuditLog.class, "exists-uuid")).thenReturn(auditLog);
        Mockito.when(auditLogService.getObjectById(String.class, 7)).thenReturn("someObject");

        AuditLogDetails result = dwrService.getAuditLogDetails("exists-uuid");

        assertNotNull(result);
        assertTrue("objectExists moet true zijn als het object nog in de DB staat", result.isObjectExists());
    }

    // =========================================================================
    // Child-logs — getChildAuditLogs() returnt een Set<AuditLog>
    // =========================================================================

    @Test
    public void getAuditLogDetails_shouldIncludeChildLogDetailsWhenPresent() {
        AuditLog childLog = new AuditLog();
        childLog.setAction(Action.UPDATED);
        childLog.setType(String.class);
        childLog.setIdentifier(2);
        childLog.setUuid("child-uuid");

        Set<AuditLog> children = new LinkedHashSet<AuditLog>();
        children.add(childLog);

        AuditLog parentLog = Mockito.mock(AuditLog.class);
        Mockito.when(parentLog.getAction()).thenReturn(Action.DELETED);
        Mockito.when(parentLog.getType()).thenReturn((Class) String.class);
        Mockito.when(parentLog.getIdentifier()).thenReturn((Serializable) Integer.valueOf(1));
        Mockito.when(parentLog.getUuid()).thenReturn("parent-uuid");
        Mockito.when(parentLog.getSimpleTypeName()).thenReturn("String");
        Mockito.when(parentLog.getOpenmrsVersion()).thenReturn("1.8.3");
        Mockito.when(parentLog.hasChildLogs()).thenReturn(true);
        Mockito.when(parentLog.getChildAuditLogs()).thenReturn(children);

        Mockito.when(auditLogService.getObjectByUuid(AuditLog.class, "parent-uuid")).thenReturn(parentLog);

        AuditLogDetails result = dwrService.getAuditLogDetails("parent-uuid");

        assertNotNull(result);
        assertNotNull("Child-logs moeten aanwezig zijn in de details", result.getChildAuditLogDetails());
        assertEquals("Er moet precies 1 child-log zijn", 1, result.getChildAuditLogDetails().size());
        assertEquals("child-uuid", result.getChildAuditLogDetails().get(0).getUuid());
    }

    @Test
    public void getAuditLogDetails_shouldNotSetChildDetailsWhenNoChildLogs() {
        AuditLog auditLog = new AuditLog();
        auditLog.setAction(Action.DELETED);
        auditLog.setType(String.class);
        auditLog.setIdentifier(5);
        auditLog.setUuid("no-children-uuid");

        Mockito.when(auditLogService.getObjectByUuid(AuditLog.class, "no-children-uuid")).thenReturn(auditLog);

        AuditLogDetails result = dwrService.getAuditLogDetails("no-children-uuid");

        assertNotNull(result);
        assertNull("Zonder child-logs mag childAuditLogDetails niet gezet worden",
                result.getChildAuditLogDetails());
    }

    // =========================================================================
    // Velden in het resultaat
    // =========================================================================

    @Test
    public void getAuditLogDetails_shouldPopulateUuidAndActionAndIdentifierInResult() {
        AuditLog auditLog = new AuditLog();
        auditLog.setAction(Action.CREATED);
        auditLog.setType(String.class);
        auditLog.setIdentifier(3);
        auditLog.setUuid("check-fields-uuid");

        Mockito.when(auditLogService.getObjectByUuid(AuditLog.class, "check-fields-uuid")).thenReturn(auditLog);
        Mockito.when(auditLogService.getObjectById(String.class, 3)).thenReturn(null);

        AuditLogDetails result = dwrService.getAuditLogDetails("check-fields-uuid");

        assertNotNull(result);
        assertEquals("check-fields-uuid", result.getUuid());
        assertEquals("CREATED", result.getAction());
        assertEquals(Integer.valueOf(3), result.getIdentifier());
    }
}