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

import java.util.Collections;
import java.util.Set;
import org.apache.commons.collections.CollectionUtils;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Stack;

import org.openmrs.module.auditlog.AuditLog;
import org.openmrs.module.auditlog.util.AuditLogUtil;

/**
 * Manages the transaction-scoped state for audit log interception.
 * This class is responsible for maintaining the ThreadLocal stacks that track
 * inserted, updated, and deleted objects during a Hibernate transaction.
 * 
 * Responsibility: Transaction state management (SRP principle)
 */
public class TransactionAuditState {

    // Use stacks to take care of nested transactions to avoid NPE since on each
    // transaction
    // completion the ThreadLocals get nullified
    private ThreadLocal<Stack<HashSet<Object>>> inserts = new ThreadLocal<Stack<HashSet<Object>>>();

    private ThreadLocal<Stack<HashSet<Object>>> updates = new ThreadLocal<Stack<HashSet<Object>>>();

    private ThreadLocal<Stack<HashSet<Object>>> deletes = new ThreadLocal<Stack<HashSet<Object>>>();

    // Mapping between objects and maps of their changed property names and their
    // older values
    // The first item in the array is the old value while the second is the new
    // value
    private ThreadLocal<Stack<Map<Object, Map<String, Object[]>>>> objectChangesMap = new ThreadLocal<Stack<Map<Object, Map<String, Object[]>>>>();

    // Mapping between entities and lists of their Collections in the current
    // session
    private ThreadLocal<Stack<Map<Object, List<Collection<?>>>>> entityCollectionsMap = new ThreadLocal<Stack<Map<Object, List<Collection<?>>>>>();

    // Mapping between parent entities and lists of AuditLogs for their collection
    // elements
    private ThreadLocal<Stack<Map<Object, List<AuditLog>>>> ownerUuidChildLogsMap = new ThreadLocal<Stack<Map<Object, List<AuditLog>>>>();

    // Mapping between collection elements and their AuditLogs
    private ThreadLocal<Stack<Map<Object, AuditLog>>> childObjectUuidAuditLogMap = new ThreadLocal<Stack<Map<Object, AuditLog>>>();

    // Mapping between parent entities and sets of removed collection elements
    private ThreadLocal<Stack<Map<Object, HashSet<Object>>>> entityRemovedChildrenMap = new ThreadLocal<Stack<Map<Object, HashSet<Object>>>>();

    private ThreadLocal<Stack<Date>> date = new ThreadLocal<Stack<Date>>();

    /**
     * Initializes the stacks for a new transaction
     */
    public void initializeForTransaction() {
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
     * Cleans up the transaction state (called at the end of a transaction)
     */
    public void cleanup() {
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

    // Accessor methods for all state

    public HashSet<Object> getInserts() {
        return inserts.get().peek();
    }

    public HashSet<Object> getUpdates() {
        return updates.get().peek();
    }

    public HashSet<Object> getDeletes() {
        return deletes.get().peek();
    }

    public Map<Object, Map<String, Object[]>> getObjectChangesMap() {
        return objectChangesMap.get().peek();
    }

    public Map<Object, List<Collection<?>>> getEntityCollectionsMap() {
        return entityCollectionsMap.get().peek();
    }

    public Map<Object, List<AuditLog>> getOwnerUuidChildLogsMap() {
        return ownerUuidChildLogsMap.get().peek();
    }

    public Map<Object, AuditLog> getChildObjectUuidAuditLogMap() {
        return childObjectUuidAuditLogMap.get().peek();
    }

    public Map<Object, HashSet<Object>> getEntityRemovedChildrenMap() {
        return entityRemovedChildrenMap.get().peek();
    }

    public Date getCurrentDate() {
        return date.get().peek();
    }

    public boolean hasChanges() {
        return !getInserts().isEmpty() || !getUpdates().isEmpty() || !getDeletes().isEmpty();
    }

    // Private helper methods

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
        if (inserts.get().empty()) {
            inserts.remove();
        }
        if (updates.get().empty()) {
            updates.remove();
        }
        if (deletes.get().empty()) {
            deletes.remove();
        }
        if (objectChangesMap.get().empty()) {
            objectChangesMap.remove();
        }
        if (entityCollectionsMap.get().empty()) {
            entityCollectionsMap.remove();
        }
        if (ownerUuidChildLogsMap.get().empty()) {
            ownerUuidChildLogsMap.remove();
        }
        if (childObjectUuidAuditLogMap.get().empty()) {
            childObjectUuidAuditLogMap.remove();
        }
        if (entityRemovedChildrenMap.get().empty()) {
            entityRemovedChildrenMap.remove();
        }
        if (date.get().empty()) {
            date.remove();
        }
    }

    public void handleUpdatedCollection(Object currentCollOrMap, Object previousCollOrMap, Object owningObject, String role) {
		
		if (currentCollOrMap != null || previousCollOrMap != null) {
			String propertyName = role.substring(role.lastIndexOf('.') + 1);
			
			if (objectChangesMap.get().peek().get(owningObject) == null) {
				objectChangesMap.get().peek().put(owningObject, new HashMap<String, Object[]>());
			}
			
			Object previousSerializedItems = null;
			Object newSerializedItems = null;
			Class<?> collectionOrMapType;
			if (currentCollOrMap != null) {
				collectionOrMapType = currentCollOrMap.getClass();
			} else {
				collectionOrMapType = previousCollOrMap.getClass();
			}
			
			if (Collection.class.isAssignableFrom(collectionOrMapType)) {
				Collection cColl = (Collection) currentCollOrMap;
				Collection pColl = (Collection) previousCollOrMap;
				if (List.class.isAssignableFrom(collectionOrMapType)) {
					if (cColl == null) {
						cColl = Collections.EMPTY_LIST;
					}
					if (pColl == null) {
						pColl = Collections.EMPTY_LIST;
					}
				} else if (Set.class.isAssignableFrom(collectionOrMapType)) {
					if (cColl == null) {
						cColl = Collections.EMPTY_SET;
					}
					if (pColl == null) {
						pColl = Collections.EMPTY_SET;
					}
				}
				
				previousSerializedItems = AuditLogUtil.serializeCollectionItems(pColl);
				newSerializedItems = AuditLogUtil.serializeCollectionItems(cColl);
				
				//Track removed items so that when we create logs for them,
				//and link them to the parent's log
				Set<Object> removedItems = new HashSet<Object>();
				removedItems.addAll(CollectionUtils.subtract(pColl, cColl));
				if (!removedItems.isEmpty()) {
					if (entityRemovedChildrenMap.get().peek().get(owningObject) == null) {
						entityRemovedChildrenMap.get().peek().put(owningObject, new HashSet<Object>());
					}
					for (Object removedItem : removedItems) {
						entityRemovedChildrenMap.get().peek().get(owningObject).add(removedItem);
					}
				}
			} else if (Map.class.isAssignableFrom(collectionOrMapType)) {
				//For some reason hibernate ends calling onCollectionUpdate even when the map has
				//no changes. I think it uses object equality for the map entries and assumes the map has
				//changes. Noticed this happens for user.userProperties and added a unit test to prove it
				if (previousCollOrMap.equals(currentCollOrMap)) {
					return;
				}
				
				previousSerializedItems = AuditLogUtil.serializeMapItems((Map) previousCollOrMap);
				newSerializedItems = AuditLogUtil.serializeMapItems((Map) currentCollOrMap);
			}
			
			updates.get().peek().add(owningObject);
			objectChangesMap.get().peek().get(owningObject)
			        .put(propertyName, new Object[] { newSerializedItems, previousSerializedItems });
		}
	}
}
