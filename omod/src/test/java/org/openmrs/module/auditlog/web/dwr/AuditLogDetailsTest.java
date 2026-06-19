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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

/**
 * Unit tests for {@link AuditLogDetails}.
 *
 * AuditLogDetails is a pure DTO with no branch logic; the tests verify the
 * convenience constructor wires every field correctly and that each
 * setter/getter pair round-trips its value, including null-handling for
 * the optional changes/child-details fields.
 */
public class AuditLogDetailsTest {

    @Test
    public void constructor_shouldWireAllFieldsFromArguments() {
        Map<String, Object> changes = new HashMap<String, Object>();
        changes.put("conceptClass", new String[] { "2", "1" });

        AuditLogDetails details = new AuditLogDetails(
                "display", Integer.valueOf(1), "Patient", "UPDATED", "uuid", "2.6.0", true, changes);

        assertEquals("display", details.getDisplayString());
        assertEquals(Integer.valueOf(1), details.getIdentifier());
        assertEquals("Patient", details.getSimpleTypeName());
        assertEquals("UPDATED", details.getAction());
        assertEquals("uuid", details.getUuid());
        assertEquals("2.6.0", details.getOpenmrsVersion());
        assertTrue(details.isObjectExists());
        assertEquals(changes, details.getChanges());
    }

    @Test
    public void constructor_shouldAllowNullChanges() {
        // DWRAuditLogService geeft null door voor child-logs (zie getAuditLogDetails)
        AuditLogDetails details = new AuditLogDetails(
                null, Integer.valueOf(2), "Obs", "CREATED", "child-uuid", "2.6.0", false, null);

        assertNull(details.getChanges());
        assertFalse(details.isObjectExists());
    }

    @Test
    public void displayStringSetterAndGetter_shouldRoundTrip() {
        AuditLogDetails details = newMinimalDetails();

        details.setDisplayString("new value");

        assertEquals("new value", details.getDisplayString());
    }

    @Test
    public void objectExistsSetterAndGetter_shouldRoundTrip() {
        AuditLogDetails details = newMinimalDetails();

        details.setObjectExists(true);
        assertTrue(details.isObjectExists());

        details.setObjectExists(false);
        assertFalse(details.isObjectExists());
    }

    @Test
    public void identifierSetterAndGetter_shouldRoundTrip() {
        AuditLogDetails details = newMinimalDetails();
        Serializable newId = Integer.valueOf(42);

        details.setIdentifier(newId);

        assertEquals(newId, details.getIdentifier());
    }

    @Test
    public void simpleTypeNameSetterAndGetter_shouldRoundTrip() {
        AuditLogDetails details = newMinimalDetails();

        details.setSimpleTypeName("Encounter");

        assertEquals("Encounter", details.getSimpleTypeName());
    }

    @Test
    public void actionSetterAndGetter_shouldRoundTrip() {
        AuditLogDetails details = newMinimalDetails();

        details.setAction("DELETED");

        assertEquals("DELETED", details.getAction());
    }

    @Test
    public void uuidSetterAndGetter_shouldRoundTrip() {
        AuditLogDetails details = newMinimalDetails();

        details.setUuid("another-uuid");

        assertEquals("another-uuid", details.getUuid());
    }

    @Test
    public void openmrsVersionSetterAndGetter_shouldRoundTrip() {
        AuditLogDetails details = newMinimalDetails();

        details.setOpenmrsVersion("3.0.0");

        assertEquals("3.0.0", details.getOpenmrsVersion());
    }

    @Test
    public void changesSetterAndGetter_shouldRoundTrip() {
        AuditLogDetails details = newMinimalDetails();
        Map<String, Object> newChanges = new HashMap<String, Object>();
        newChanges.put("version", new String[] { "1.11", "1.10" });

        details.setChanges(newChanges);

        assertEquals(newChanges, details.getChanges());
    }

    @Test
    public void childAuditLogDetailsSetterAndGetter_shouldRoundTrip() {
        AuditLogDetails details = newMinimalDetails();
        List<AuditLogDetails> children = new ArrayList<AuditLogDetails>();
        children.add(newMinimalDetails());

        details.setChildAuditLogDetails(children);

        assertEquals(children, details.getChildAuditLogDetails());
    }

    @Test
    public void childAuditLogDetails_shouldBeNullByDefault() {
        // Bevestigt dat een AuditLogDetails zonder kinderen (bv. bij DELETE-acties
        // zonder child logs)
        // geen lege lijst maar null teruggeeft, zoals DWRAuditLogService verwacht via
        // hasChildLogs()
        AuditLogDetails details = newMinimalDetails();

        assertNull(details.getChildAuditLogDetails());
    }

    private AuditLogDetails newMinimalDetails() {
        return new AuditLogDetails("display", Integer.valueOf(1), "Patient", "UPDATED", "uuid", "2.6.0", true,
                new HashMap<String, Object>());
    }
}