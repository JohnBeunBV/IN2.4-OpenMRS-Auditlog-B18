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

import static org.openmrs.module.auditlog.AuditLog.Action.DELETED;
import static org.openmrs.module.auditlog.AuditLog.Action.UPDATED;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.sql.Blob;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
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
import org.codehaus.jackson.map.ObjectMapper;
import org.hibernate.EntityMode;
import org.hibernate.MappingException;
import org.hibernate.engine.SessionFactoryImplementor;
import org.hibernate.metadata.ClassMetadata;
import org.hibernate.persister.collection.CollectionPersister;
import org.hibernate.proxy.HibernateProxy;
import org.openmrs.GlobalProperty;
import org.openmrs.api.APIException;
import org.openmrs.api.AdministrationService;
import org.openmrs.api.context.Context;
import org.openmrs.module.auditlog.AuditLog;
import org.openmrs.module.auditlog.api.db.DAOUtils;

/**
 * Contains utility methods used by the module.
 * 
 * NOTE: Serialization methods have been moved to AuditLogSerializer.
 * Reflection and Hibernate metadata methods have been moved to
 * HibernateMetadataUtils.
 * Legacy methods are preserved for backward compatibility.
 */
public class AuditLogUtil {

	private static final Log log = LogFactory.getLog(AuditLogUtil.class);

	private static ObjectMapper mapper = null;

	private static ObjectMapper getMapper() {
		if (mapper == null) {
			mapper = new ObjectMapper();
		}
		return mapper;
	}

	/**
	 * Converts a set of class objects to a list of class name strings
	 * 
	 * @param clazzes
	 * @return
	 */
	public static List<String> getAsListOfClassnames(Set<Class<?>> clazzes) {
		List<String> classnames = new ArrayList<String>(clazzes.size());
		for (Class<?> clazz : clazzes) {
			classnames.add(clazz.getName());
		}
		return classnames;
	}

	/**
	 * Gets the class of the collection elements if the property with the specified
	 * name is a
	 * collection
	 * 
	 * @param owningType   the type the collection belongs to
	 * @param propertyName the property name of the collection
	 * @return the class of the elements of the matching property
	 * @should return the class of the property
	 */
	/**
	 * Gets the class of the collection elements if the property with the specified
	 * name is a
	 * collection
	 * 
	 * DEPRECATED: Use HibernateMetadataUtils.getCollectionElementType() instead.
	 * 
	 * @param owningType   the type the collection belongs to
	 * @param propertyName the property name of the collection
	 * @return the class of the elements of the matching property
	 * @should return the class of the property
	 */
	@Deprecated
	public static Class<?> getCollectionElementType(Class<?> owningType, String propertyName) {
		return HibernateMetadataUtils.getCollectionElementType(owningType, propertyName);
	}

	/**
	 * Convenience method that find a field with the specified name in the specified
	 * class The
	 * method is recursively called to check all superclasses too
	 * 
	 * @param clazz
	 * @param fieldName
	 * @return
	 */
	/**
	 * Convenience method that find a field with the specified name in the specified
	 * class The
	 * method is recursively called to check all superclasses too
	 * 
	 * DEPRECATED: Use HibernateMetadataUtils.getField() instead.
	 * 
	 * @param clazz
	 * @param fieldName
	 * @return
	 */
	@Deprecated
	public static Field getField(Class<?> clazz, String fieldName) {
		return HibernateMetadataUtils.getField(clazz, fieldName);
	}

	/**
	 * Returns a map of changes for AuditLogs with action UPDATED
	 * 
	 * @param auditLog
	 * @return a map of changes
	 */
	public static Map<String, List> getChangesOfUpdatedItem(AuditLog auditLog) {
		if (auditLog.getAction() != UPDATED) {
			throw new APIException("Can't call this method for an AuditLog item with action " + auditLog.getAction());
		}

		Map<String, List> changes = new HashMap<String, List>();
		if (auditLog.getSerializedData() != null) {
			try {
				String serializedStr = getAsString(auditLog.getSerializedData());
				if (StringUtils.isNotBlank(serializedStr)) {
					changes = new ObjectMapper().readValue(serializedStr, Map.class);
				}
			} catch (Exception e) {
				log.warn("Failed to convert serialized data to a map", e);
			}
		}
		return changes;
	}

	/**
	 * Returns a map of property names and values for AuditLogs with action DELETED
	 * 
	 * @param auditLog
	 * @return a map of property names and values
	 */
	public static Map<String, String> getLastStateOfDeletedItem(AuditLog auditLog) {
		if (auditLog.getAction() != DELETED) {
			throw new APIException("Can't call this method for an AuditLog item with action " + auditLog.getAction());
		}

		Map<String, String> changes = new HashMap<String, String>();
		if (auditLog.getSerializedData() != null) {
			try {
				String serializedStr = getAsString(auditLog.getSerializedData());
				if (StringUtils.isNotBlank(serializedStr)) {
					changes = new ObjectMapper().readValue(serializedStr, Map.class);
				}
			} catch (Exception e) {
				log.warn("Failed to convert serialized last state data to a map", e);
			}
		}

		return changes;
	}

	/**
	 * Gets the new property value for the specified property
	 * 
	 * @param propertyName
	 * @param auditLog
	 * @return the new property value if found
	 */
	public static Object getNewValueOfUpdatedItem(String propertyName, AuditLog auditLog) {
		Map<String, List> changes = getChangesOfUpdatedItem(auditLog);
		if (changes.get(propertyName) != null) {
			return (changes.get(propertyName)).get(0);
		}
		return null;
	}

	/**
	 * Gets the old property value for the specified property
	 * 
	 * @param propertyName
	 * @param auditLog
	 * @return the old property value if found
	 */
	public static Object getPreviousValueOfUpdatedItem(String propertyName, AuditLog auditLog) {
		Map<String, List> changes = getChangesOfUpdatedItem(auditLog);
		if (changes.get(propertyName) != null) {
			return (changes.get(propertyName)).get(1);
		}
		return null;
	}

	/**
	 * Gets the CollectionPersister for the collection matching the specified name
	 * in the specified
	 * class
	 * 
	 * @param collPropertyName
	 * @param clazz
	 * @should return the collection persister
	 * @should return the collection persister if the property is declared in a
	 *         super class
	 */
	/**
	 * Gets the CollectionPersister for the collection matching the specified name
	 * in the specified
	 * class
	 * 
	 * DEPRECATED: Use HibernateMetadataUtils.getCollectionPersister() instead.
	 * 
	 * @param collPropertyName
	 * @param clazz
	 * @should return the collection persister
	 * @should return the collection persister if the property is declared in a
	 *         super class
	 */
	@Deprecated
	public static CollectionPersister getCollectionPersister(String collPropertyName, Class<?> clazz,
			SessionFactoryImplementor sfi) {
		return HibernateMetadataUtils.getCollectionPersister(collPropertyName, clazz, sfi);
	}

	public static void setGlobalProperty(String property, String propertyValue) {
		AdministrationService as = Context.getAdministrationService();
		GlobalProperty gp = as.getGlobalPropertyObject(property);
		if (gp == null) {
			gp = new GlobalProperty(property, propertyValue);
		} else {
			gp.setPropertyValue(propertyValue);
		}
		as.saveGlobalProperty(gp);
	}

	public static Class<?> getActualType(Object persistentObject) {
		Class<?> type = persistentObject.getClass();
		if (persistentObject instanceof HibernateProxy) {
			type = ((HibernateProxy) persistentObject).getHibernateLazyInitializer().getPersistentClass();
		}
		return type;
	}

	public static boolean isPersistent(Class<?> clazz) {
		return getClassMetadata(clazz) != null;
	}

	public static ClassMetadata getClassMetadata(Class<?> clazz) {
		return DAOUtils.getClassMetadata(clazz);
	}

	/**
	 * DEPRECATED: Use AuditLogSerializer.getAsString() instead.
	 */
	@Deprecated
	public static String getAsString(Blob blob) throws Exception {
		return AuditLogSerializer.getAsString(blob);
	}

	/**
	 * Serializes the specified object to a String, typically it returns the
	 * object's uuid if it is
	 * an OpenmrsObject, if not it returns the primary key value if it is a
	 * persistent object
	 * otherwise calls toString() method except for Date, Enum and Class objects
	 * that are
	 * handled in a special way.
	 * 
	 * DEPRECATED: Use AuditLogSerializer.serializeObject() instead.
	 * 
	 * @param obj the object to serialize
	 * @return the serialized String form of the object
	 */
	@Deprecated
	public static String serializeObject(Object obj) {
		return AuditLogSerializer.serializeObject(obj);
	}

	/**
	 * Utility method that serializes the collection entries to a string
	 * 
	 * DEPRECATED: Use AuditLogSerializer.serializeCollectionItems() instead.
	 * 
	 * @param collection the Collection object
	 * @return A collection of serialized elements
	 */
	@Deprecated
	public static List<String> serializeCollectionItems(Collection<?> collection) {
		return AuditLogSerializer.serializeCollectionItems(collection);
	}

	/**
	 * Utility method that serializes the map entries to a string
	 * 
	 * DEPRECATED: Use AuditLogSerializer.serializeMapItems() instead.
	 * 
	 * @param map the Map object
	 * @return A map of serialized map keys and values
	 */
	@Deprecated
	public static Map<String, String> serializeMapItems(Map<?, ?> map) {
		return AuditLogSerializer.serializeMapItems(map);
	}

	/**
	 * Utility method that serializes the passed in data to json, this method
	 * assumes all the passed
	 * in data is already serialized
	 * 
	 * DEPRECATED: Use AuditLogSerializer.serializeToJson() instead.
	 * 
	 * @param data the data to serialize
	 * @return the generated json
	 */
	@Deprecated
	public static String serializeToJson(Object data) {
		return AuditLogSerializer.serializeToJson(data);
	}

	/**
	 * Helper method used by AuditLogSerializer to get serialized object from
	 * metadata.
	 * This method is internal and should not be called directly from outside.
	 * 
	 * @param obj the object to serialize from metadata
	 * @return the serialized string or null if not persistent
	 */
	public static String serializeObjectFromMetadata(Object obj) {
		Class<?> clazz = getActualType(obj);
		ClassMetadata metadata = getClassMetadata(clazz);
		if (metadata != null) {
			Serializable id = metadata.getIdentifier(obj, EntityMode.POJO);
			if (id != null) {
				return id.toString();
			}
		}
		return null;
	}
}
