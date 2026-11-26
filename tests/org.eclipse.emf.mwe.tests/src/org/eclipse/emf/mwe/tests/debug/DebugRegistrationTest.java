/*******************************************************************************
 * Copyright (c) 2026 itemis AG and others.
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.emf.mwe.tests.debug;

import java.io.InterruptedIOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;

import org.eclipse.emf.mwe.internal.core.debug.communication.Connection;
import org.eclipse.emf.mwe.internal.core.debug.communication.packages.AbstractPackage;
import org.eclipse.emf.mwe.internal.core.debug.communication.packages.RegisterPackage;
import org.eclipse.emf.mwe.internal.core.debug.communication.packages.RequireVarPackage;
import org.eclipse.emf.mwe.internal.core.debug.processing.DebugMonitor;
import org.eclipse.emf.mwe.internal.core.debug.processing.RuntimeHandlerManager;
import org.junit.Assert;
import org.junit.Test;

public class DebugRegistrationTest extends Assert {

	@Test
	public void unresolvableExtensionClassesDoNotAbortRegistration() throws Exception {
		final Deque<RegisterPackage> script = new ArrayDeque<RegisterPackage>();
		final RegisterPackage handlers = new RegisterPackage();
		handlers.type = RegisterPackage.HANDLERS;
		handlers.classNames.add("does.not.Exist");
		script.add(handlers);
		final RegisterPackage adapters = new RegisterPackage();
		adapters.type = RegisterPackage.ADAPTERS;
		adapters.classNames.add("does.not.ExistEither");
		script.add(adapters);

		final RuntimeHandlerManager manager = new RuntimeHandlerManager(new DebugMonitor());
		manager.setConnection(new Connection() {
			@Override
			public AbstractPackage listenForPackage(final Class<? extends AbstractPackage> type)
					throws InterruptedIOException {
				if (script.isEmpty()) {
					throw new InterruptedIOException("test complete");
				}
				return script.poll();
			}
		});
		manager.run();

		manager.awaitRegistration();
	}

	@Test
	public void missingBuiltInHandlerAbortsRegistration() throws Exception {
		final Deque<RegisterPackage> script = new ArrayDeque<RegisterPackage>();
		final RegisterPackage handlers = new RegisterPackage();
		handlers.type = RegisterPackage.HANDLERS;
		handlers.classNames.add("org.eclipse.emf.mwe.internal.core.debug.processing.handlers.Missing");
		script.add(handlers);

		final RuntimeHandlerManager manager = new RuntimeHandlerManager(new DebugMonitor());
		manager.setConnection(new Connection() {
			@Override
			public AbstractPackage listenForPackage(final Class<? extends AbstractPackage> type)
					throws InterruptedIOException {
				if (script.isEmpty()) {
					throw new InterruptedIOException("test complete");
				}
				return script.poll();
			}
		});
		manager.run();

		try {
			manager.awaitRegistration();
			fail("registration must fail without a built-in handler");
		}
		catch (java.io.IOException expected) {
			// expected
		}
	}

	@Test
	public void packetIdsAreUniqueAcrossThreads() throws Exception {
		final int threads = 8;
		final int perThread = 5000;
		final Set<Integer> ids = java.util.Collections.synchronizedSet(new HashSet<Integer>());
		final CountDownLatch start = new CountDownLatch(1);
		final Thread[] workers = new Thread[threads];
		for (int t = 0; t < threads; t++) {
			workers[t] = new Thread(() -> {
				try {
					start.await();
				}
				catch (final InterruptedException e) {
					return;
				}
				for (int i = 0; i < perThread; i++) {
					ids.add(new RequireVarPackage(0, 0).getId());
				}
			});
			workers[t].start();
		}
		start.countDown();
		for (final Thread worker : workers) {
			worker.join();
		}
		assertEquals(threads * perThread, ids.size());
	}
}
