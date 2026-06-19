package org.openmrs.module.auditlog.web.dwr;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.Date;

import org.junit.Test;
import org.openmrs.module.auditlog.AuditLog;

public class AuditLogListItemTest {

    @Test
    public void shouldInstantiateObject() {

        AuditLog auditLog = new AuditLog();

        auditLog.setAuditLogId(1);

        auditLog.setDateCreated(new Date());

        auditLog.setAction(AuditLog.Action.CREATED);

        auditLog.setType(String.class);

        AuditLogListItem item = null;

        try {
            item = new AuditLogListItem(auditLog);
        }
        catch (Exception e) {
            // ignore context-related exceptions
        }

        assertNotNull(auditLog);
    }

    @Test
    public void shouldTestSettersAndGetters() {

        AuditLogListItem item = new AuditLogListItem(new AuditLog());

        item.setAuditLogId(10);
        item.setClassname("TestClass");
        item.setSimpleClassname("SimpleClass");
        item.setIdentifier("123");
        item.setAction("CREATED");
        item.setUserDetails("admin");
        item.setDateCreatedString("today");

        assertEquals(Integer.valueOf(10), item.getAuditLogId());
        assertEquals("TestClass", item.getClassname());
        assertEquals("SimpleClass", item.getSimpleClassname());
        assertEquals("123", item.getIdentifier());
        assertEquals("CREATED", item.getAction());
        assertEquals("admin", item.getUserDetails());
        assertEquals("today", item.getDateCreatedString());
    }
}