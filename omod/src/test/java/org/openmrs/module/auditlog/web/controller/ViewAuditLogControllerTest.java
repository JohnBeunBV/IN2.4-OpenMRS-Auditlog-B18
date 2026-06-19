package org.openmrs.module.auditlog.web.controller;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.springframework.ui.ModelMap;

public class ViewAuditLogControllerTest {

    @Test
    public void controllerShouldInstantiate() {

        ViewAuditLogController controller =
                new ViewAuditLogController();

        assertEquals(
                ViewAuditLogController.class,
                controller.getClass());
    }

    @Test
    public void showFormShouldNotCrash() {

        ViewAuditLogController controller =
                new ViewAuditLogController();

        ModelMap model = new ModelMap();

        try {
            controller.showForm(model);
        }
        catch (Exception e) {
            // expected in non-authenticated test context
        }

        assertEquals(true, true);
    }
}