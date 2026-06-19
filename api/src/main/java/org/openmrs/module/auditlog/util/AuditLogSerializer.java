package org.openmrs.module.auditlog.util;

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

/**
 * Responsible for converting values to serialized String representations.
 *
 * Fully context-free:
 * - no Hibernate access
 * - no OpenMRS Context access
 * - deterministic pure serialization utility
 */
public final class AuditLogSerializer {

	private static final Log log = LogFactory.getLog(AuditLogSerializer.class);

	private static ObjectMapper mapper;

	private AuditLogSerializer() { }

	interface TypeSerializer {
		String serialize(Object obj);
	}

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
				return serializeToJson(
				    serializeCollectionItems((Collection<?>) obj));
			}
		});

		TYPE_SERIALIZERS.put(Map.class, new TypeSerializer() {
			@Override
			public String serialize(Object obj) {
				return serializeToJson(
				    serializeMapItems((Map<?, ?>) obj));
			}
		});
	}

	public static String serializeObject(Object obj) {
		if (obj == null) {
			return null;
		}

		Class<?> clazz = AuditLogUtil.getActualType(obj);

		for (Map.Entry<Class<?>, TypeSerializer> entry : TYPE_SERIALIZERS.entrySet()) {
			if (entry.getKey().isAssignableFrom(clazz)) {
				String result = entry.getValue().serialize(obj);

				if (StringUtils.isNotBlank(result)) {
					return result;
				}

				break;
			}
		}

		// primitives/wrappers/String
		if (isPrimitiveOrWrapper(clazz)
				|| String.class.equals(clazz)) {
			return obj.toString();
		}

		// final fallback
		return obj.toString();
	}

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

	public static Map<String, String> serializeMapItems(Map<?, ?> map) {
		if (MapUtils.isEmpty(map)) {
			return null;
		}

		Map<String, String> result =
		        new HashMap<String, String>(map.size());

		for (Map.Entry<?, ?> entry : map.entrySet()) {
			String key = serializeObject(entry.getKey());
			String value = serializeObject(entry.getValue());

			if (key != null && value != null) {
				result.put(key, value);
			}
		}

		return result;
	}

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

	private static ObjectMapper getMapper() {
		if (mapper == null) {
			mapper = new ObjectMapper();
		}

		return mapper;
	}

	private static boolean isPrimitiveOrWrapper(Class<?> clazz) {
		return clazz.isPrimitive()
				|| Boolean.class.equals(clazz)
				|| Byte.class.equals(clazz)
				|| Character.class.equals(clazz)
				|| Short.class.equals(clazz)
				|| Integer.class.equals(clazz)
				|| Long.class.equals(clazz)
				|| Float.class.equals(clazz)
				|| Double.class.equals(clazz)
				|| Void.class.equals(clazz);
	}	
}