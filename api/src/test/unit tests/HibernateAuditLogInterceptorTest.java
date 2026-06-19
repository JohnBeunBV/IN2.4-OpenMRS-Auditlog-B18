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
package org.openmrs.module.auditlog.api.db.hibernate.interceptor;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Unit tests for
 * {@link HibernateAuditLogInterceptor#isBlankOrCaseInsensitiveEqual(Object, Object)},
 * extracted from {@code onFlushDirty()} by the Extract Method refactoring.
 *
 * <p>
 * The method is package-private, so this test must live in the same package
 * ({@code org.openmrs.module.auditlog.api.db.hibernate.interceptor}) to call it
 * directly.
 * Instantiating {@code HibernateAuditLogInterceptor} directly does not require
 * a Hibernate
 * session or Spring context: the constructor only initializes the (also
 * context-free)
 * {@link AuditLogTransactionContext} field.
 * </p>
 */
public class HibernateAuditLogInterceptorTest {

    private HibernateAuditLogInterceptor interceptor = new HibernateAuditLogInterceptor();

    @Test
    public void isBlankOrCaseInsensitiveEqual_shouldReturnTrueWhenBothValuesAreNull() throws Exception {
        assertTrue(interceptor.isBlankOrCaseInsensitiveEqual(null, null));
    }

    @Test
    public void isBlankOrCaseInsensitiveEqual_shouldReturnTrueWhenPreviousIsNullAndCurrentIsBlank() throws Exception {
        assertTrue(interceptor.isBlankOrCaseInsensitiveEqual(null, ""));
    }

    @Test
    public void isBlankOrCaseInsensitiveEqual_shouldReturnTrueWhenPreviousIsBlankAndCurrentIsNull() throws Exception {
        assertTrue(interceptor.isBlankOrCaseInsensitiveEqual("", null));
    }

    @Test
    public void isBlankOrCaseInsensitiveEqual_shouldReturnTrueWhenBothValuesAreBlank() throws Exception {
        assertTrue(interceptor.isBlankOrCaseInsensitiveEqual("", ""));
    }

    @Test
    public void isBlankOrCaseInsensitiveEqual_shouldReturnTrueForIdenticalValues() throws Exception {
        assertTrue(interceptor.isBlankOrCaseInsensitiveEqual("abc", "abc"));
    }

    @Test
    public void isBlankOrCaseInsensitiveEqual_shouldReturnTrueWhenValuesOnlyDifferByCase() throws Exception {
        assertTrue(interceptor.isBlankOrCaseInsensitiveEqual("abc", "ABC"));
    }

    @Test
    public void isBlankOrCaseInsensitiveEqual_shouldReturnFalseForDifferentNonBlankValues() throws Exception {
        assertFalse(interceptor.isBlankOrCaseInsensitiveEqual("abc", "def"));
    }

    @Test
    public void isBlankOrCaseInsensitiveEqual_shouldReturnFalseWhenPreviousIsNullAndCurrentIsNonBlank()
            throws Exception {
        assertFalse(interceptor.isBlankOrCaseInsensitiveEqual(null, "abc"));
    }
}