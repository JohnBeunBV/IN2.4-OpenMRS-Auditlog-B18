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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;

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
import java.sql.Blob;
import org.hibernate.metadata.ClassMetadata;
import org.hibernate.persister.entity.EntityPersister;
import org.hibernate.type.StringType;
import org.hibernate.type.TextType;
import org.hibernate.type.Type;
import org.openmrs.module.auditlog.AuditLog;
import org.openmrs.module.auditlog.AuditLog.Action;
import org.openmrs.module.auditlog.util.AuditLogConstants;
import org.openmrs.module.auditlog.util.AuditLogSerializer;
import org.openmrs.module.auditlog.util.AuditLogUtil;
import org.openmrs.module.auditlog.util.HibernateMetadataUtils;
import org.openmrs.util.OpenmrsConstants;
import org.openmrs.util.OpenmrsUtil;
import org.springframework.orm.hibernate3.SessionFactoryUtils;
import org.springframework.stereotype.Component;

/**
 * A hibernate {@link org.hibernate.Interceptor} implementation, intercepts any
 * database inserts,
 * updates and deletes and creates audit log entries for Audited Objects, it
 * logs changes for a
 * single session meaning that if User A and B concurrently make changes to the
 * same object, there
 * will be 2 log entries in the DB, one for each user's session. Any
 * changes/inserts/deletes made to
 * the DB that are not made through the application won't be detected by the
 * module.
 * 
 * <pre>
 * Trying to make this the very last intercepter in order to catch all updates, inserts and deletes. Typically this
 * should be after the AuditableInterceptor from core so that dateChanged and changedBy field are
 * ignored but it might give way for other interceptors come after this
 * </pre>
 */
@Component("zzz-auditLogInterceptor")
public class HibernateAuditLogInterceptor extends EmptyInterceptor {

	private static final long serialVersionUID = 1L;

	private static final Log log = LogFactory.getLog(HibernateAuditLogInterceptor.class);

	// Refactored: Transaction state is now managed by TransactionAuditState
	private TransactionAuditState transactionState = new TransactionAuditState();

	// Refactored: AuditLog creation is now delegated to AuditLogFactory
	private AuditLogFactory auditLogFactory = new AuditLogFactory(transactionState);

	// Ignore these properties because they match auditLog.user and
	// auditLog.dateCreated
	private static final String[] IGNORED_PROPERTIES = new String[] { "changedBy", "dateChanged", "creator",
			"dateCreated",
			"voidedBy", "dateVoided", "retiredBy", "dateRetired", "personChangedBy", "personDateChanged",
			"personCreator",
			"personDateCreated" };

	/**
	 * @see org.hibernate.EmptyInterceptor#afterTransactionBegin(org.hibernate.Transaction)
	 */
	@Override
	public void afterTransactionBegin(Transaction tx) {
		transactionState.initializeForTransaction();
	}

	/**
	 * @see org.hibernate.EmptyInterceptor#onSave(Object, java.io.Serializable,
	 *      Object[], String[],
	 *      org.hibernate.type.Type[])
	 */
	@Override
	public boolean onSave(Object entity, Serializable id, Object[] state, String[] propertyNames, Type[] types) {
		if (InterceptorUtil.isAudited(entity.getClass())) {
			if (log.isDebugEnabled()) {
				log.debug("Creating log entry for created object with id:" + id + " of type:"
						+ entity.getClass().getName());
			}

			transactionState.getInserts().add(entity);
		}

		return false;
	}

	/**
	 * @see org.hibernate.EmptyInterceptor#onFlushDirty(Object,
	 *      java.io.Serializable, Object[],
	 *      Object[], String[], org.hibernate.type.Type[])
	 */
	@Override
	public boolean onFlushDirty(Object entity, Serializable id, Object[] currentState, Object[] previousState,
			String[] propertyNames, Type[] types) {

		if (propertyNames != null && InterceptorUtil.isAudited(entity.getClass())) {
			if (previousState == null) {
				// This is a detached object, load the previous state in a separate session
				Session tmpSession = null;
				SessionFactory sf = InterceptorUtil.getSessionFactory();
				try {
					tmpSession = SessionFactoryUtils.getNewSession(sf);
					Object obj = tmpSession.get(entity.getClass(), id);
					EntityPersister ep = ((SessionImplementor) tmpSession).getEntityPersister(null, obj);
					previousState = ep.getPropertyValues(obj, EntityMode.POJO);
				} finally {
					if (tmpSession != null) {
						SessionFactoryUtils.closeSession(tmpSession);
					}
				}

			}
			Map<String, Object[]> propertyChangesMap = null;// Map<propertyName, Object[]{currentValue, PreviousValue}>
			for (int i = 0; i < propertyNames.length; i++) {
				// we need to ignore dateChanged and changedBy fields in any case they
				// are actually part of the Auditlog in form of user and dateCreated
				if (ArrayUtils.contains(IGNORED_PROPERTIES, propertyNames[i])) {
					continue;
				}

				Object previousValue = (previousState != null) ? previousState[i] : null;
				Object currentValue = (currentState != null) ? currentState[i] : null;
				if (!types[i].isCollectionType() && !OpenmrsUtil.nullSafeEquals(currentValue, previousValue)) {
					// For string properties, ignore changes from null to blank and vice versa
					// TODO This should be user configurable via a module GP
					if (StringType.class.getName().equals(types[i].getClass().getName())
							|| TextType.class.getName().equals(types[i].getClass().getName())) {
						String currentStateString = null;
						if (currentValue != null && !StringUtils.isBlank(currentValue.toString())) {
							currentStateString = currentValue.toString();
						}

						String previousValueString = null;
						if (previousValue != null && !StringUtils.isBlank(previousValue.toString())) {
							previousValueString = previousValue.toString();
						}

						// TODO Case sensibility here should be configurable via a GP
						if (OpenmrsUtil.nullSafeEqualsIgnoreCase(previousValueString, currentStateString)) {
							continue;
						}
					}

					if (propertyChangesMap == null) {
						propertyChangesMap = new HashMap<String, Object[]>();
					}

					String serializedPreviousValue = AuditLogSerializer.serializeObject(previousValue);
					String serializedCurrentValue = AuditLogSerializer.serializeObject(currentValue);

					propertyChangesMap.put(propertyNames[i],
							new String[] { serializedCurrentValue, serializedPreviousValue });
				}
			}

			if (MapUtils.isNotEmpty(propertyChangesMap)) {
				if (log.isDebugEnabled()) {
					log.debug("Creating log entry for updated object with id:" + id + " of type:"
							+ entity.getClass().getName());
				}

				transactionState.getUpdates().add(entity);
				transactionState.getObjectChangesMap().put(entity, propertyChangesMap);
			}
		}

		return false;
	}

	/**
	 * @see org.hibernate.EmptyInterceptor#onDelete(Object, java.io.Serializable,
	 *      Object[],
	 *      String[], org.hibernate.type.Type[])
	 */
	@Override
	public void onDelete(Object entity, Serializable id, Object[] state, String[] propertyNames, Type[] types) {
		if (InterceptorUtil.isAudited(entity.getClass())) {
			if (log.isDebugEnabled()) {
				log.debug("Creating log entry for deleted object with id:" + id + " of type:"
						+ entity.getClass().getName());
			}
			for (int i = 0; i < types.length; i++) {
				if (types[i].isCollectionType()) {
					// Avoids LazyInitializationException since the parent is already purged
					Hibernate.initialize(state[i]);
				}
			}
			transactionState.getDeletes().add(entity);
		}
	}

	/**
	 * @see org.hibernate.EmptyInterceptor#onCollectionUpdate(Object,
	 *      java.io.Serializable)
	 */
	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Override
	public void onCollectionUpdate(Object collection, Serializable key) throws CallbackException {
		if (collection != null) {
			PersistentCollection persistentColl = ((PersistentCollection) collection);
			if (InterceptorUtil.isAudited(persistentColl.getOwner().getClass())) {
				Object owningObject = persistentColl.getOwner();
				Map previousStoredSnapshotMap = (Map) persistentColl.getStoredSnapshot();
				Object previousCollOrMap;
				if (Collection.class.isAssignableFrom(collection.getClass())) {
					previousCollOrMap = previousStoredSnapshotMap.values();
				} else {
					previousCollOrMap = previousStoredSnapshotMap;
				}

				transactionState.handleUpdatedCollection(collection, previousCollOrMap, owningObject, persistentColl.getRole());
			}
		}
	}

	@Override
	public void onCollectionRemove(Object collection, Serializable key) throws CallbackException {
		// We need to get all collection elements and link their childlogs to the
		// parent's
		if (collection != null) {
			PersistentCollection persistentColl = (PersistentCollection) collection;
			if (InterceptorUtil.isAudited(persistentColl.getOwner().getClass())) {
				Object owningObject = persistentColl.getOwner();
				String role = persistentColl.getRole();
				String propertyName = role.substring(role.lastIndexOf('.') + 1);
				ClassMetadata cmd = HibernateMetadataUtils
						.getClassMetadata(AuditLogUtil.getActualType(owningObject));
				Object currentCollection = cmd.getPropertyValue(
						owningObject,
						propertyName,
						EntityMode.POJO);
				// new instance i.e one calls the collection's setter and passes in a new
				// instance even if the
				// new collection contains some elements, we want to treat this as regular
				// collection update,
				// Except if onCollectionRemove is called because the owner got purged from the
				// DB.
				// I believe hibernate calls onDelete for the owner before onCollectionRemove
				// for all its
				// collections so we can guarantee that the owner is already in the 'deletes'
				// state
				boolean isOwnerDeleted = OpenmrsUtil.collectionContains(transactionState.getDeletes(), owningObject);
				if (Collection.class.isAssignableFrom(collection.getClass())) {
					Collection coll = (Collection) collection;
					if (!coll.isEmpty()) {
						if (isOwnerDeleted) {
							if (transactionState.getEntityRemovedChildrenMap().get(owningObject) == null) {
								transactionState.getEntityRemovedChildrenMap().put(owningObject, new HashSet<Object>());
							}
							for (Object removedItem : coll) {
								transactionState.getEntityRemovedChildrenMap().get(owningObject).add(removedItem);
							}
						} else if (!isOwnerDeleted && currentCollection == null) {
							Class<?> propertyClass = cmd.getPropertyType(propertyName).getReturnedClass();
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
				} else {
					// TODO: Handle other persistent collections types e.g bags
				}

				if (!isOwnerDeleted) {
					transactionState.handleUpdatedCollection(currentCollection, collection, owningObject, role);
				}
			}
		}
	}

	/**
	 * This is a hacky way to find all loaded persistent objects in this session
	 * that have
	 * collections
	 * 
	 * @see org.hibernate.EmptyInterceptor#findDirty(Object, java.io.Serializable,
	 *      Object[],
	 *      Object[], String[], org.hibernate.type.Type[])
	 */
	@Override
	public int[] findDirty(Object entity, Serializable id, Object[] currentState, Object[] previousState,
			String[] propertyNames, Type[] types) {
		if (InterceptorUtil.isAudited(entity.getClass())) {
			if (transactionState.getEntityCollectionsMap().get(entity) == null) {
				// This is the first time we are trying to find collection elements for this
				// object
				if (log.isDebugEnabled()) {
					log.debug("Finding collections for object:" + entity.getClass() + " #" + id);
				}

				for (int i = 0; i < propertyNames.length; i++) {
					if (types[i].isCollectionType()) {
						Object coll = currentState[i];
						// For now ignore maps because still cant imagine a logical case where the
						// keys or values are Persistent objects that can't exist on their own
						if (coll != null && Collection.class.isAssignableFrom(coll.getClass())) {
							Collection<?> collection = (Collection<?>) coll;
							if (!collection.isEmpty()) {
								if (transactionState.getEntityCollectionsMap().get(entity) == null) {
									transactionState.getEntityCollectionsMap().put(entity,
											new ArrayList<Collection<?>>());
								}
								if (!HibernateMetadataUtils
										.getCollectionPersister(propertyNames[i], entity.getClass(), null)
										.isManyToMany()) {
									transactionState.getEntityCollectionsMap().get(entity).add(collection);
								}
							}
						} // else {
							// TODO handle maps too because hibernate treats maps to be of CollectionType
							// }
					}
				}
			}
		}

		return super.findDirty(entity, id, currentState, previousState, propertyNames, types);
	}

	/**
	 * @see org.hibernate.EmptyInterceptor#beforeTransactionCompletion(org.hibernate.Transaction)
	 */
	@Override
	public void beforeTransactionCompletion(Transaction tx) {
		try {
			if (!transactionState.hasChanges()) {
				return;
			}

			try {
				// TODO handle daemon or un authenticated operations

				// If we have any entities in the session that have child collections and there
				// were some updates,
				// check all collection items to find dirty ones so that we can mark the the
				// owners as dirty too
				// I.e if a ConceptName/Mapping/Description was edited, mark the the Concept as
				// dirty too
				for (Map.Entry<Object, List<Collection<?>>> entry : transactionState.getEntityCollectionsMap()
						.entrySet()) {
					for (Collection<?> coll : entry.getValue()) {
						for (Object obj : coll) {
							boolean isInsert = OpenmrsUtil.collectionContains(transactionState.getInserts(), obj);
							boolean isUpdate = OpenmrsUtil.collectionContains(transactionState.getUpdates(), obj);

							// We handle the removed collections items below because either way they
							// are nolonger in the current collection
							if (isInsert || isUpdate) {
								Object owner = entry.getKey();
								boolean ownerHasUpdates = OpenmrsUtil.collectionContains(transactionState.getUpdates(),
										owner);
								boolean isOwnerNew = OpenmrsUtil.collectionContains(transactionState.getInserts(),
										owner);
								if (ownerHasUpdates) {
									if (log.isDebugEnabled()) {
										log.debug("There is already an auditlog for owner:" + owner.getClass() + " - "
												+ InterceptorUtil.getId(owner));
									}
								} else if (!isOwnerNew) {
									// A collection item was updated and no other update had been made on the owner
									if (log.isDebugEnabled()) {
										log.debug("Creating log entry for edited owner object with id:"
												+ InterceptorUtil.getId(owner) + " of type:"
												+ owner.getClass().getName()
												+ " due to an update for a item in a child collection");
									}
									transactionState.getUpdates().add(owner);
								}

								if (InterceptorUtil.isAudited(obj.getClass())) {
									if (transactionState.getOwnerUuidChildLogsMap().get(owner) == null) {
										transactionState.getOwnerUuidChildLogsMap().put(owner,
												new ArrayList<AuditLog>());
									}

									AuditLog childLog = auditLogFactory.instantiateAuditLog(obj,
											isInsert ? Action.CREATED : Action.UPDATED);

									transactionState.getChildObjectUuidAuditLogMap().put(obj, childLog);
									transactionState.getOwnerUuidChildLogsMap().get(owner).add(childLog);
								}

								// TODO add this collection to the list of changes properties
								/*
								 * Map<String, Object[]> propertyValuesMap =
								 * objectChangesMap.get().peek().get(owner);
								 * if(propertyValuesMap == null)
								 * propertyValuesMap = new HashMap<String, Object[]>();
								 * propertyValuesMap.put(arg0, arg1);
								 */
							}
						}
					}
				}

				for (Map.Entry<Object, HashSet<Object>> entry : transactionState.getEntityRemovedChildrenMap()
						.entrySet()) {
					Object removedItemsOwner = entry.getKey();
					for (Object removed : entry.getValue()) {
						// TODO add test to ensure that this should fail for collections
						// that don't have all-delete-orphan cascade
						boolean isDelete = OpenmrsUtil.collectionContains(transactionState.getDeletes(), removed);
						if (isDelete) {
							if (InterceptorUtil.isAudited(removed.getClass())) {
								if (transactionState.getOwnerUuidChildLogsMap().get(removedItemsOwner) == null)
									transactionState.getOwnerUuidChildLogsMap().put(removedItemsOwner,
											new ArrayList<AuditLog>());

								AuditLog childLog = auditLogFactory.instantiateAuditLog(removed, Action.DELETED);

								transactionState.getChildObjectUuidAuditLogMap().put(removed, childLog);
								transactionState.getOwnerUuidChildLogsMap().get(removedItemsOwner).add(childLog);
							}
						}
					}
				}

				List<AuditLog> logs = new ArrayList<AuditLog>();
				for (Object insert : transactionState.getInserts()) {
					logs.add(auditLogFactory.createAuditLogIfNecessary(insert, Action.CREATED));
				}

				for (Object delete : transactionState.getDeletes()) {
					logs.add(auditLogFactory.createAuditLogIfNecessary(delete, Action.DELETED));
				}

				for (Object update : transactionState.getUpdates()) {
					logs.add(auditLogFactory.createAuditLogIfNecessary(update, Action.UPDATED));
				}

				for (AuditLog al : logs) {
					InterceptorUtil.saveAuditLog(al);
				}
			} catch (Exception e) {
				// error should not bubble out of the interceptor
				log.error("An error occured while creating audit log(s):", e);
			}
		} finally {
			// cleanup
			transactionState.cleanup();
		}
	}
}
