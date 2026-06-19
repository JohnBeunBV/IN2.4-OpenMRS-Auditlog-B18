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
package org.openmrs.module.auditlog.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.sql.Blob;
import java.util.List;
import java.util.Map;

import org.hibernate.Hibernate;
import org.junit.Test;
import org.openmrs.api.APIException;
import org.openmrs.module.auditlog.AuditLog;
import org.openmrs.module.auditlog.AuditLog.Action;

/**
 * Unit tests for the deserialization helpers that remained in
 * {@link AuditLogUtil} after
 * the Extract Class refactoring ({@code getChangesOfUpdatedItem},
 * {@code getLastStateOfDeletedItem}). These methods only read a {@link Blob}
 * and parse
 * JSON; they have no Hibernate-session or Spring-context dependency, so they
 * are tested
 * here as plain JUnit tests, without {@code BaseModuleContextSensitiveTest}.
 *
 * <p>
 * {@link Hibernate#createBlob(byte[])} is used to build test input, matching
 * how
 * {@code HibernateAuditLogInterceptor} itself constructs the blob in production
 * code.
 * </p>
 */
public class AuditLogUtilDeserializationTest {

    @Test
    @SuppressWarnings("rawtypes")
    public void getChangesOfUpdatedItem_shouldReturnEmptyMapWhenSerializedDataIsNull() throws Exception {
        AuditLog auditLog = new AuditLog();
        auditLog.setAction(Action.UPDATED);
        auditLog.setSerializedData(null);

        Map<String, List> changes = AuditLogUtil.getChangesOfUpdatedItem(auditLog);

        assertNotNull(changes);
        assertTrue(changes.isEmpty());
    }

    @Test
    @SuppressWarnings("rawtypes")
    public void getChangesOfUpdatedItem_shouldParseSerializedJsonIntoAMapOfChanges() throws Exception {
        String json = "{\"name\":[\"new value\",\"old value\"]}";
        Blob blob = Hibernate.createBlob(json.getBytes());

        AuditLog auditLog = new AuditLog();
        auditLog.setAction(Action.UPDATED);
        auditLog.setSerializedData(blob);

        Map<String, List> changes = AuditLogUtil.getChangesOfUpdatedItem(auditLog);

        assertEquals(1, changes.size());
        List nameChange = changes.get("name");
        assertEquals("new value", nameChange.get(0));
        assertEquals("old value", nameChange.get(1));
    }

    @Test
    public void getChangesOfUpdatedItem_shouldFailForAnAuditLogWithAnActionOtherThanUpdated() throws Exception {
        AuditLog auditLog = new AuditLog();
        auditLog.setAction(Action.CREATED);

        try {
            AuditLogUtil.getChangesOfUpdatedItem(auditLog);
            fail("Should have thrown an APIException for a non-UPDATED action");
        } catch (APIException e) {
            // expected
        }
    }

    @Test
    public void getLastStateOfDeletedItem_shouldReturnEmptyMapWhenSerializedDataIsNull() throws Exception {
        AuditLog auditLog = new AuditLog();
        auditLog.setAction(Action.DELETED);
        auditLog.setSerializedData(null);

        Map<String, String> lastState = AuditLogUtil.getLastStateOfDeletedItem(auditLog);

        assertNotNull(lastState);
        assertTrue(lastState.isEmpty());
    }

    @Test
    public void getLastStateOfDeletedItem_shouldParseSerializedJsonIntoAMapOfPropertyValues() throws Exception {
        String json = "{\"name\":\"John\",\"identifier\":\"42\"}";
        Blob blob = Hibernate.createBlob(json.getBytes());

        AuditLog auditLog = new AuditLog();
        auditLog.setAction(Action.DELETED);
        auditLog.setSerializedData(blob);

        Map<String, String> lastState = AuditLogUtil.getLastStateOfDeletedItem(auditLog);

        assertEquals(2, lastState.size());
        assertEquals("John", lastState.get("name"));
        assertEquals("42", lastState.get("identifier"));
    }

    @Test
    public void getLastStateOfDeletedItem_shouldFailForAnAuditLogWithAnActionOtherThanDeleted() throws Exception {
        AuditLog auditLog = new AuditLog();
        auditLog.setAction(Action.UPDATED);

        try {
            AuditLogUtil.getLastStateOfDeletedItem(auditLog);
            fail("Should have thrown an APIException for a non-DELETED action");
        } catch (APIException e) {
            // expected
        }
    }
}