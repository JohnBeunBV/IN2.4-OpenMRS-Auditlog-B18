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

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.sql.Blob;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.codehaus.jackson.map.ObjectMapper;
import org.hibernate.proxy.HibernateProxy;

/**
 * Utility class for serializing objects to various formats (String, JSON, etc).
 * This class handles serialization of objects, collections, maps, dates, enums,
 * and classes.
 * 
 * Responsibility: Object serialization (Separation of Concerns principle)
 */
public class AuditLogSerializer {

    private static final Log log = LogFactory.getLog(AuditLogSerializer.class);

    private static ObjectMapper mapper = null;

    private static ObjectMapper getMapper() {
        if (mapper == null) {
            mapper = new ObjectMapper();
        }
        return mapper;
    }

    /**
     * Serializes the specified object to a String.
     * Returns the object's uuid if it is an OpenmrsObject, the primary key value if
     * it is
     * a persistent object, otherwise calls toString() method with special handling
     * for
     * Date, Enum and Class objects.
     * 
     * @param obj the object to serialize
     * @return the serialized String form of the object
     */
    public static String serializeObject(Object obj) {
        String serializedValue = null;
        if (obj != null) {
            Class<?> clazz = AuditLogUtil.getActualType(obj);
            if (Date.class.isAssignableFrom(clazz)) {
                serializedValue = new SimpleDateFormat(AuditLogConstants.DATE_FORMAT).format(obj);
            } else if (Enum.class.isAssignableFrom(clazz)) {
                // Use value.name() over value.toString() to ensure we always get back the enum
                // constant value and not the value returned by the implementation of
                // value.toString()
                serializedValue = ((Enum<?>) obj).name();
            } else if (Class.class.isAssignableFrom(clazz)) {
                serializedValue = ((Class<?>) obj).getName();
            } else if (Collection.class.isAssignableFrom(clazz)) {
                serializedValue = serializeToJson(serializeCollectionItems((Collection) obj));
            } else if (Map.class.isAssignableFrom(clazz)) {
                serializedValue = serializeToJson(serializeMapItems((Map) obj));
            }
            if (StringUtils.isBlank(serializedValue)) {
                serializedValue = AuditLogUtil.serializeObjectFromMetadata(obj);
            }

            if (StringUtils.isBlank(serializedValue)) {
                serializedValue = obj.toString();
            }
        }

        return serializedValue;
    }

    /**
     * Utility method that serializes the collection entries to a string
     * 
     * @param collection the Collection object
     * @return A collection of serialized elements
     */
    public static List<String> serializeCollectionItems(Collection<?> collection) {
        List<String> serializedCollectionItems = null;
        if (CollectionUtils.isNotEmpty(collection)) {
            serializedCollectionItems = new ArrayList<String>(collection.size());
            for (Object collItem : collection) {
                String serializedItem = serializeObject(collItem);
                if (serializedItem != null) {
                    serializedCollectionItems.add(serializedItem);
                }
            }
        }

        return serializedCollectionItems;
    }

    /**
     * Utility method that serializes the map entries to a string
     * 
     * @param map the Map object
     * @return A map of serialized map keys and values
     */
    public static Map<String, String> serializeMapItems(Map<?, ?> map) {
        Map<String, String> serializedMap = null;
        if (MapUtils.isNotEmpty(map)) {
            serializedMap = new HashMap<String, String>(map.size());
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String serializedKey = serializeObject(entry.getKey());
                String serializedValue = serializeObject(entry.getValue());
                if (serializedKey != null && serializedValue != null) {
                    serializedMap.put(serializedKey, serializedValue);
                }
            }
        }

        return serializedMap;
    }

    /**
     * Utility method that serializes the passed in data to JSON.
     * This method assumes all the passed in data is already serialized.
     * 
     * @param data the data to serialize to JSON
     * @return the generated JSON string
     */
    public static String serializeToJson(Object data) {
        String json = null;
        if (data != null) {
            try {
                json = getMapper().writeValueAsString(data);
            } catch (Exception e) {
                log.error("Failed to generate JSON data", e);
            }
        }

        return json;
    }

    /**
     * Converts a Blob to a String
     * 
     * @param blob the Blob to convert
     * @return the String representation of the Blob
     * @throws Exception if an error occurs while reading the Blob
     */
    public static String getAsString(Blob blob) throws Exception {
        BufferedReader br = new BufferedReader(new InputStreamReader(blob.getBinaryStream()));
        StringBuffer sb = new StringBuffer();
        String line;
        while ((line = br.readLine()) != null) {
            sb.append(line);
        }
        return sb.toString();
    }
}
