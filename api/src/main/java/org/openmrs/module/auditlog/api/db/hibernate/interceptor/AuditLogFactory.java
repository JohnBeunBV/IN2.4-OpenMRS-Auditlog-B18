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
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hibernate.Hibernate;
import org.openmrs.api.context.Context;
import org.openmrs.module.auditlog.AuditLog;
import org.openmrs.module.auditlog.AuditLog.Action;
import org.openmrs.module.auditlog.util.AuditLogConstants;
import org.openmrs.module.auditlog.util.AuditLogSerializer;
import org.openmrs.util.OpenmrsConstants;

/**
 * Factory for creating AuditLog instances.
 * This class encapsulates the logic for instantiating and configuring AuditLog
 * objects.
 * 
 * Responsibility: AuditLog object creation (SRP principle)
 */
public class AuditLogFactory {

    private static final Log log = LogFactory.getLog(AuditLogFactory.class);

    private TransactionAuditState transactionState;

    /**
     * Constructor
     * 
     * @param transactionState the transaction state manager
     */
    public AuditLogFactory(TransactionAuditState transactionState) {
        this.transactionState = transactionState;
    }

    /**
     * Creates an AuditLog if necessary for the specified object
     * If this is a collection element, it may have already been created.
     * 
     * @param object the object to create an AuditLog for
     * @param action the action that was performed (CREATED, UPDATED, or DELETED)
     * @return the created or previously created AuditLog
     */
    public AuditLog createAuditLogIfNecessary(Object object, Action action) {
        // If this is a collection element, we already created a log for it
        AuditLog auditLog = transactionState.getChildObjectUuidAuditLogMap().get(object);
        if (auditLog == null) {
            auditLog = instantiateAuditLog(object, action);
        }

        // Attach any child logs if this is a parent object
        Map<Object, List<AuditLog>> ownerChildLogsMap = transactionState.getOwnerUuidChildLogsMap();
        if (ownerChildLogsMap != null && ownerChildLogsMap.containsKey(object)) {
            for (AuditLog child : ownerChildLogsMap.get(object)) {
                auditLog.addChildAuditLog(child);
            }
        }
        return auditLog;
    }

    /**
     * Creates a new instance of an AuditLog for the specified object and Action
     * 
     * @param object the object to create an AuditLog for
     * @param action the action that was performed (CREATED, UPDATED, or DELETED)
     * @return the created AuditLog
     */
    public AuditLog instantiateAuditLog(Object object, Action action) {
        Serializable id = InterceptorUtil.getId(object);
        String serializedId = AuditLogSerializer.serializeObject(id);
        AuditLog auditLog = new AuditLog(object.getClass(), serializedId, action,
                Context.getAuthenticatedUser(), transactionState.getCurrentDate());

        auditLog.setOpenmrsVersion(OpenmrsConstants.OPENMRS_VERSION_SHORT);
        auditLog.setModuleVersion(AuditLogConstants.MODULE_VERSION);

        if (action == Action.UPDATED || action == Action.DELETED) {
            setSerializedData(object, action, auditLog);
        }

        return auditLog;
    }

    /**
     * Sets the serialized data for an AuditLog based on the action type
     * 
     * @param object   the object being audited
     * @param action   the action (UPDATED or DELETED)
     * @param auditLog the AuditLog to update
     */
    private void setSerializedData(Object object, Action action, AuditLog auditLog) {
        if (action == Action.UPDATED) {
            Map<String, Object[]> propertyValuesMap = transactionState.getObjectChangesMap().get(object);
            if (propertyValuesMap != null) {
                String serializedJson = AuditLogSerializer.serializeToJson(propertyValuesMap);
                Blob blob = Hibernate.createBlob(serializedJson.getBytes());
                auditLog.setSerializedData(blob);
            }
        } else if (action == Action.DELETED && InterceptorUtil.storeLastStateOfDeletedItems()) {
            String serializedData = InterceptorUtil.serializePersistentObject(object);
            Blob blob = Hibernate.createBlob(serializedData.getBytes());
            auditLog.setSerializedData(blob);
        }
    }
}
