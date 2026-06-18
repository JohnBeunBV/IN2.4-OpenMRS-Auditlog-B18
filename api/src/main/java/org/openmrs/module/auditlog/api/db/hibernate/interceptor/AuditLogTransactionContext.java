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

import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Stack;

import org.openmrs.module.auditlog.AuditLog;

/**
 * Holds all per-thread, per-transaction state used by {@link HibernateAuditLogInterceptor}.
 *
 * <p>Each of the nine stacks tracks a different aspect of the current Hibernate transaction.
 * Stacks (instead of plain ThreadLocals) allow nested transactions: the element at the top
 * always belongs to the innermost active transaction.</p>
 *
 * <p>Lifecycle:</p>
 * <ol>
 *   <li>Call {@link #pushFrame()} at the start of each transaction
 *       ({@code afterTransactionBegin}).</li>
 *   <li>Access the current frame via the typed {@code peek*()} accessors.</li>
 *   <li>Call {@link #popFrame()} in the {@code finally} block of
 *       {@code beforeTransactionCompletion}.</li>
 * </ol>
 *
 * Extracting this class from {@link HibernateAuditLogInterceptor} achieves two things:
 * <ul>
 *   <li>The interceptor itself shrinks to pure audit logic — no stack bookkeeping.</li>
 *   <li>Stack administration can be unit-tested in isolation, without a Hibernate session.</li>
 * </ul>
 */
public class AuditLogTransactionContext {

    // -------------------------------------------------------------------------
    // ThreadLocal stacks — one entry per active (nested) transaction
    // -------------------------------------------------------------------------

    private final ThreadLocal<Stack<HashSet<Object>>> inserts =
            new ThreadLocal<Stack<HashSet<Object>>>();

    private final ThreadLocal<Stack<HashSet<Object>>> updates =
            new ThreadLocal<Stack<HashSet<Object>>>();

    private final ThreadLocal<Stack<HashSet<Object>>> deletes =
            new ThreadLocal<Stack<HashSet<Object>>>();

    /** Maps each entity to its changed properties: propertyName → [newValue, oldValue]. */
    private final ThreadLocal<Stack<Map<Object, Map<String, Object[]>>>> objectChangesMap =
            new ThreadLocal<Stack<Map<Object, Map<String, Object[]>>>>();

    /** Maps each entity to the persistent collections it owns in this session. */
    private final ThreadLocal<Stack<Map<Object, List<Collection<?>>>>> entityCollectionsMap =
            new ThreadLocal<Stack<Map<Object, List<Collection<?>>>>>();

    /** Maps each owner entity to AuditLogs created for its collection elements. */
    private final ThreadLocal<Stack<Map<Object, List<AuditLog>>>> ownerUuidChildLogsMap =
            new ThreadLocal<Stack<Map<Object, List<AuditLog>>>>();

    /** Maps each collection element to its AuditLog, preventing duplicate log creation. */
    private final ThreadLocal<Stack<Map<Object, AuditLog>>> childObjectUuidAuditLogMap =
            new ThreadLocal<Stack<Map<Object, AuditLog>>>();

    /** Maps each parent entity to collection elements removed during this transaction. */
    private final ThreadLocal<Stack<Map<Object, HashSet<Object>>>> entityRemovedChildrenMap =
            new ThreadLocal<Stack<Map<Object, HashSet<Object>>>>();

    /** The timestamp at which each transaction started. */
    private final ThreadLocal<Stack<Date>> date =
            new ThreadLocal<Stack<Date>>();

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    /**
     * Initialises empty ThreadLocal stacks on the first call per thread, then
     * pushes a fresh frame for the new transaction onto every stack.
     *
     * <p>Must be called from {@code HibernateAuditLogInterceptor.afterTransactionBegin()}.</p>
     */
    public void pushFrame() {
        initializeStacksIfNecessary();

        inserts.get().push(new HashSet<Object>());
        updates.get().push(new HashSet<Object>());
        deletes.get().push(new HashSet<Object>());
        objectChangesMap.get().push(new HashMap<Object, Map<String, Object[]>>());
        entityCollectionsMap.get().push(new HashMap<Object, List<Collection<?>>>());
        ownerUuidChildLogsMap.get().push(new HashMap<Object, List<AuditLog>>());
        childObjectUuidAuditLogMap.get().push(new HashMap<Object, AuditLog>());
        entityRemovedChildrenMap.get().push(new HashMap<Object, HashSet<Object>>());
        date.get().push(new Date());
    }

    /**
     * Pops the current transaction's frame from every stack, then removes the
     * ThreadLocals entirely when no more nested transactions are active.
     *
     * <p>Must be called from the {@code finally} block of
     * {@code HibernateAuditLogInterceptor.beforeTransactionCompletion()}.</p>
     */
    public void popFrame() {
        inserts.get().pop();
        updates.get().pop();
        deletes.get().pop();
        objectChangesMap.get().pop();
        entityCollectionsMap.get().pop();
        ownerUuidChildLogsMap.get().pop();
        childObjectUuidAuditLogMap.get().pop();
        entityRemovedChildrenMap.get().pop();
        date.get().pop();

        removeStacksIfEmpty();
    }

    // -------------------------------------------------------------------------
    // Frame accessors — always operate on the top of each stack
    // -------------------------------------------------------------------------

    public HashSet<Object> currentInserts() {
        return inserts.get().peek();
    }

    public HashSet<Object> currentUpdates() {
        return updates.get().peek();
    }

    public HashSet<Object> currentDeletes() {
        return deletes.get().peek();
    }

    public Map<Object, Map<String, Object[]>> currentObjectChangesMap() {
        return objectChangesMap.get().peek();
    }

    public Map<Object, List<Collection<?>>> currentEntityCollectionsMap() {
        return entityCollectionsMap.get().peek();
    }

    public Map<Object, List<AuditLog>> currentOwnerChildLogsMap() {
        return ownerUuidChildLogsMap.get().peek();
    }

    public Map<Object, AuditLog> currentChildObjectAuditLogMap() {
        return childObjectUuidAuditLogMap.get().peek();
    }

    public Map<Object, HashSet<Object>> currentEntityRemovedChildrenMap() {
        return entityRemovedChildrenMap.get().peek();
    }

    public Date currentDate() {
        return date.get().peek();
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private void initializeStacksIfNecessary() {
        if (inserts.get() == null) {
            inserts.set(new Stack<HashSet<Object>>());
        }
        if (updates.get() == null) {
            updates.set(new Stack<HashSet<Object>>());
        }
        if (deletes.get() == null) {
            deletes.set(new Stack<HashSet<Object>>());
        }
        if (objectChangesMap.get() == null) {
            objectChangesMap.set(new Stack<Map<Object, Map<String, Object[]>>>());
        }
        if (entityCollectionsMap.get() == null) {
            entityCollectionsMap.set(new Stack<Map<Object, List<Collection<?>>>>());
        }
        if (ownerUuidChildLogsMap.get() == null) {
            ownerUuidChildLogsMap.set(new Stack<Map<Object, List<AuditLog>>>());
        }
        if (childObjectUuidAuditLogMap.get() == null) {
            childObjectUuidAuditLogMap.set(new Stack<Map<Object, AuditLog>>());
        }
        if (entityRemovedChildrenMap.get() == null) {
            entityRemovedChildrenMap.set(new Stack<Map<Object, HashSet<Object>>>());
        }
        if (date.get() == null) {
            date.set(new Stack<Date>());
        }
    }

    private void removeStacksIfEmpty() {
        if (inserts.get().empty())                  { inserts.remove(); }
        if (updates.get().empty())                  { updates.remove(); }
        if (deletes.get().empty())                  { deletes.remove(); }
        if (objectChangesMap.get().empty())         { objectChangesMap.remove(); }
        if (entityCollectionsMap.get().empty())     { entityCollectionsMap.remove(); }
        if (ownerUuidChildLogsMap.get().empty())    { ownerUuidChildLogsMap.remove(); }
        if (childObjectUuidAuditLogMap.get().empty()){ childObjectUuidAuditLogMap.remove(); }
        if (entityRemovedChildrenMap.get().empty()) { entityRemovedChildrenMap.remove(); }
        if (date.get().empty())                     { date.remove(); }
    }
}
