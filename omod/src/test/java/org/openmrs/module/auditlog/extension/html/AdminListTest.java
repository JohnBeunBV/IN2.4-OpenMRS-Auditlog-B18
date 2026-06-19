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
package org.openmrs.module.auditlog.extension.html;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Map;

import org.junit.Test;
import org.openmrs.module.Extension;
import org.openmrs.module.auditlog.util.AuditLogConstants;

/**
 * Unit tests for {@link AdminList}.
 *
 * AdminList is a configuration-only extension point with no branch logic;
 * the tests verify the exact, contractual values it must return for the
 * OpenMRS admin-page extension framework to wire it up correctly.
 */
public class AdminListTest {

    private final AdminList adminList = new AdminList();

    @Test
    public void getMediaType_shouldReturnHtml() {
        assertEquals(Extension.MEDIA_TYPE.html, adminList.getMediaType());
    }

    @Test
    public void getTitle_shouldReturnModuleTitleKey() {
        assertEquals(AuditLogConstants.MODULE_ID + ".title", adminList.getTitle());
    }

    @Test
    public void getLinks_shouldContainExactlyOneEntry() {
        Map<String, String> links = adminList.getLinks();

        assertEquals(1, links.size());
    }

    @Test
    public void getLinks_shouldMapViewAuditLogUrlToItsMessageKey() {
        Map<String, String> links = adminList.getLinks();

        String expectedUrl = "module/" + AuditLogConstants.MODULE_ID + "/viewAuditLog.htm";
        String expectedKey = AuditLogConstants.MODULE_ID + ".viewAuditLog";

        assertTrue("De viewAuditLog-url moet als sleutel aanwezig zijn", links.containsKey(expectedUrl));
        assertEquals(expectedKey, links.get(expectedUrl));
    }

    @Test
    public void getLinks_shouldReturnANewMapOnEachCall() {
        // Voorkomt per ongeluk gedeelde, muteerbare state tussen meerdere
        // admin-pagina-renders
        Map<String, String> firstCall = adminList.getLinks();
        Map<String, String> secondCall = adminList.getLinks();

        firstCall.put("extra", "value");

        assertEquals("Tweede aanroep mag niet beïnvloed zijn door mutatie van het eerste resultaat",
                1, secondCall.size());
    }
}