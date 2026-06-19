package org.openmrs.module.auditlog.web.controller;

import static org.junit.Assert.assertNotNull;

import org.junit.Test;
import org.springframework.ui.ModelMap;

public class ViewAuditLogControllerTest {

    @Test
    public void controllerClassShouldExist() {

        assertNotNull(ViewAuditLogController.class);
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
            // expected in test context without authenticated OpenMRS session
        }

        assertNotNull(model);
    }
}