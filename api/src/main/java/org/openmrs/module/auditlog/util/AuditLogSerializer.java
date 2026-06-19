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

import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.codehaus.jackson.map.ObjectMapper;
import org.hibernate.EntityMode;
import org.hibernate.metadata.ClassMetadata;

/**
 * Responsible for converting OpenMRS/Hibernate objects to their serialized String representation
 * for storage in {@link org.openmrs.module.auditlog.AuditLog#serializedData}.
 *
 * <p><b>Refactoring: Extract Class</b> – serialization methods were extracted from
 * {@link AuditLogUtil} (which now retains only reflection/metadata helpers). This splits
 * two unrelated responsibilities that previously lived in the same class.</p>
 *
 * <p><b>Refactoring: Strategy Pattern via lookup table (Map)</b> –
 * {@link #serializeObject(Object)} previously used a chain of {@code if/else if} branches
 * to dispatch on type. Each branch is now a {@link TypeSerializer} entry in
 * {@link #TYPE_SERIALIZERS}. This is a lightweight, registry-based application of the
 * Strategy Pattern, consistent with the existing {@code AuditStrategy} hierarchy in the
 * {@code strategy} package. Separate classes per type would be over-engineering given
 * the small, stable set of variants.</p>
 *
 * <p>Note: uses anonymous inner classes instead of lambdas to stay compatible with
 * Java 7 / {@code -source 1.7} as required by the module POM.</p>
 *
 * <p>Public API is identical to the methods that were in {@code AuditLogUtil}, so
 * existing callers only need an import change.</p>
 */
public final class AuditLogSerializer {

	private static final Log log = LogFactory.getLog(AuditLogSerializer.class);

	private static ObjectMapper mapper;

	private AuditLogSerializer() { /* static utility class */ }

	// -----------------------------------------------------------------------
	// Strategy interface — Java-7-compatible alternative to Function<Object,String>
	// -----------------------------------------------------------------------

	/**
	 * Single-method interface that converts an object to its serialized String form.
	 * Implemented as anonymous inner classes below so the code compiles under -source 1.7.
	 */
	interface TypeSerializer {
		String serialize(Object obj);
	}

	// -----------------------------------------------------------------------
	// REFACTORING: Strategy Pattern via lookup table
	//
	// Original code had a chain of if/else-if blocks in serializeObject():
	//
	//   if (Date.class.isAssignableFrom(clazz)) { ... }
	//   else if (Enum.class.isAssignableFrom(clazz)) { ... }
	//   else if (Class.class.isAssignableFrom(clazz)) { ... }
	//   else if (Collection.class.isAssignableFrom(clazz)) { ... }
	//   else if (Map.class.isAssignableFrom(clazz)) { ... }
	//
	// Each entry in TYPE_SERIALIZERS couples a super-type to an interchangeable
	// TypeSerializer, mirroring the design of AuditStrategy / AllAuditStrategy /
	// NoneAuditStrategy in the strategy package.
	//
	// LinkedHashMap preserves insertion order so more-specific types can be placed
	// before broader supertypes if needed in the future.
	// -----------------------------------------------------------------------
	@SuppressWarnings({ "rawtypes", "unchecked" })
	private static final Map<Class<?>, TypeSerializer> TYPE_SERIALIZERS;

	static {
		TYPE_SERIALIZERS = new LinkedHashMap<Class<?>, TypeSerializer>();

		TYPE_SERIALIZERS.put(Date.class, new TypeSerializer() {
			@Override
			public String serialize(Object obj) {
				return new SimpleDateFormat(AuditLogConstants.DATE_FORMAT).format(obj);
			}
		});

		TYPE_SERIALIZERS.put(Enum.class, new TypeSerializer() {
			@Override
			public String serialize(Object obj) {
				// Use .name() over .toString() to always get the enum constant,
				// not the value returned by a custom toString() implementation.
				return ((Enum<?>) obj).name();
			}
		});

		TYPE_SERIALIZERS.put(Class.class, new TypeSerializer() {
			@Override
			public String serialize(Object obj) {
				return ((Class<?>) obj).getName();
			}
		});

		TYPE_SERIALIZERS.put(Collection.class, new TypeSerializer() {
			@Override
			public String serialize(Object obj) {
				return serializeToJson(serializeCollectionItems((Collection<?>) obj));
			}
		});

		TYPE_SERIALIZERS.put(Map.class, new TypeSerializer() {
			@Override
			public String serialize(Object obj) {
				return serializeToJson(serializeMapItems((Map<?, ?>) obj));
			}
		});
	}

	// -----------------------------------------------------------------------
	// Public API
	// -----------------------------------------------------------------------

	/**
	 * Serializes {@code obj} to a String for storage in an AuditLog.
	 *
	 * <p>Dispatch order (via {@link #TYPE_SERIALIZERS}):</p>
	 * <ol>
	 *   <li>Date        → formatted with {@link AuditLogConstants#DATE_FORMAT}</li>
	 *   <li>Enum        → {@code .name()}</li>
	 *   <li>Class       → {@code .getName()}</li>
	 *   <li>Collection  → JSON array of serialized elements</li>
	 *   <li>Map         → JSON object of serialized keys/values</li>
	 *   <li>Persistent entity → Hibernate identifier as String</li>
	 *   <li>Fallback    → {@code .toString()}</li>
	 * </ol>
	 *
	 * @param obj the value to serialize; may be {@code null}
	 * @return serialized string, or {@code null} if {@code obj} is {@code null}
	 */
	public static String serializeObject(Object obj) {
		if (obj == null) {
			return null;
		}

		Class<?> clazz = AuditLogUtil.getActualType(obj);

		// Walk the strategy registry
		for (Map.Entry<Class<?>, TypeSerializer> entry : TYPE_SERIALIZERS.entrySet()) {
			if (entry.getKey().isAssignableFrom(clazz)) {
				String result = entry.getValue().serialize(obj);
				if (StringUtils.isNotBlank(result)) {
					return result;
				}
				// Strategy produced blank — fall through to Hibernate id / toString
				break;
			}
		}

		// Persistent entity: use Hibernate identifier
		ClassMetadata metadata = AuditLogUtil.getClassMetadata(clazz);
		if (metadata != null) {
			Serializable id = metadata.getIdentifier(obj, EntityMode.POJO);
			if (id != null) {
				return id.toString();
			}
		}

		// Last-resort fallback
		return obj.toString();
	}

	/**
	 * Serializes each element of a {@link Collection} using {@link #serializeObject(Object)}.
	 *
	 * @param collection the collection to serialize; may be {@code null} or empty
	 * @return list of serialized element strings, or {@code null} if collection is empty/null
	 */
	public static List<String> serializeCollectionItems(Collection<?> collection) {
		if (CollectionUtils.isEmpty(collection)) {
			return null;
		}
		List<String> result = new ArrayList<String>(collection.size());
		for (Object item : collection) {
			String serialized = serializeObject(item);
			if (serialized != null) {
				result.add(serialized);
			}
		}
		return result;
	}

	/**
	 * Serializes each key and value of a {@link Map} using {@link #serializeObject(Object)}.
	 *
	 * @param map the map to serialize; may be {@code null} or empty
	 * @return map of serialized key-value pairs, or {@code null} if the map is empty/null
	 */
	public static Map<String, String> serializeMapItems(Map<?, ?> map) {
		if (MapUtils.isEmpty(map)) {
			return null;
		}
		Map<String, String> result = new HashMap<String, String>(map.size());
		for (Map.Entry<?, ?> entry : map.entrySet()) {
			String key   = serializeObject(entry.getKey());
			String value = serializeObject(entry.getValue());
			if (key != null && value != null) {
				result.put(key, value);
			}
		}
		return result;
	}

	/**
	 * Converts already-serialized data (a Map, List, or other Jackson-compatible object)
	 * to a JSON string.
	 *
	 * @param data the data to convert; may be {@code null}
	 * @return JSON string, or {@code null} if {@code data} is {@code null}
	 */
	public static String serializeToJson(Object data) {
		if (data == null) {
			return null;
		}
		try {
			return getMapper().writeValueAsString(data);
		}
		catch (Exception e) {
			log.error("Failed to generate changes data", e);
			return null;
		}
	}

	// -----------------------------------------------------------------------
	// Private helpers
	// -----------------------------------------------------------------------

	private static ObjectMapper getMapper() {
		if (mapper == null) {
			mapper = new ObjectMapper();
		}
		return mapper;
	}
}
