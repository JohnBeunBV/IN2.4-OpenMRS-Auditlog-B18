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

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collection;

import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hibernate.MappingException;
import org.hibernate.engine.SessionFactoryImplementor;
import org.hibernate.metadata.ClassMetadata;
import org.hibernate.persister.collection.CollectionPersister;
import org.openmrs.module.auditlog.api.db.hibernate.interceptor.InterceptorUtil;

/**
 * Utility class for Hibernate metadata and reflection operations.
 * This class handles reflection operations and Hibernate metadata access.
 * 
 * Responsibility: Hibernate metadata and reflection utilities (Separation of
 * Concerns principle)
 */
public class HibernateMetadataUtils {

    private static final Log log = LogFactory.getLog(HibernateMetadataUtils.class);

    /**
     * Gets the class of the collection elements if the property with the specified
     * name is a collection
     * 
     * @param owningType   the type the collection belongs to
     * @param propertyName the property name of the collection
     * @return the class of the elements of the matching property
     */
    public static Class<?> getCollectionElementType(Class<?> owningType, String propertyName) {
        Field field = getField(owningType, propertyName);
        if (field != null) {
            if (Collection.class.isAssignableFrom(field.getType())) {
                Type type = field.getGenericType();
                if (type instanceof ParameterizedType) {
                    ParameterizedType pt = (ParameterizedType) type;
                    if (!ArrayUtils.isEmpty(pt.getActualTypeArguments())) {
                        return (Class<?>) pt.getActualTypeArguments()[0];
                    }
                }
            }
        } else {
            log.warn("Failed to find property " + propertyName + " in class " + owningType.getName());
        }

        return null;
    }

    /**
     * Convenience method that finds a field with the specified name in the
     * specified class.
     * The method is recursively called to check all superclasses too.
     * 
     * @param clazz     the class to search in
     * @param fieldName the name of the field to find
     * @return the Field if found, null otherwise
     */
    public static Field getField(Class<?> clazz, String fieldName) {
        Field field = null;
        try {
            field = clazz.getDeclaredField(fieldName);
        } catch (Exception e) {
            // check the super classes if any
            if (clazz.getSuperclass() != null) {
                field = getField(clazz.getSuperclass(), fieldName);
            }
        }

        return field;
    }

    /**
     * Gets the CollectionPersister for the collection matching the specified name
     * in the specified class
     * 
     * @param collPropertyName the collection property name
     * @param clazz            the class containing the collection
     * @param sfi              the SessionFactoryImplementor (if null, will be
     *                         retrieved automatically)
     * @return the CollectionPersister for the specified collection
     */
    public static CollectionPersister getCollectionPersister(String collPropertyName, Class<?> clazz,
            SessionFactoryImplementor sfi) {
        if (sfi == null) {
            sfi = (SessionFactoryImplementor) InterceptorUtil.getSessionFactory();
        }
        CollectionPersister cp = null;
        try {
            cp = sfi.getCollectionPersister(clazz.getName() + "." + collPropertyName);
        } catch (MappingException e) {
            // check the super classes if any
            if (clazz.getSuperclass() != null) {
                cp = getCollectionPersister(collPropertyName, clazz.getSuperclass(), sfi);
            }
        }

        return cp;
    }

    /**
     * Gets the ClassMetadata for the specified class
     * 
     * @param clazz the class to get metadata for
     * @return the ClassMetadata or null if not found
     */
    public static ClassMetadata getClassMetadata(Class<?> clazz) {
        return AuditLogUtil.getClassMetadata(clazz);
    }

    /**
     * Checks if the specified class is a persistent class
     * 
     * @param clazz the class to check
     * @return true if the class is persistent (has metadata), false otherwise
     */
    public static boolean isPersistent(Class<?> clazz) {
        return getClassMetadata(clazz) != null;
    }
}
