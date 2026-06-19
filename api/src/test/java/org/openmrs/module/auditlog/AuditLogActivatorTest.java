package org.openmrs.module.auditlog;

import org.junit.Test;

public class AuditLogActivatorTest {

    @Test
    public void shouldRunLifecycleMethodsWithoutErrors() {
        AuditLogActivator activator = new AuditLogActivator();

        activator.willStart();
        activator.started();
        activator.willStop();
        activator.stopped();
    }
}