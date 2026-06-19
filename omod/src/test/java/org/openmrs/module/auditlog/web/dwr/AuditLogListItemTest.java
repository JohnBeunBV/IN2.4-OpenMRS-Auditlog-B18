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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.openmrs.PersonName;
import org.openmrs.User;
import org.openmrs.api.context.Context;
import org.openmrs.messagesource.MessageSourceService;
import org.openmrs.module.auditlog.AuditLog;
import org.openmrs.module.auditlog.AuditLog.Action;

/**
 * Unit tests voor {@link AuditLogListItem}.
 *
 * AuditLogListItem.<init> bevat de meeste branch-logica (type-naam
 * resolutie, daemon-vs-reguliere gebruiker opmaak, actie-mapping), maar twee
 * branches roepen de statische OpenMRS {@link Context} facade aan
 * (getMessageSourceService(), getDateFormat()). Die statische aanroepen worden
 * gestubbed met Mockito's mockStatic via anonieme klassen (Java 1.7-compatibel,
 * geen lambdas of method references).
 *
 * De drie tests die eerder een NPE gooiden via User.addName() zijn herschreven
 * met Mockito-mocks voor User en PersonName.
 */
public class AuditLogListItemTest {

    private static final String DAEMON_USER_UUID = "A4F30A1B-5EB9-11DF-A648-37A07F9C90FB";

    private MockedStatic<Context> mockedContext;

    @Before
    public void setUp() {
        mockedContext = Mockito.mockStatic(Context.class);
    }

    @After
    public void tearDown() {
        mockedContext.close();
    }

    // =========================================================================
    // Constructor: null-safety
    // =========================================================================

    @Test
    public void constructor_shouldNotFailForNullAuditLog() {
        AuditLogListItem item = new AuditLogListItem(null);

        assertNull("Alle velden moeten null/default blijven bij null-invoer", item.getAuditLogId());
        assertNull(item.getClassname());
        assertNull(item.getAction());
    }

    // =========================================================================
    // setTypeDetails() — classname / simpleClassname resolutie
    // =========================================================================

    @Test
    public void constructor_shouldSetClassnameAndSimpleClassnameForRegularType() {
        AuditLog auditLog = new AuditLog();
        auditLog.setType(String.class);

        AuditLogListItem item = new AuditLogListItem(auditLog);

        assertEquals("java.lang.String", item.getClassname());
        assertEquals("String", item.getSimpleClassname());
    }

    @Test
    public void constructor_shouldStripNestedClassPrefixFromSimpleClassname() {
        AuditLog auditLog = new AuditLog();
        auditLog.setType(Map.Entry.class);

        AuditLogListItem item = new AuditLogListItem(auditLog);

        assertEquals("Map.Entry geeft volledige naam met '$'", "java.util.Map$Entry", item.getClassname());
        assertEquals("Geneste klasse: alleen het deel na '$' wordt gebruikt", "Entry", item.getSimpleClassname());
    }

    @Test
    public void constructor_shouldLeaveClassnameNullWhenTypeIsNull() {
        AuditLog auditLog = new AuditLog();
        auditLog.setType(null);

        AuditLogListItem item = new AuditLogListItem(auditLog);

        assertNull("Bij ontbrekend type mag classname niet gezet worden", item.getClassname());
        assertNull(item.getSimpleClassname());
    }

    // =========================================================================
    // setActionDetails()
    // =========================================================================

    @Test
    public void constructor_shouldSetActionStringFromEnum() {
        AuditLog auditLog = new AuditLog();
        auditLog.setAction(Action.CREATED);

        AuditLogListItem item = new AuditLogListItem(auditLog);

        assertEquals("CREATED", item.getAction());
    }

    @Test
    public void constructor_shouldLeaveActionNullWhenActionIsNull() {
        AuditLog auditLog = new AuditLog();
        auditLog.setAction(null);

        AuditLogListItem item = new AuditLogListItem(auditLog);

        assertNull(item.getAction());
    }

    // =========================================================================
    // setUserDetailsInternal() — null user (geen Context-aanroep verwacht)
    // =========================================================================

    @Test
    public void constructor_shouldLeaveUserDetailsBlankWhenUserIsNull() {
        AuditLog auditLog = new AuditLog();
        auditLog.setUser(null);

        AuditLogListItem item = new AuditLogListItem(auditLog);

        assertEquals("", item.getUserDetails());
        mockedContext.verifyNoInteractions();
    }

    // =========================================================================
    // setUserDetailsInternal() — daemon user branch
    // =========================================================================

    @Test
    public void constructor_shouldUseSystemChangeMessageForDaemonUser() {
        User daemonUser = new User();
        daemonUser.setUuid(DAEMON_USER_UUID);

        AuditLog auditLog = new AuditLog();
        auditLog.setUser(daemonUser);

        final MessageSourceService messageSourceService = Mockito.mock(MessageSourceService.class);
        Mockito.when(messageSourceService.getMessage("auditlog.systemChange")).thenReturn("System change");
        mockedContext.when(new MockedStatic.Verification() {
            public void apply() throws Throwable {
                Context.getMessageSourceService();
            }
        }).thenReturn(messageSourceService);

        AuditLogListItem item = new AuditLogListItem(auditLog);

        assertEquals("Daemon-gebruiker moet de vertaalde systeemmelding tonen", "System change", item.getUserDetails());
    }

    // =========================================================================
    // setUserDetailsInternal() — reguliere gebruiker, naam/username combinaties
    //
    // FIX: User.addName(PersonName) gooit een NullPointerException buiten een
    // Hibernate/Spring-context. Oplossing: mock User en PersonName zodat we
    // uitsluitend het gedrag van AuditLogListItem testen.
    // =========================================================================

    @Test
    public void constructor_shouldNotTreatRegularUuidAsDaemonUser() {
        PersonName mockName = Mockito.mock(PersonName.class);
        Mockito.when(mockName.getFullName()).thenReturn("Jane Doe");

        User regularUser = Mockito.mock(User.class);
        Mockito.when(regularUser.getUuid()).thenReturn("not-the-daemon-uuid");
        Mockito.when(regularUser.getPersonName()).thenReturn(mockName);
        Mockito.when(regularUser.getUsername()).thenReturn(null);

        AuditLog auditLog = new AuditLog();
        auditLog.setUser(regularUser);

        AuditLogListItem item = new AuditLogListItem(auditLog);

        assertTrue("Niet-daemon gebruiker moet de eigen naam tonen, niet de systeemmelding",
                item.getUserDetails().contains("Jane"));
        mockedContext.verify(new MockedStatic.Verification() {
            public void apply() throws Throwable {
                Context.getMessageSourceService();
            }
        }, Mockito.never());
    }

    @Test
    public void constructor_shouldNotFailWhenDaemonUserUuidIsNull() {
        User userWithNullUuid = new User();
        userWithNullUuid.setUuid(null);

        AuditLog auditLog = new AuditLog();
        auditLog.setUser(userWithNullUuid);

        AuditLogListItem item = new AuditLogListItem(auditLog);

        assertEquals("", item.getUserDetails());
    }

    @Test
    public void constructor_shouldSetFullNameForRegularUserWithoutUsername() {
        PersonName mockName = Mockito.mock(PersonName.class);
        Mockito.when(mockName.getFullName()).thenReturn("John Smith");

        User user = Mockito.mock(User.class);
        Mockito.when(user.getUuid()).thenReturn("regular-uuid");
        Mockito.when(user.getPersonName()).thenReturn(mockName);
        Mockito.when(user.getUsername()).thenReturn(null);

        AuditLog auditLog = new AuditLog();
        auditLog.setUser(user);

        AuditLogListItem item = new AuditLogListItem(auditLog);

        assertEquals("John Smith", item.getUserDetails());
    }

    @Test
    public void constructor_shouldAppendUsernameInBracketsWhenPresent() {
        PersonName mockName = Mockito.mock(PersonName.class);
        Mockito.when(mockName.getFullName()).thenReturn("John Smith");

        User user = Mockito.mock(User.class);
        Mockito.when(user.getUuid()).thenReturn("regular-uuid");
        Mockito.when(user.getPersonName()).thenReturn(mockName);
        Mockito.when(user.getUsername()).thenReturn("jsmith");

        AuditLog auditLog = new AuditLog();
        auditLog.setUser(user);

        AuditLogListItem item = new AuditLogListItem(auditLog);

        assertEquals("John Smith[jsmith]", item.getUserDetails());
    }

    @Test
    public void constructor_shouldHandleUserWithUsernameButNoPersonName() {
        User user = new User();
        user.setUuid("regular-uuid");
        user.setUsername("noname");

        AuditLog auditLog = new AuditLog();
        auditLog.setUser(user);

        AuditLogListItem item = new AuditLogListItem(auditLog);

        assertEquals("[noname]", item.getUserDetails());
    }

    @Test
    public void constructor_shouldLeaveUserDetailsBlankWhenNoNameAndNoUsername() {
        User user = new User();
        user.setUuid("regular-uuid");

        AuditLog auditLog = new AuditLog();
        auditLog.setUser(user);

        AuditLogListItem item = new AuditLogListItem(auditLog);

        assertEquals("", item.getUserDetails());
    }

    // =========================================================================
    // setDateCreatedDetails()
    // =========================================================================

    @Test
    public void constructor_shouldFormatDateCreatedUsingContextDateFormat() {
        final Date date = new Date(0L);
        final SimpleDateFormat fixedFormat = new SimpleDateFormat("dd/MM/yyyy");
        mockedContext.when(new MockedStatic.Verification() {
            public void apply() throws Throwable {
                Context.getDateFormat();
            }
        }).thenReturn(fixedFormat);

        AuditLog auditLog = new AuditLog();
        auditLog.setDateCreated(date);

        AuditLogListItem item = new AuditLogListItem(auditLog);

        assertEquals(fixedFormat.format(date), item.getDateCreatedString());
    }

    @Test
    public void constructor_shouldLeaveDateCreatedStringNullWhenDateCreatedIsNull() {
        AuditLog auditLog = new AuditLog();
        auditLog.setDateCreated(null);

        AuditLogListItem item = new AuditLogListItem(auditLog);

        assertNull("Bij ontbrekende datum mag dateCreatedString niet gezet worden", item.getDateCreatedString());
        mockedContext.verify(new MockedStatic.Verification() {
            public void apply() throws Throwable {
                Context.getDateFormat();
            }
        }, Mockito.never());
    }

    // =========================================================================
    // Getters/setters — directe manipulatie buiten het constructor-pad
    // =========================================================================

    @Test
    public void shouldAllowDirectFieldManipulationViaSettersAndGetters() {
        AuditLogListItem item = new AuditLogListItem(null);

        item.setAuditLogId(10);
        item.setClassname("TestClass");
        item.setSimpleClassname("SimpleClass");
        item.setIdentifier("123");
        item.setAction("CREATED");
        item.setUserDetails("admin");
        item.setDateCreatedString("2024-01-01");

        assertEquals(Integer.valueOf(10), item.getAuditLogId());
        assertEquals("TestClass", item.getClassname());
        assertEquals("SimpleClass", item.getSimpleClassname());
        assertEquals("123", item.getIdentifier());
        assertEquals("CREATED", item.getAction());
        assertEquals("admin", item.getUserDetails());
        assertEquals("2024-01-01", item.getDateCreatedString());
    }
}