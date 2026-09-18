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
import java.lang.reflect.Proxy;

import org.eclipse.emf.mwe.core.debug.model.SyntaxElement;
import org.eclipse.emf.mwe.core.debug.processing.ElementAdapter;
import org.eclipse.emf.mwe.internal.core.debug.communication.Connection;
import org.eclipse.emf.mwe.internal.core.debug.communication.packages.AbstractPackage;
import org.eclipse.emf.mwe.internal.core.debug.communication.packages.BreakpointPackage;
import org.eclipse.emf.mwe.internal.core.debug.processing.DebugMonitor;
import org.eclipse.emf.mwe.internal.core.debug.processing.handlers.BreakpointRuntimeHandler;
import org.junit.Assert;
import org.junit.Test;

public class BreakpointRuntimeHandlerTest extends Assert {

	@Test
	public void runtimeFailureDoesNotStopBreakpointListener() {
		final TestConnection connection = new TestConnection();
		final BreakpointRuntimeHandler handler = new BreakpointRuntimeHandler();
		handler.init(new ThrowingMonitor(), connection);

		handler.run();

		assertEquals(1, connection.processedPackets);
	}

	private static class TestConnection extends Connection {
		private final BreakpointPackage packet = new BreakpointPackage(BreakpointRuntimeHandler.SET,
				new SyntaxElement());
		private boolean packetReturned;
		private int processedPackets;

		@Override
		public AbstractPackage listenForPackage(final Class<? extends AbstractPackage> type) throws InterruptedIOException {
			if (!packetReturned) {
				packetReturned = true;
				return packet;
			}
			throw new InterruptedIOException("test complete");
		}

		@Override
		public void markPackageProcessed(final AbstractPackage packet) {
			processedPackets++;
		}
	}

	private static class ThrowingMonitor extends DebugMonitor {
		private final ElementAdapter adapter = (ElementAdapter) Proxy.newProxyInstance(
				ElementAdapter.class.getClassLoader(), new Class<?>[]{ElementAdapter.class}, (proxy, method, args) -> {
					if ("findElement".equals(method.getName())) {
						throw new IllegalStateException("test failure");
					}
					return null;
				});

		@Override
		public ElementAdapter getAdapter(final Object element) {
			return adapter;
		}
	}
}
