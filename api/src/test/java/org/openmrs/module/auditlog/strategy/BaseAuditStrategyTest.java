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
package org.openmrs.module.auditlog.strategy;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * UNIT TEST (no Spring/Hibernate context, no BaseModuleContextSensitiveTest).
 * <p>
 * {@link BaseAuditStrategy#equals(Object)} and
 * {@link BaseAuditStrategy#hashCode()} base equality
 * purely on {@code getClass()}, with no dependency on the OpenMRS
 * {@code Context} or the
 * {@code AuditLogHelper} lookup that some subclasses (like
 * {@link AllAuditStrategy}) otherwise
 * need. {@link AllAuditStrategy} and {@link NoneAuditStrategy} are used here
 * purely as two
 * concrete, no-arg-constructible subclasses to exercise the shared
 * equals/hashCode contract
 * directly - the strategy-specific {@code isAudited()} behaviour itself is
 * already covered by
 * the existing {@code AllAuditStrategyTest}/{@code NoneAuditStrategyTest}
 * integration tests.
 */
public class BaseAuditStrategyTest {

    /**
     * @see BaseAuditStrategy#equals(Object)
     */
    @Test
    public void equals_shouldReturnTrueForTwoInstancesOfTheSameStrategyClass() throws Exception {
        assertTrue(new AllAuditStrategy().equals(new AllAuditStrategy()));
    }

    /**
     * @see BaseAuditStrategy#equals(Object)
     */
    @Test
    public void equals_shouldReturnFalseForInstancesOfDifferentStrategyClasses() throws Exception {
        assertFalse(new AllAuditStrategy().equals(new NoneAuditStrategy()));
    }

    /**
     * @see BaseAuditStrategy#equals(Object)
     */
    @Test
    public void equals_shouldReturnFalseWhenComparedToNull() throws Exception {
        assertFalse(new AllAuditStrategy().equals(null));
    }

    /**
     * @see BaseAuditStrategy#equals(Object)
     */
    @Test
    public void equals_shouldReturnTrueWhenComparedToItself() throws Exception {
        AllAuditStrategy strategy = new AllAuditStrategy();
        assertTrue(strategy.equals(strategy));
    }

    /**
     * @see BaseAuditStrategy#hashCode()
     */
    @Test
    public void hashCode_shouldBeBasedOnTheConcreteClass() throws Exception {
        assertEquals(new AllAuditStrategy().hashCode(), new AllAuditStrategy().hashCode());
        assertEquals(AllAuditStrategy.class.hashCode(), new AllAuditStrategy().hashCode());
    }

    /**
     * @see BaseAuditStrategy#hashCode()
     */
    @Test
    public void hashCode_shouldDifferBetweenDifferentStrategyClasses() throws Exception {
        assertFalse(new AllAuditStrategy().hashCode() == new NoneAuditStrategy().hashCode());
    }
}