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
package org.openmrs.module.auditlog.util;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Types;
import java.util.HashMap;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.openmrs.module.auditlog.AuditLog;

/**
 * UNIT TEST (no Spring/Hibernate context, no BaseModuleContextSensitiveTest).
 * <p>
 * {@link AuditLogActionType} is a plain Hibernate {@code UserType}
 * implementation with no
 * dependency on a running OpenMRS/Hibernate session - it only needs a
 * {@link ResultSet} and a
 * {@link PreparedStatement} to do its work. Rather than pulling in a mocking
 * framework, this
 * test uses small hand rolled JDK dynamic proxies that record/return exactly
 * what is needed,
 * which keeps the test fast, deterministic and dependency free.
 * <p>
 * Before this test, this class was entirely uncovered (0% / 46.7% combined with
 * other gaps),
 * even though every branch in it is simple and worth pinning down with a direct
 * test.
 */
public class AuditLogActionTypeTest {

    private AuditLogActionType actionType;

    @Before
    public void setup() {
        actionType = new AuditLogActionType();
    }

    /**
     * @see AuditLogActionType#sqlTypes()
     */
    @Test
    public void sqlTypes_shouldReturnVarcharType() throws Exception {
        assertArrayEquals(new int[] { Types.VARCHAR }, actionType.sqlTypes());
    }

    /**
     * @see AuditLogActionType#returnedClass()
     */
    @Test
    public void returnedClass_shouldReturnAuditLogActionClass() throws Exception {
        assertEquals(AuditLog.Action.class, actionType.returnedClass());
    }

    /**
     * @see AuditLogActionType#equals(Object, Object)
     */
    @Test
    public void equals_shouldReturnTrueForSameReference() throws Exception {
        assertTrue(actionType.equals(AuditLog.Action.CREATED, AuditLog.Action.CREATED));
    }

    /**
     * @see AuditLogActionType#equals(Object, Object)
     */
    @Test
    public void equals_shouldReturnTrueForEqualValues() throws Exception {
        // Different enum constants of the same name are still == in Java, so use
        // Strings instead
        // to genuinely exercise the x.equals(y) branch rather than the x == y short
        // circuit.
        assertTrue(actionType.equals(new String("abc"), new String("abc")));
    }

    /**
     * @see AuditLogActionType#equals(Object, Object)
     */
    @Test
    public void equals_shouldReturnFalseForDifferentValues() throws Exception {
        assertFalse(actionType.equals(AuditLog.Action.CREATED, AuditLog.Action.UPDATED));
    }

    /**
     * @see AuditLogActionType#equals(Object, Object)
     */
    @Test
    public void equals_shouldReturnFalseWhenFirstArgumentIsNull() throws Exception {
        assertFalse(actionType.equals(null, AuditLog.Action.CREATED));
    }

    /**
     * @see AuditLogActionType#hashCode(Object)
     */
    @Test
    public void hashCode_shouldReturnZeroForNull() throws Exception {
        assertEquals(0, actionType.hashCode(null));
    }

    /**
     * @see AuditLogActionType#hashCode(Object)
     */
    @Test
    public void hashCode_shouldDelegateToTheValuesHashCode() throws Exception {
        assertEquals(AuditLog.Action.DELETED.hashCode(), actionType.hashCode(AuditLog.Action.DELETED));
    }

    /**
     * @see AuditLogActionType#nullSafeGet(ResultSet, String[], Object)
     */
    @Test
    public void nullSafeGet_shouldReturnNullWhenTheColumnValueIsNull() throws Exception {
        ResultSet rs = newResultSetReturning(null);
        assertNull(actionType.nullSafeGet(rs, new String[] { "action" }, null));
    }

    /**
     * @see AuditLogActionType#nullSafeGet(ResultSet, String[], Object)
     */
    @Test
    public void nullSafeGet_shouldReturnTheMatchingActionEnumConstant() throws Exception {
        ResultSet rs = newResultSetReturning("UPDATED");
        Object value = actionType.nullSafeGet(rs, new String[] { "action" }, null);
        assertEquals(AuditLog.Action.UPDATED, value);
    }

    /**
     * @see AuditLogActionType#nullSafeSet(PreparedStatement, Object, int)
     */
    @Test
    public void nullSafeSet_shouldSetTheColumnAsSqlNullWhenTheValueIsNull() throws Exception {
        RecordingPreparedStatement st = newPreparedStatement();
        actionType.nullSafeSet(st.asPreparedStatement(), null, 1);
        assertTrue(st.nullWasSetAtIndex(1, Types.VARCHAR));
    }

    /**
     * @see AuditLogActionType#nullSafeSet(PreparedStatement, Object, int)
     */
    @Test
    public void nullSafeSet_shouldSetTheNameOfTheActionAsAString() throws Exception {
        RecordingPreparedStatement st = newPreparedStatement();
        actionType.nullSafeSet(st.asPreparedStatement(), AuditLog.Action.CREATED, 1);
        assertEquals("CREATED", st.stringSetAtIndex(1));
    }

    /**
     * @see AuditLogActionType#deepCopy(Object)
     */
    @Test
    public void deepCopy_shouldReturnTheSameValue() throws Exception {
        assertEquals(AuditLog.Action.DELETED, actionType.deepCopy(AuditLog.Action.DELETED));
    }

    /**
     * @see AuditLogActionType#isMutable()
     */
    @Test
    public void isMutable_shouldReturnFalse() throws Exception {
        assertFalse(actionType.isMutable());
    }

    /**
     * @see AuditLogActionType#disassemble(Object)
     */
    @Test
    public void disassemble_shouldReturnTheSameValue() throws Exception {
        assertEquals(AuditLog.Action.CREATED, actionType.disassemble(AuditLog.Action.CREATED));
    }

    /**
     * @see AuditLogActionType#assemble(java.io.Serializable, Object)
     */
    @Test
    public void assemble_shouldReturnTheCachedValue() throws Exception {
        assertEquals(AuditLog.Action.UPDATED, actionType.assemble(AuditLog.Action.UPDATED, null));
    }

    /**
     * @see AuditLogActionType#replace(Object, Object, Object)
     */
    @Test
    public void replace_shouldReturnTheOriginalValue() throws Exception {
        assertEquals(AuditLog.Action.DELETED,
                actionType.replace(AuditLog.Action.DELETED, AuditLog.Action.CREATED, null));
    }

    /**
     * Builds a minimal {@link ResultSet} proxy whose {@code getString(String)}
     * always returns the
     * given value, regardless of column name.
     */
    private ResultSet newResultSetReturning(final String columnValue) {
        InvocationHandler handler = new InvocationHandler() {

            @Override
            public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                if ("getString".equals(method.getName())) {
                    return columnValue;
                }
                return null;
            }
        };
        return (ResultSet) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] { ResultSet.class },
                handler);
    }

    private RecordingPreparedStatement newPreparedStatement() {
        return new RecordingPreparedStatement();
    }

    /**
     * Minimal {@link PreparedStatement} proxy that records calls to
     * setNull/setString so the test
     * can assert on them, without needing a real JDBC connection or a mocking
     * framework.
     */
    private static class RecordingPreparedStatement implements InvocationHandler {

        private final Map<Integer, Object> nullCalls = new HashMap<Integer, Object>();

        private final Map<Integer, String> stringCalls = new HashMap<Integer, String>();

        private final PreparedStatement proxy = (PreparedStatement) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] { PreparedStatement.class }, this);

        @Override
        public Object invoke(Object p, Method method, Object[] args) throws Throwable {
            if ("setNull".equals(method.getName())) {
                nullCalls.put((Integer) args[0], args[1]);
            } else if ("setString".equals(method.getName())) {
                stringCalls.put((Integer) args[0], (String) args[1]);
            }
            return null;
        }

        boolean nullWasSetAtIndex(int index, int sqlType) {
            return nullCalls.containsKey(index) && nullCalls.get(index).equals(sqlType);
        }

        String stringSetAtIndex(int index) {
            return stringCalls.get(index);
        }

        PreparedStatement asPreparedStatement() {
            return proxy;
        }
    }
}