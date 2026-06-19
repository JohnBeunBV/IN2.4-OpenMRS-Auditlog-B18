package org.openmrs.module.auditlog.web.dwr;

import static org.junit.Assert.*;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

public class AuditLogDetailsTest {

    @Test
    public void shouldGetAndSetValues() {

        Map<String, Object> changes = new HashMap<String, Object>();

        AuditLogDetails details = new AuditLogDetails(
                "display",
                Integer.valueOf(1),
                "Patient",
                "UPDATED",
                "uuid",
                "2.6.0",
                true,
                changes
        );

        assertEquals("display", details.getDisplayString());
        assertEquals(Integer.valueOf(1), details.getIdentifier());
        assertEquals("Patient", details.getSimpleTypeName());
        assertEquals("UPDATED", details.getAction());
        assertEquals("uuid", details.getUuid());
        assertEquals("2.6.0", details.getOpenmrsVersion());
        assertTrue(details.isObjectExists());
        assertEquals(changes, details.getChanges());

        details.setDisplayString("new");
        details.setObjectExists(false);

        Serializable id = Integer.valueOf(5);
        details.setIdentifier(id);

        details.setSimpleTypeName("Obs");
        details.setAction("CREATED");
        details.setUuid("newuuid");
        details.setOpenmrsVersion("3.0");
        details.setChanges(new HashMap<String, Object>());

        List<AuditLogDetails> childList = new ArrayList<AuditLogDetails>();
        details.setChildAuditLogDetails(childList);

        assertEquals("new", details.getDisplayString());
        assertFalse(details.isObjectExists());
        assertEquals(id, details.getIdentifier());
        assertEquals("Obs", details.getSimpleTypeName());
        assertEquals("CREATED", details.getAction());
        assertEquals("newuuid", details.getUuid());
        assertEquals("3.0", details.getOpenmrsVersion());
        assertNotNull(details.getChanges());
        assertEquals(childList, details.getChildAuditLogDetails());
    }
}