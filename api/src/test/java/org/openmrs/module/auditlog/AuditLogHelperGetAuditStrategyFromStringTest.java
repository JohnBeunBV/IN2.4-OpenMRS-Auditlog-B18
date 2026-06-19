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
package org.openmrs.module.auditlog;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.openmrs.module.auditlog.strategy.AllAuditStrategy;
import org.openmrs.module.auditlog.strategy.AllExceptAuditStrategy;
import org.openmrs.module.auditlog.strategy.AuditStrategy;
import org.openmrs.module.auditlog.strategy.BaseAuditStrategy;
import org.openmrs.module.auditlog.strategy.NoneAuditStrategy;
import org.openmrs.module.auditlog.strategy.NoneExceptAuditStrategy;

/**
 * Unit tests for {@link AuditLogHelper#getAuditStrategyFromString(String)}, one
 * of the
 * two methods simplified into a lookup table by the opzoektabel refactoring
 * described
 * in the maintainability report.
 *
 * <p>
 * The method was changed from {@code private} to package-private specifically
 * to
 * make this possible: {@code new AuditLogHelper()} requires no Spring context
 * (the
 * class has no non-static fields needing injection, and {@code @Component} is
 * inert
 * metadata until Spring's component scan picks it up), and the method itself
 * only
 * touches the two static lookup tables ({@code SHORT_NAME_STRATEGIES},
 * {@code KNOWN_STRATEGY_CLASSES}) plus, for unknown class names,
 * {@code Context.loadClass()} - which is exercised only by the (out-of-scope)
 * custom-
 * strategy fallback, not by any of the branches tested here.
 * </p>
 *
 * <p>
 * This replaces the indirect, context-dependent approach previously needed via
 * {@code getAuditingStrategy()} and {@code BaseAuditLogTest}.
 * </p>
 */
public class AuditLogHelperGetAuditStrategyFromStringTest {

    private AuditLogHelper helper = new AuditLogHelper();

    // -----------------------------------------------------------------------
    // Blank/default value
    // -----------------------------------------------------------------------

    @Test
    public void getAuditStrategyFromString_shouldDefaultToAllForANullValue() throws Exception {
        assertEquals(AuditStrategy.ALL, helper.getAuditStrategyFromString(null));
    }

    @Test
    public void getAuditStrategyFromString_shouldDefaultToAllForABlankValue() throws Exception {
        assertEquals(AuditStrategy.ALL, helper.getAuditStrategyFromString(""));
    }

    // -----------------------------------------------------------------------
    // Short names (case-insensitive)
    // -----------------------------------------------------------------------

    @Test
    public void getAuditStrategyFromString_shouldResolveTheShortNameNone() throws Exception {
        assertEquals(AuditStrategy.NONE, helper.getAuditStrategyFromString(AuditStrategy.SHORT_NAME_NONE));
    }

    @Test
    public void getAuditStrategyFromString_shouldResolveTheShortNameNoneCaseInsensitively() throws Exception {
        assertEquals(AuditStrategy.NONE,
                helper.getAuditStrategyFromString(AuditStrategy.SHORT_NAME_NONE.toUpperCase()));
    }

    @Test
    public void getAuditStrategyFromString_shouldResolveTheShortNameNoneExcept() throws Exception {
        assertEquals(AuditStrategy.NONE_EXCEPT,
                helper.getAuditStrategyFromString(AuditStrategy.SHORT_NAME_NONE_EXCEPT));
    }

    @Test
    public void getAuditStrategyFromString_shouldResolveTheShortNameAll() throws Exception {
        assertEquals(AuditStrategy.ALL, helper.getAuditStrategyFromString(AuditStrategy.SHORT_NAME_ALL));
    }

    @Test
    public void getAuditStrategyFromString_shouldResolveTheShortNameAllExcept() throws Exception {
        assertEquals(AuditStrategy.ALL_EXCEPT,
                helper.getAuditStrategyFromString(AuditStrategy.SHORT_NAME_ALL_EXCEPT));
    }

    // -----------------------------------------------------------------------
    // Fully qualified class names of the built-in strategies
    // -----------------------------------------------------------------------

    @Test
    public void getAuditStrategyFromString_shouldResolveTheFullyQualifiedNoneAuditStrategyClassName() throws Exception {
        assertEquals(AuditStrategy.NONE, helper.getAuditStrategyFromString(NoneAuditStrategy.class.getName()));
    }

    @Test
    public void getAuditStrategyFromString_shouldResolveTheFullyQualifiedNoneExceptAuditStrategyClassName()
            throws Exception {
        assertEquals(AuditStrategy.NONE_EXCEPT,
                helper.getAuditStrategyFromString(NoneExceptAuditStrategy.class.getName()));
    }

    @Test
    public void getAuditStrategyFromString_shouldResolveTheFullyQualifiedAllAuditStrategyClassName() throws Exception {
        assertEquals(AuditStrategy.ALL, helper.getAuditStrategyFromString(AllAuditStrategy.class.getName()));
    }

    @Test
    public void getAuditStrategyFromString_shouldResolveTheFullyQualifiedAllExceptAuditStrategyClassName()
            throws Exception {
        assertEquals(AuditStrategy.ALL_EXCEPT,
                helper.getAuditStrategyFromString(AllExceptAuditStrategy.class.getName()));
    }

    public class CustomTestAuditStrategy extends BaseAuditStrategy {

        /**
         * @see org.openmrs.module.auditlog.strategy.AuditStrategy#isAudited(Class)
         */
        @Override
        public boolean isAudited(Class<?> clazz) {
            return false;
        }
    }
}