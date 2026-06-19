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
package org.openmrs.module.auditlog;

import static org.junit.Assert.assertNotNull;

import org.junit.Test;

/**
 * Unit tests voor {@link AuditLogActivator}.
 *
 * AuditLogActivator erft van BaseModuleActivator en bevat alleen
 * logging-aanroepen in elke lifecycle-methode. De tests verifiëren dat
 * alle vier de lifecycle-methodes (willStart, started, willStop, stopped)
 * zonder exceptions uitvoeren — dit dekt de volledige codepaden in de klasse
 * inclusief de log.isDebugEnabled()- en log.isInfoEnabled()-branches.
 */
public class AuditLogActivatorTest {

    private final AuditLogActivator activator = new AuditLogActivator();

    @Test
    public void activator_shouldInstantiateSuccessfully() {
        assertNotNull(activator);
    }

    @Test
    public void willStart_shouldNotThrowException() {
        // Dekt de log.isDebugEnabled()-branch en de debug-log aanroep
        activator.willStart();
    }

    @Test
    public void started_shouldNotThrowException() {
        // Dekt de log.isInfoEnabled()-branch en de info-log aanroep
        activator.started();
    }

    @Test
    public void willStop_shouldNotThrowException() {
        // Dekt de log.isDebugEnabled()-branch en de debug-log aanroep
        activator.willStop();
    }

    @Test
    public void stopped_shouldNotThrowException() {
        // Dekt de log.isInfoEnabled()-branch en de info-log aanroep
        activator.stopped();
    }

    @Test
    public void fullLifecycle_shouldExecuteInOrderWithoutException() {
        // Simuleert een volledige module-levenscyclus in volgorde
        activator.willStart();
        activator.started();
        activator.willStop();
        activator.stopped();
    }
}