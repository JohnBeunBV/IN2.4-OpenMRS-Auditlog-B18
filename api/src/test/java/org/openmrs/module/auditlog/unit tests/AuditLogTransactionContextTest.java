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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Date;
import java.util.HashMap;

import org.junit.Test;

/**
 * Unit tests for {@link AuditLogTransactionContext}, introduced by the Extract
 * Class
 * refactoring of the nine {@code ThreadLocal<Stack<...>>} fields that
 * previously lived
 * directly on {@code HibernateAuditLogInterceptor}.
 *
 * <p>
 * This class is a pure POJO with no Hibernate or Spring dependency, so it is
 * tested
 * here as a plain JUnit test.
 * </p>
 */
public class AuditLogTransactionContextTest {

	@Test
	public void pushFrame_shouldInitializeEmptyInsertsUpdatesAndDeletes() throws Exception {
		AuditLogTransactionContext ctx = new AuditLogTransactionContext();
		ctx.pushFrame();

		assertNotNull(ctx.currentInserts());
		assertTrue(ctx.currentInserts().isEmpty());
		assertNotNull(ctx.currentUpdates());
		assertTrue(ctx.currentUpdates().isEmpty());
		assertNotNull(ctx.currentDeletes());
		assertTrue(ctx.currentDeletes().isEmpty());
	}

	@Test
	public void currentInserts_shouldAllowAddingEntities() throws Exception {
		AuditLogTransactionContext ctx = new AuditLogTransactionContext();
		ctx.pushFrame();

		Object entity = new Object();
		ctx.currentInserts().add(entity);

		assertEquals(1, ctx.currentInserts().size());
		assertTrue(ctx.currentInserts().contains(entity));
	}

	@Test
	public void currentUpdates_shouldAllowAddingEntities() throws Exception {
		AuditLogTransactionContext ctx = new AuditLogTransactionContext();
		ctx.pushFrame();

		Object entity = new Object();
		ctx.currentUpdates().add(entity);

		assertTrue(ctx.currentUpdates().contains(entity));
	}

	@Test
	public void currentDeletes_shouldAllowAddingEntities() throws Exception {
		AuditLogTransactionContext ctx = new AuditLogTransactionContext();
		ctx.pushFrame();

		Object entity = new Object();
		ctx.currentDeletes().add(entity);

		assertTrue(ctx.currentDeletes().contains(entity));
	}

	@Test
	public void currentDate_shouldReturnTheTimeAtWhichPushFrameWasCalled() throws Exception {
		AuditLogTransactionContext ctx = new AuditLogTransactionContext();

		Date before = new Date();
		ctx.pushFrame();
		Date after = new Date();

		assertNotNull(ctx.currentDate());
		assertFalse(ctx.currentDate().before(before));
		assertFalse(ctx.currentDate().after(after));
	}

	@Test
	public void currentObjectChangesMap_shouldStartEmptyAndBeMutable() throws Exception {
		AuditLogTransactionContext ctx = new AuditLogTransactionContext();
		ctx.pushFrame();

		assertNotNull(ctx.currentObjectChangesMap());
		assertTrue(ctx.currentObjectChangesMap().isEmpty());

		Object entity = new Object();
		ctx.currentObjectChangesMap().put(entity, new HashMap<String, Object[]>());

		assertEquals(1, ctx.currentObjectChangesMap().size());
	}

	@Test
	public void currentCollectionAndChildLogMaps_shouldAllStartEmpty() throws Exception {
		AuditLogTransactionContext ctx = new AuditLogTransactionContext();
		ctx.pushFrame();

		assertNotNull(ctx.currentEntityCollectionsMap());
		assertTrue(ctx.currentEntityCollectionsMap().isEmpty());
		assertNotNull(ctx.currentOwnerChildLogsMap());
		assertTrue(ctx.currentOwnerChildLogsMap().isEmpty());
		assertNotNull(ctx.currentChildObjectAuditLogMap());
		assertTrue(ctx.currentChildObjectAuditLogMap().isEmpty());
		assertNotNull(ctx.currentEntityRemovedChildrenMap());
		assertTrue(ctx.currentEntityRemovedChildrenMap().isEmpty());
	}

	@Test
	public void pushFrame_shouldSupportNestedTransactionsWithoutLeakingStateBetweenFrames() throws Exception {
		AuditLogTransactionContext ctx = new AuditLogTransactionContext();

		ctx.pushFrame();
		Object outerEntity = new Object();
		ctx.currentInserts().add(outerEntity);

		ctx.pushFrame();
		Object innerEntity = new Object();
		ctx.currentInserts().add(innerEntity);
		assertEquals(1, ctx.currentInserts().size());
		assertTrue(ctx.currentInserts().contains(innerEntity));

		ctx.popFrame();

		// the outer frame must be unaffected by what happened in the inner frame
		assertEquals(1, ctx.currentInserts().size());
		assertTrue(ctx.currentInserts().contains(outerEntity));
		assertFalse(ctx.currentInserts().contains(innerEntity));

		ctx.popFrame();
	}

	@Test
	public void currentInserts_shouldFailWhenCalledWithoutAMatchingPushFrame() throws Exception {
		AuditLogTransactionContext ctx = new AuditLogTransactionContext();

		try {
			ctx.currentInserts();
			fail("Expected an exception because no frame was pushed for this thread");
		} catch (Exception e) {
			// expected: the underlying ThreadLocal stack has not been initialized yet
		}
	}

	@Test
	public void currentInserts_shouldFailAfterAllPushedFramesHaveBeenPopped() throws Exception {
		AuditLogTransactionContext ctx = new AuditLogTransactionContext();
		ctx.pushFrame();
		ctx.popFrame();

		try {
			ctx.currentInserts();
			fail("Expected an exception because popFrame() removes the ThreadLocal once empty");
		} catch (Exception e) {
			// expected
		}
	}
}