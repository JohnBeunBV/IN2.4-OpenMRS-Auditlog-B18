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

import java.io.Serializable;
import java.sql.Blob;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hibernate.CallbackException;
import org.hibernate.EmptyInterceptor;
import org.hibernate.EntityMode;
import org.hibernate.Hibernate;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.hibernate.collection.PersistentCollection;
import org.hibernate.engine.SessionImplementor;
import org.hibernate.metadata.ClassMetadata;
import org.hibernate.persister.entity.EntityPersister;
import org.hibernate.type.StringType;
import org.hibernate.type.TextType;
import org.hibernate.type.Type;
import org.openmrs.api.context.Context;
import org.openmrs.module.auditlog.AuditLog;
import org.openmrs.module.auditlog.AuditLog.Action;
import org.openmrs.module.auditlog.util.AuditLogConstants;
import org.openmrs.module.auditlog.util.AuditLogSerializer;
import org.openmrs.module.auditlog.util.AuditLogUtil;
import org.openmrs.util.OpenmrsConstants;
import org.openmrs.util.OpenmrsUtil;
import org.springframework.orm.hibernate3.SessionFactoryUtils;
import org.springframework.stereotype.Component;

/**
 * A hibernate {@link org.hibernate.Interceptor} implementation, intercepts any database inserts,
 * updates and deletes and creates audit log entries for Audited Objects.
 *
 * <p>Refactoring applied (sprint deliverable):</p>
 * <ul>
 *   <li><b>Extract Class</b> – the nine {@code ThreadLocal<Stack<...>>} fields and their
 *       lifecycle methods have been moved to {@link AuditLogTransactionContext}. This class
 *       holds a single {@code ctx} field instead.</li>
 *   <li><b>Extract Method</b> – {@code beforeTransactionCompletion} is decomposed into
 *       three focused methods:
 *       {@link #propagateDirtyCollectionOwners()},
 *       {@link #createChildLogsForRemovedItems()}, and
 *       {@link #buildAndPersistAuditLogs()}.</li>
 *   <li><b>Extract Method</b> – the string-comparison block inside {@code onFlushDirty} is
 *       now {@link #isBlankOrCaseInsensitiveEqual(Object, Object)}, making it independently
 *       unit-testable.</li>
 * </ul>
 */
@Component("zzz-auditLogInterceptor")
public class HibernateAuditLogInterceptor extends EmptyInterceptor {

    private static final long serialVersionUID = 1L;

    private static final Log log = LogFactory.getLog(HibernateAuditLogInterceptor.class);

    // -----------------------------------------------------------------------
    // REFACTORING: Extract Class
    // Nine ThreadLocal<Stack<...>> fields + initializeStacksIfNecessary() +
    // removeStacksIfEmpty() → AuditLogTransactionContext
    // -----------------------------------------------------------------------
    private final AuditLogTransactionContext ctx = new AuditLogTransactionContext();

    private static final String[] IGNORED_PROPERTIES = new String[] {
            "changedBy", "dateChanged", "creator", "dateCreated",
            "voidedBy", "dateVoided", "retiredBy", "dateRetired",
            "personChangedBy", "personDateChanged", "personCreator", "personDateCreated"
    };

    // -----------------------------------------------------------------------
    // Hibernate Interceptor callbacks
    // -----------------------------------------------------------------------

    @Override
    public void afterTransactionBegin(Transaction tx) {
        ctx.pushFrame();
    }

    @Override
    public boolean onSave(Object entity, Serializable id, Object[] state,
                          String[] propertyNames, Type[] types) {
        if (InterceptorUtil.isAudited(entity.getClass())) {
            if (log.isDebugEnabled()) {
                log.debug("Creating log entry for created object with id:" + id
                        + " of type:" + entity.getClass().getName());
            }
            ctx.currentInserts().add(entity);
        }
        return false;
    }

    @Override
    public boolean onFlushDirty(Object entity, Serializable id,
                                Object[] currentState, Object[] previousState,
                                String[] propertyNames, Type[] types) {

        if (propertyNames != null && InterceptorUtil.isAudited(entity.getClass())) {
            if (previousState == null) {
                // Detached object – load previous state in a separate session
                Session tmpSession = null;
                SessionFactory sf = InterceptorUtil.getSessionFactory();
                try {
                    tmpSession = SessionFactoryUtils.getNewSession(sf);
                    Object obj = tmpSession.get(entity.getClass(), id);
                    EntityPersister ep = ((SessionImplementor) tmpSession)
                            .getEntityPersister(null, obj);
                    previousState = ep.getPropertyValues(obj, EntityMode.POJO);
                } finally {
                    if (tmpSession != null) {
                        SessionFactoryUtils.closeSession(tmpSession);
                    }
                }
            }

            Map<String, Object[]> propertyChangesMap = null;
            for (int i = 0; i < propertyNames.length; i++) {
                if (ArrayUtils.contains(IGNORED_PROPERTIES, propertyNames[i])) {
                    continue;
                }

                Object previousValue = (previousState != null) ? previousState[i] : null;
                Object currentValue  = (currentState  != null) ? currentState[i]  : null;

                if (!types[i].isCollectionType()
                        && !OpenmrsUtil.nullSafeEquals(currentValue, previousValue)) {

                    // REFACTORING: Extract Method – string comparison logic
                    if (types[i] instanceof StringType || types[i] instanceof TextType) {
                        if (isBlankOrCaseInsensitiveEqual(previousValue, currentValue)) {
                            continue;
                        }
                    }

                    if (propertyChangesMap == null) {
                        propertyChangesMap = new HashMap<String, Object[]>();
                    }

                    propertyChangesMap.put(propertyNames[i], new String[] {
                            AuditLogSerializer.serializeObject(currentValue),
                            AuditLogSerializer.serializeObject(previousValue)
                    });
                }
            }

            if (MapUtils.isNotEmpty(propertyChangesMap)) {
                if (log.isDebugEnabled()) {
                    log.debug("Creating log entry for updated object with id:" + id
                            + " of type:" + entity.getClass().getName());
                }
                ctx.currentUpdates().add(entity);
                ctx.currentObjectChangesMap().put(entity, propertyChangesMap);
            }
        }
        return false;
    }

    @Override
    public void onDelete(Object entity, Serializable id, Object[] state,
                         String[] propertyNames, Type[] types) {
        if (InterceptorUtil.isAudited(entity.getClass())) {
            if (log.isDebugEnabled()) {
                log.debug("Creating log entry for deleted object with id:" + id
                        + " of type:" + entity.getClass().getName());
            }
            for (int i = 0; i < types.length; i++) {
                if (types[i].isCollectionType()) {
                    Hibernate.initialize(state[i]);
                }
            }
            ctx.currentDeletes().add(entity);
        }
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Override
    public void onCollectionUpdate(Object collection, Serializable key) throws CallbackException {
        if (collection != null) {
            PersistentCollection persistentColl = (PersistentCollection) collection;
            if (InterceptorUtil.isAudited(persistentColl.getOwner().getClass())) {
                Object owningObject = persistentColl.getOwner();
                Map previousStoredSnapshotMap = (Map) persistentColl.getStoredSnapshot();
                Object previousCollOrMap;
                if (Collection.class.isAssignableFrom(collection.getClass())) {
                    previousCollOrMap = previousStoredSnapshotMap.values();
                } else {
                    previousCollOrMap = previousStoredSnapshotMap;
                }
                handleUpdatedCollection(collection, previousCollOrMap, owningObject,
                        persistentColl.getRole());
            }
        }
    }

    @Override
    public void onCollectionRemove(Object collection, Serializable key) throws CallbackException {
        if (collection != null) {
            PersistentCollection persistentColl = (PersistentCollection) collection;
            if (InterceptorUtil.isAudited(persistentColl.getOwner().getClass())) {
                Object owningObject = persistentColl.getOwner();
                String role = persistentColl.getRole();
                String propertyName = role.substring(role.lastIndexOf('.') + 1);
                ClassMetadata cmd = AuditLogUtil.getClassMetadata(
                        AuditLogUtil.getActualType(owningObject));
                Object currentCollection = cmd.getPropertyValue(owningObject, propertyName,
                        EntityMode.POJO);

                boolean isOwnerDeleted = OpenmrsUtil.collectionContains(
                        ctx.currentDeletes(), owningObject);

                if (Collection.class.isAssignableFrom(collection.getClass())) {
                    Collection coll = (Collection) collection;
                    if (!coll.isEmpty()) {
                        if (isOwnerDeleted) {
                            if (ctx.currentEntityRemovedChildrenMap().get(owningObject) == null) {
                                ctx.currentEntityRemovedChildrenMap()
                                        .put(owningObject, new HashSet<Object>());
                            }
                            for (Object removedItem : coll) {
                                ctx.currentEntityRemovedChildrenMap()
                                        .get(owningObject).add(removedItem);
                            }
                        } else if (currentCollection == null) {
                            Class<?> propertyClass = cmd.getPropertyType(propertyName)
                                    .getReturnedClass();
                            if (Set.class.isAssignableFrom(propertyClass)) {
                                currentCollection = Collections.EMPTY_SET;
                            } else if (List.class.isAssignableFrom(propertyClass)) {
                                currentCollection = Collections.EMPTY_LIST;
                            }
                        }
                    }
                } else if (Map.class.isAssignableFrom(collection.getClass())) {
                    Map map = (Map) collection;
                    if (!map.isEmpty() && !isOwnerDeleted && currentCollection == null) {
                        currentCollection = Collections.EMPTY_MAP;
                    }
                }

                if (!isOwnerDeleted) {
                    handleUpdatedCollection(currentCollection, collection, owningObject, role);
                }
            }
        }
    }

    @Override
    public int[] findDirty(Object entity, Serializable id, Object[] currentState,
                           Object[] previousState, String[] propertyNames, Type[] types) {
        if (InterceptorUtil.isAudited(entity.getClass())) {
            if (ctx.currentEntityCollectionsMap().get(entity) == null) {
                if (log.isDebugEnabled()) {
                    log.debug("Finding collections for object:" + entity.getClass() + " #" + id);
                }
                for (int i = 0; i < propertyNames.length; i++) {
                    if (types[i].isCollectionType()) {
                        Object coll = currentState[i];
                        if (coll != null && Collection.class.isAssignableFrom(coll.getClass())) {
                            Collection<?> collection = (Collection<?>) coll;
                            if (!collection.isEmpty()) {
                                if (ctx.currentEntityCollectionsMap().get(entity) == null) {
                                    ctx.currentEntityCollectionsMap()
                                            .put(entity, new ArrayList<Collection<?>>());
                                }
                                if (!AuditLogUtil.getCollectionPersister(
                                        propertyNames[i], entity.getClass(), null).isManyToMany()) {
                                    ctx.currentEntityCollectionsMap().get(entity).add(collection);
                                }
                            }
                        }
                    }
                }
            }
        }
        return super.findDirty(entity, id, currentState, previousState, propertyNames, types);
    }

    @Override
    public void beforeTransactionCompletion(Transaction tx) {
        try {
            if (ctx.currentInserts().isEmpty()
                    && ctx.currentUpdates().isEmpty()
                    && ctx.currentDeletes().isEmpty()) {
                return;
            }

            try {
                // REFACTORING: Extract Method – three focused private methods
                propagateDirtyCollectionOwners();
                createChildLogsForRemovedItems();
                buildAndPersistAuditLogs();
            } catch (Exception e) {
                log.error("An error occured while creating audit log(s):", e);
            }
        } finally {
            ctx.popFrame();
        }
    }

    // -----------------------------------------------------------------------
    // REFACTORING: Extract Method from beforeTransactionCompletion()
    // -----------------------------------------------------------------------

    /**
     * Lines 387–433 of the original {@code beforeTransactionCompletion()}.
     *
     * <p>Iterates over all entities that own collections and marks the owner as updated
     * whenever a collection item was inserted or updated. Also pre-creates child
     * {@link AuditLog} entries for those items so they are linked to the owner's log.</p>
     */
    private void propagateDirtyCollectionOwners() {
        for (Map.Entry<Object, List<Collection<?>>> entry :
                ctx.currentEntityCollectionsMap().entrySet()) {
            for (Collection<?> coll : entry.getValue()) {
                for (Object obj : coll) {
                    boolean isInsert = OpenmrsUtil.collectionContains(ctx.currentInserts(), obj);
                    boolean isUpdate = OpenmrsUtil.collectionContains(ctx.currentUpdates(), obj);

                    if (isInsert || isUpdate) {
                        Object owner = entry.getKey();
                        boolean ownerHasUpdates = OpenmrsUtil.collectionContains(
                                ctx.currentUpdates(), owner);
                        boolean isOwnerNew = OpenmrsUtil.collectionContains(
                                ctx.currentInserts(), owner);

                        if (ownerHasUpdates) {
                            if (log.isDebugEnabled()) {
                                log.debug("There is already an auditlog for owner:"
                                        + owner.getClass() + " - "
                                        + InterceptorUtil.getId(owner));
                            }
                        } else if (!isOwnerNew) {
                            if (log.isDebugEnabled()) {
                                log.debug("Creating log entry for edited owner object with id:"
                                        + InterceptorUtil.getId(owner) + " of type:"
                                        + owner.getClass().getName()
                                        + " due to an update for a item in a child collection");
                            }
                            ctx.currentUpdates().add(owner);
                        }

                        if (InterceptorUtil.isAudited(obj.getClass())) {
                            if (ctx.currentOwnerChildLogsMap().get(owner) == null) {
                                ctx.currentOwnerChildLogsMap()
                                        .put(owner, new ArrayList<AuditLog>());
                            }
                            AuditLog childLog = instantiateAuditLog(
                                    obj, isInsert ? Action.CREATED : Action.UPDATED);
                            ctx.currentChildObjectAuditLogMap().put(obj, childLog);
                            ctx.currentOwnerChildLogsMap().get(owner).add(childLog);
                        }
                    }
                }
            }
        }
    }

    /**
     * Lines 435–453 of the original {@code beforeTransactionCompletion()}.
     *
     * <p>For every parent whose collection had items removed, checks whether each
     * removed item was also recorded as a delete, and if so creates a child
     * {@link AuditLog} entry with {@link Action#DELETED}.</p>
     */
    private void createChildLogsForRemovedItems() {
        for (Map.Entry<Object, HashSet<Object>> entry :
                ctx.currentEntityRemovedChildrenMap().entrySet()) {
            Object removedItemsOwner = entry.getKey();
            for (Object removed : entry.getValue()) {
                boolean isDelete = OpenmrsUtil.collectionContains(ctx.currentDeletes(), removed);
                if (isDelete && InterceptorUtil.isAudited(removed.getClass())) {
                    if (ctx.currentOwnerChildLogsMap().get(removedItemsOwner) == null) {
                        ctx.currentOwnerChildLogsMap()
                                .put(removedItemsOwner, new ArrayList<AuditLog>());
                    }
                    AuditLog childLog = instantiateAuditLog(removed, Action.DELETED);
                    ctx.currentChildObjectAuditLogMap().put(removed, childLog);
                    ctx.currentOwnerChildLogsMap().get(removedItemsOwner).add(childLog);
                }
            }
        }
    }

    /**
     * Lines 455–470 of the original {@code beforeTransactionCompletion()}.
     *
     * <p>Assembles the final list of {@link AuditLog} objects for all inserts, deletes,
     * and updates, attaches child logs to their parents, and persists everything.</p>
     */
    private void buildAndPersistAuditLogs() {
        List<AuditLog> logs = new ArrayList<AuditLog>();

        for (Object insert : ctx.currentInserts()) {
            logs.add(createAuditLogIfNecessary(insert, Action.CREATED));
        }
        for (Object delete : ctx.currentDeletes()) {
            logs.add(createAuditLogIfNecessary(delete, Action.DELETED));
        }
        for (Object update : ctx.currentUpdates()) {
            logs.add(createAuditLogIfNecessary(update, Action.UPDATED));
        }

        for (AuditLog al : logs) {
            InterceptorUtil.saveAuditLog(al);
        }
    }

    // -----------------------------------------------------------------------
    // REFACTORING: Extract Method from onFlushDirty()
    // -----------------------------------------------------------------------

    /**
     * Lines 186–202 of the original {@code onFlushDirty()}.
     *
     * <p>Returns {@code true} when both values are either blank/null or equal when
     * compared case-insensitively. Used to suppress noise when a string property
     * transitions between {@code null} and an empty string, or only changes case.</p>
     *
     * <p>This is now independently testable without a Hibernate session:</p>
     * <pre>
     *     assertTrue(interceptor.isBlankOrCaseInsensitiveEqual(null, ""));
     *     assertTrue(interceptor.isBlankOrCaseInsensitiveEqual("Hello", "hello"));
     *     assertFalse(interceptor.isBlankOrCaseInsensitiveEqual("a", "b"));
     * </pre>
     *
     * @param previous the previous property value (may be {@code null})
     * @param current  the current property value (may be {@code null})
     * @return {@code true} if the change should be ignored
     */
    boolean isBlankOrCaseInsensitiveEqual(Object previous, Object current) {
        String currentStr = (current != null && !StringUtils.isBlank(current.toString()))
                ? current.toString() : null;
        String previousStr = (previous != null && !StringUtils.isBlank(previous.toString()))
                ? previous.toString() : null;
        return OpenmrsUtil.nullSafeEqualsIgnoreCase(previousStr, currentStr);
    }

    // -----------------------------------------------------------------------
    // Private helpers (unchanged logic, just re-delegated to ctx)
    // -----------------------------------------------------------------------

    private AuditLog createAuditLogIfNecessary(Object object, Action action) {
        AuditLog auditLog = ctx.currentChildObjectAuditLogMap().get(object);
        if (auditLog == null) {
            auditLog = instantiateAuditLog(object, action);
        }
        if (ctx.currentOwnerChildLogsMap().containsKey(object)) {
            for (AuditLog child : ctx.currentOwnerChildLogsMap().get(object)) {
                auditLog.addChildAuditLog(child);
            }
        }
        return auditLog;
    }

    private AuditLog instantiateAuditLog(Object object, Action action) {
        Serializable id = InterceptorUtil.getId(object);
        String serializedId = AuditLogSerializer.serializeObject(id);
        AuditLog auditLog = new AuditLog(object.getClass(), serializedId, action,
                Context.getAuthenticatedUser(), ctx.currentDate());
        auditLog.setOpenmrsVersion(OpenmrsConstants.OPENMRS_VERSION_SHORT);
        auditLog.setModuleVersion(AuditLogConstants.MODULE_VERSION);

        if (action == Action.UPDATED || action == Action.DELETED) {
            if (action == Action.UPDATED) {
                Map<String, Object[]> propertyValuesMap =
                        ctx.currentObjectChangesMap().get(object);
                if (propertyValuesMap != null) {
                    Blob blob = Hibernate.createBlob(
                            AuditLogSerializer.serializeToJson(propertyValuesMap).getBytes());
                    auditLog.setSerializedData(blob);
                }
            } else if (InterceptorUtil.storeLastStateOfDeletedItems()) {
                Blob blob = Hibernate.createBlob(
                        InterceptorUtil.serializePersistentObject(object).getBytes());
                auditLog.setSerializedData(blob);
            }
        }
        return auditLog;
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private void handleUpdatedCollection(Object currentCollOrMap, Object previousCollOrMap,
                                         Object owningObject, String role) {
        if (currentCollOrMap != null || previousCollOrMap != null) {
            String propertyName = role.substring(role.lastIndexOf('.') + 1);

            if (ctx.currentObjectChangesMap().get(owningObject) == null) {
                ctx.currentObjectChangesMap().put(owningObject, new HashMap<String, Object[]>());
            }

            Object previousSerializedItems = null;
            Object newSerializedItems = null;
            Class<?> collectionOrMapType = (currentCollOrMap != null)
                    ? currentCollOrMap.getClass() : previousCollOrMap.getClass();

            if (Collection.class.isAssignableFrom(collectionOrMapType)) {
                Collection cColl = (Collection) currentCollOrMap;
                Collection pColl = (Collection) previousCollOrMap;
                if (List.class.isAssignableFrom(collectionOrMapType)) {
                    if (cColl == null) cColl = Collections.EMPTY_LIST;
                    if (pColl == null) pColl = Collections.EMPTY_LIST;
                } else if (Set.class.isAssignableFrom(collectionOrMapType)) {
                    if (cColl == null) cColl = Collections.EMPTY_SET;
                    if (pColl == null) pColl = Collections.EMPTY_SET;
                }
                previousSerializedItems = AuditLogSerializer.serializeCollectionItems(pColl);
                newSerializedItems      = AuditLogSerializer.serializeCollectionItems(cColl);

                Set<Object> removedItems = new HashSet<Object>();
                removedItems.addAll(CollectionUtils.subtract(pColl, cColl));
                if (!removedItems.isEmpty()) {
                    if (ctx.currentEntityRemovedChildrenMap().get(owningObject) == null) {
                        ctx.currentEntityRemovedChildrenMap()
                                .put(owningObject, new HashSet<Object>());
                    }
                    ctx.currentEntityRemovedChildrenMap().get(owningObject)
                            .addAll(removedItems);
                }
            } else if (Map.class.isAssignableFrom(collectionOrMapType)) {
                if (previousCollOrMap.equals(currentCollOrMap)) {
                    return;
                }
                previousSerializedItems = AuditLogSerializer.serializeMapItems(
                        (Map) previousCollOrMap);
                newSerializedItems = AuditLogSerializer.serializeMapItems(
                        (Map) currentCollOrMap);
            }

            ctx.currentUpdates().add(owningObject);
            ctx.currentObjectChangesMap().get(owningObject)
                    .put(propertyName, new Object[] { newSerializedItems, previousSerializedItems });
        }
    }
}
