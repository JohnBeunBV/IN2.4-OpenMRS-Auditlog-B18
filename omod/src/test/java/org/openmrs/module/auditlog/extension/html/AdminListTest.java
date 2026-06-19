package org.openmrs.module.auditlog.extension.html;

import static org.junit.Assert.*;

import java.util.Map;

import org.junit.Test;
import org.openmrs.module.Extension;
import org.openmrs.module.auditlog.util.AuditLogConstants;

public class AdminListTest {

    @Test
    public void shouldReturnHtmlMediaType() {
        AdminList adminList = new AdminList();

        assertEquals(Extension.MEDIA_TYPE.html, adminList.getMediaType());
    }

    @Test
    public void shouldReturnCorrectTitle() {
        AdminList adminList = new AdminList();

        assertEquals(AuditLogConstants.MODULE_ID + ".title", adminList.getTitle());
    }

    @Test
    public void shouldReturnLinks() {
        AdminList adminList = new AdminList();

        Map<String, String> links = adminList.getLinks();

        assertEquals(1, links.size());

        assertEquals(
                AuditLogConstants.MODULE_ID + ".viewAuditLog",
                links.get("module/" + AuditLogConstants.MODULE_ID + "/viewAuditLog.htm")
        );
    }
}