/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.auditlog.strategy;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;

import org.junit.Before;
import org.junit.Test;
import org.openmrs.Patient;
import org.openmrs.Person;
import org.openmrs.api.context.Context;
import org.openmrs.module.auditlog.AuditLogHelper;
import org.openmrs.module.auditlog.util.AuditLogConstants;
import org.openmrs.module.auditlog.util.AuditLogUtil;
import org.openmrs.test.BaseModuleContextSensitiveTest;

/**
 * Tests for SEC-03 (CWE-778): Default auditing strategy must be ALL, not NONE.
 *
 * <p>Regression guard: if the global property {@code auditlog.auditingStrategy} is absent
 * (fresh install / property deleted), the module must still log patient mutations.
 * Before the fix, the fallback was {@link AuditStrategy#NONE}, meaning nothing was
 * ever logged until an administrator explicitly configured the strategy — a silent
 * security gap that violated NEN-7510 requirements.
 */
public class DefaultAuditingStrategyTest extends BaseModuleContextSensitiveTest {

    private AuditLogHelper helper;

    @Before
    public void setUp() {
        helper = Context.getRegisteredComponents(AuditLogHelper.class).get(0);
        // Remove the global property to simulate a fresh install with no strategy configured
        AuditLogUtil.setGlobalProperty(AuditLogConstants.GP_AUDITING_STRATEGY, "");
    }

    /**
     * SEC-03 regression test.
     *
     * <p>When no auditing strategy is configured (blank / missing global property), the
     * module must default to {@link AuditStrategy#ALL} so that patient mutations are
     * logged immediately after a fresh install without any manual intervention.
     *
     * @verifies return ALL strategy when global property is blank
     * @see AuditLogHelper#getAuditingStrategy()
     */
    @Test
    public void getAuditingStrategy_shouldDefaultToAllWhenGlobalPropertyIsBlank() {
        AuditStrategy strategy = helper.getAuditingStrategy();

        assertEquals(
            "SEC-03: Default strategy must be ALL, not NONE (CWE-778 — insufficient logging)",
            AuditStrategy.ALL,
            strategy
        );
    }

    /**
     * SEC-03 regression test.
     *
     * <p>With the default (ALL) strategy, Patient and Person must be considered audited
     * even before an administrator has touched the settings. This mirrors the
     * NEN-7510 requirement that patient-record mutations are always traceable.
     *
     * @verifies audit Patient class by default
     * @see AuditLogHelper#isAudited(Class)
     */
    @Test
    public void isAudited_shouldAuditPatientByDefault() {
        // Patient mutations must be logged from the very first install
        boolean patientAudited = helper.isAudited(Patient.class);

        assertEquals(
            "SEC-03: Patient mutations must be logged with the default strategy (ALL)",
            true,
            patientAudited
        );
    }

    /**
     * SEC-03 regression test.
     *
     * <p>Verifies the same guarantee for {@link Person}, whose records are closely
     * linked to patient identity and are equally sensitive.
     *
     * @verifies audit Person class by default
     * @see AuditLogHelper#isAudited(Class)
     */
    @Test
    public void isAudited_shouldAuditPersonByDefault() {
        boolean personAudited = helper.isAudited(Person.class);

        assertEquals(
            "SEC-03: Person mutations must be logged with the default strategy (ALL)",
            true,
            personAudited
        );
    }

    /**
     * Sanity-check: explicitly setting NONE must override the default and produce
     * zero logging — confirming the default is not hard-coded but still configurable.
     *
     * @verifies respect explicit NONE setting after default is active
     * @see AuditLogHelper#getAuditingStrategy()
     */
    @Test
    public void getAuditingStrategy_shouldRespectExplicitNoneSetting() {
        // First confirm the default is ALL
        assertEquals(AuditStrategy.ALL, helper.getAuditingStrategy());

        // Now explicitly set NONE
        AuditLogUtil.setGlobalProperty(
            AuditLogConstants.GP_AUDITING_STRATEGY,
            AuditStrategy.SHORT_NAME_NONE
        );

        AuditStrategy strategy = helper.getAuditingStrategy();
        assertEquals(
            "After explicitly setting NONE, strategy must be NONE",
            AuditStrategy.NONE,
            strategy
        );
        assertFalse(
            "With NONE strategy, Patient must not be audited",
            helper.isAudited(Patient.class)
        );
    }
}
