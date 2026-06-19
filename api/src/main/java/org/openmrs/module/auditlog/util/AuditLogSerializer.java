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
 * Responsible for converting values to serialized String representations.
 *
 * Serialisation of standard Java types (Date, Enum, Class, Collection, Map,
 * primitives/wrappers/String) is fully context-free.
 *
 * Serialisation of Hibernate-managed entities delegates to
 * {@link AuditLogUtil#getClassMetadata(Class)} to retrieve the entity's
 * database identifier — the same behaviour as the original
 * {@code AuditLogUtil.serializeObject()}.
 */
public final class AuditLogSerializer {

	private static final Log log = LogFactory.getLog(AuditLogSerializer.class);

	private static ObjectMapper mapper;

	private AuditLogSerializer() {
	}

	// -------------------------------------------------------------------------
	// Inner strategy interface
	// -------------------------------------------------------------------------

	interface TypeSerializer {

		String serialize(Object obj);
	}

	// -------------------------------------------------------------------------
	// Lookup table: type → serialiser (ordered; first match wins)
	// -------------------------------------------------------------------------

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
				// Use .name() rather than .toString() to guarantee the enum constant
				// value, not an overridden toString() representation.
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

	// -------------------------------------------------------------------------
	// Public API
	// -------------------------------------------------------------------------

	/**
	 * Serializes the specified object to a String.
	 *
	 * <p>
	 * Resolution order:
	 * <ol>
	 * <li>null → null</li>
	 * <li>Date → formatted date string (yyyy-MM-dd HH:mm:ss)</li>
	 * <li>Enum → {@link Enum#name()}</li>
	 * <li>Class → {@link Class#getName()}</li>
	 * <li>Collection → JSON array of serialised elements</li>
	 * <li>Map → JSON object of serialised key-value pairs</li>
	 * <li>Hibernate entity → string form of the database identifier</li>
	 * <li>Anything else → {@link Object#toString()}</li>
	 * </ol>
	 *
	 * @param obj the object to serialize; may be null
	 * @return the serialized string, or null when {@code obj} is null
	 */
	public static String serializeObject(Object obj) {
		if (obj == null) {
			return null;
		}

		Class<?> clazz = AuditLogUtil.getActualType(obj);

		// --- Step 1: try the type-dispatch table ---
		for (Map.Entry<Class<?>, TypeSerializer> entry : TYPE_SERIALIZERS.entrySet()) {
			if (entry.getKey().isAssignableFrom(clazz)) {
				String result = entry.getValue().serialize(obj);
				if (StringUtils.isNotBlank(result)) {
					return result;
				}
				// A match was found but produced a blank result (e.g. empty
				// collection/map). Stop searching the table and fall through to
				// the Hibernate check; the object might still be an entity.
				break;
			}
		}

		// --- Step 2: Hibernate entity → use database identifier ---
		// This mirrors the original AuditLogUtil.serializeObject() behaviour and
		// is essential for correct serialisation of all @Entity-annotated types
		// (ConceptClass, User, ConceptDescription, etc.).
		ClassMetadata metadata = AuditLogUtil.getClassMetadata(clazz);
		if (metadata != null) {
			Serializable id = metadata.getIdentifier(obj, EntityMode.POJO);
			if (id != null) {
				return id.toString();
			}
		}

		// --- Step 3: toString() fallback ---
		return obj.toString();
	}

	/**
	 * Serializes each element of the collection and returns the resulting list.
	 * Returns null for a null or empty collection.
	 *
	 * @param collection the collection to serialize
	 * @return a list of serialized element strings, or null
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
	 * Serializes each key-value pair of the map and returns the resulting map.
	 * Returns null for a null or empty map.
	 *
	 * @param map the map to serialize
	 * @return a map of serialized key-value strings, or null
	 */
	public static Map<String, String> serializeMapItems(Map<?, ?> map) {
		if (MapUtils.isEmpty(map)) {
			return null;
		}

		Map<String, String> result = new HashMap<String, String>(map.size());

		for (Map.Entry<?, ?> entry : map.entrySet()) {
			String key = serializeObject(entry.getKey());
			String value = serializeObject(entry.getValue());
			if (key != null && value != null) {
				result.put(key, value);
			}
		}

		return result;
	}

	/**
	 * Serializes the passed-in data to JSON. Assumes all data is already in its
	 * final serialized form (i.e. a {@code List<String>} or
	 * {@code Map<String, String>}).
	 *
	 * @param data the data to serialize; may be null
	 * @return the generated JSON string, or null when {@code data} is null
	 */
	public static String serializeToJson(Object data) {
		if (data == null) {
			return null;
		}

		try {
			return getMapper().writeValueAsString(data);
		} catch (Exception e) {
			log.error("Failed to generate changes data", e);
			return null;
		}
	}

	// -------------------------------------------------------------------------
	// Private helpers
	// -------------------------------------------------------------------------

	private static ObjectMapper getMapper() {
		if (mapper == null) {
			mapper = new ObjectMapper();
		}
		return mapper;
	}
}