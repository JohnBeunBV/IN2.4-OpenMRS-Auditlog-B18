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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;
import org.openmrs.module.auditlog.AuditLog.Action;

/**
 * Unit tests for {@link AuditLogSerializer}, introduced by the Extract Class
 * refactoring
 * of the serialization methods that previously lived in {@link AuditLogUtil}.
 *
 * <p>
 * <b>Scope note:</b> only scenarios reachable without an active OpenMRS
 * {@code Context}
 * are covered here. {@code serializeObject()} falls back to
 * {@code AuditLogUtil.getClassMetadata()} (which requires a Spring-managed
 * {@code SessionFactory} via {@code Context.getRegisteredComponents()}) for any
 * value
 * that is not a {@code Date}, {@code Enum}, {@code Class}, {@code Collection}
 * or
 * {@code Map} - this includes plain {@code String} values nested inside a
 * collection or
 * map. Those scenarios remain covered by the existing behavior tests
 * ({@code AuditLogBehaviorTest}, {@code CollectionsAuditLogBehaviorTest}),
 * which already
 * run inside a full Spring/Hibernate context. Test inputs below therefore use
 * {@code Date} and enum values, including when nested in collections/maps, so
 * every
 * test here runs as a true isolated unit test.
 * </p>
 */
public class AuditLogSerializerTest {

    private String formatted(Date date) {
        return new SimpleDateFormat(AuditLogConstants.DATE_FORMAT).format(date);
    }

    // -----------------------------------------------------------------------
    // serializeObject()
    // -----------------------------------------------------------------------

    @Test
    public void serializeObject_shouldReturnNullForNullInput() throws Exception {
        assertNull(AuditLogSerializer.serializeObject(null));
    }

    @Test
    public void serializeObject_shouldFormatADateUsingTheConfiguredDateFormat() throws Exception {
        Date date = new Date();

        assertEquals(formatted(date), AuditLogSerializer.serializeObject(date));
    }

    @Test
    public void serializeObject_shouldReturnTheEnumConstantNameForAnEnumValue() throws Exception {
        assertEquals("CREATED", AuditLogSerializer.serializeObject(Action.CREATED));
    }

    @Test
    public void serializeObject_shouldReturnTheClassNameForAClassObject() throws Exception {
        assertEquals("java.lang.String", AuditLogSerializer.serializeObject(String.class));
    }

    @Test
    public void serializeObject_shouldSerializeACollectionOfDatesAsAJsonArray() throws Exception {
        Date date1 = new Date(0);
        Date date2 = new Date();
        List<Date> dates = Arrays.asList(date1, date2);

        String expected = "[\"" + formatted(date1) + "\",\"" + formatted(date2) + "\"]";

        assertEquals(expected, AuditLogSerializer.serializeObject(dates));
    }

    @Test
    public void serializeObject_shouldSerializeAMapWithEnumKeyAndValueAsAJsonObject() throws Exception {
        Map<Action, Action> map = new LinkedHashMap<Action, Action>();
        map.put(Action.CREATED, Action.UPDATED);

        assertEquals("{\"CREATED\":\"UPDATED\"}", AuditLogSerializer.serializeObject(map));
    }

    // -----------------------------------------------------------------------
    // serializeToJson()
    // -----------------------------------------------------------------------

    @Test
    public void serializeToJson_shouldReturnNullForNullInput() throws Exception {
        assertNull(AuditLogSerializer.serializeToJson(null));
    }

    @Test
    public void serializeToJson_shouldConvertAListOfStringsToAJsonArray() throws Exception {
        List<String> data = Arrays.asList("a", "b");

        assertEquals("[\"a\",\"b\"]", AuditLogSerializer.serializeToJson(data));
    }

    @Test
    public void serializeToJson_shouldConvertAnEmptyListToAnEmptyJsonArray() throws Exception {
        assertEquals("[]", AuditLogSerializer.serializeToJson(new ArrayList<String>()));
    }

    // -----------------------------------------------------------------------
    // serializeCollectionItems()
    // -----------------------------------------------------------------------

    @Test
    public void serializeCollectionItems_shouldReturnNullForANullCollection() throws Exception {
        assertNull(AuditLogSerializer.serializeCollectionItems(null));
    }

    @Test
    public void serializeCollectionItems_shouldReturnNullForAnEmptyCollection() throws Exception {
        assertNull(AuditLogSerializer.serializeCollectionItems(new ArrayList<Date>()));
    }

    @Test
    public void serializeCollectionItems_shouldSerializeEachDateInTheCollection() throws Exception {
        Date date1 = new Date(0);
        Date date2 = new Date();

        List<String> result = AuditLogSerializer.serializeCollectionItems(Arrays.asList(date1, date2));

        assertEquals(2, result.size());
        assertEquals(formatted(date1), result.get(0));
        assertEquals(formatted(date2), result.get(1));
    }

    @Test
    public void serializeCollectionItems_shouldSkipNullElementsInsteadOfAddingAPlaceholder() throws Exception {
        Date date = new Date();

        List<String> result = AuditLogSerializer.serializeCollectionItems(Arrays.asList(date, null));

        // the null element is skipped entirely, it is not represented as a null/blank
        // entry
        assertEquals(1, result.size());
        assertEquals(formatted(date), result.get(0));
    }

    // -----------------------------------------------------------------------
    // serializeMapItems()
    // -----------------------------------------------------------------------

    @Test
    public void serializeMapItems_shouldReturnNullForANullMap() throws Exception {
        assertNull(AuditLogSerializer.serializeMapItems(null));
    }

    @Test
    public void serializeMapItems_shouldReturnNullForAnEmptyMap() throws Exception {
        assertNull(AuditLogSerializer.serializeMapItems(new LinkedHashMap<Action, Action>()));
    }

    @Test
    public void serializeMapItems_shouldSerializeAnEnumKeyAndValuePair() throws Exception {
        Map<Action, Action> map = new LinkedHashMap<Action, Action>();
        map.put(Action.CREATED, Action.UPDATED);

        Map<String, String> result = AuditLogSerializer.serializeMapItems(map);

        assertEquals(1, result.size());
        assertEquals("UPDATED", result.get("CREATED"));
    }

    @Test
    public void serializeMapItems_shouldSkipAnEntryWhoseValueSerializesToNull() throws Exception {
        Map<Action, Object> map = new LinkedHashMap<Action, Object>();
        map.put(Action.CREATED, null);

        Map<String, String> result = AuditLogSerializer.serializeMapItems(map);

        // the input map was not empty, so the result is an empty map rather than null,
        // but the entry itself is dropped because its value serialized to null
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }
}