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
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.List;

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

	@Test
	public void concurrentBreakpointUpdatesAreNotLost() throws Exception {
		final int count = 1000;
		final BreakpointRuntimeHandler handler = new BreakpointRuntimeHandler();
		handler.init(new UnresolvedMonitor(), new BulkConnection(count));

		final Thread listener = new Thread(handler::run);
		final Throwable[] failure = new Throwable[1];
		listener.start();
		try {
			while (listener.isAlive()) {
				handler.shallSuspend(false, new Object(), 0);
				Thread.yield();
			}
		}
		catch (final Throwable t) {
			failure[0] = t;
		}
		listener.join();
		assertNull(String.valueOf(failure[0]), failure[0]);

		// not resolvable yet: all breakpoints must still be pending after the last pass
		handler.shallSuspend(false, new Object(), 0);
		final Field pending = BreakpointRuntimeHandler.class.getDeclaredField("breakpointTOs");
		pending.setAccessible(true);
		assertEquals(count, ((List<?>) pending.get(handler)).size());
	}

	private static class BulkConnection extends Connection {
		private final int count;
		private int returned;

		BulkConnection(final int count) {
			this.count = count;
		}

		@Override
		public AbstractPackage listenForPackage(final Class<? extends AbstractPackage> type) throws InterruptedIOException {
			if (returned++ < count) {
				final SyntaxElement se = new SyntaxElement();
				se.line = returned;
				return new BreakpointPackage(BreakpointRuntimeHandler.SET, se);
			}
			throw new InterruptedIOException("test complete");
		}

		@Override
		public void markPackageProcessed(final AbstractPackage packet) {
		}
	}

	private static class ScriptedConnection extends Connection {
		private final java.util.Deque<BreakpointPackage> script = new java.util.ArrayDeque<BreakpointPackage>();

		ScriptedConnection add(final int type) {
			final SyntaxElement se = new SyntaxElement();
			se.resource = "wf.mwe";
			se.line = 3;
			script.add(new BreakpointPackage(type, se));
			return this;
		}

		@Override
		public AbstractPackage listenForPackage(final Class<? extends AbstractPackage> type) throws InterruptedIOException {
			if (script.isEmpty()) {
				throw new InterruptedIOException("test complete");
			}
			return script.poll();
		}

		@Override
		public void markPackageProcessed(final AbstractPackage packet) {
		}
	}

	private static class ResolvingMonitor extends DebugMonitor {
		Object resolved;
		private final ElementAdapter adapter = (ElementAdapter) Proxy.newProxyInstance(
				ElementAdapter.class.getClassLoader(), new Class<?>[]{ElementAdapter.class},
				(proxy, method, args) -> "findElement".equals(method.getName()) ? resolved : null);

		@Override
		public ElementAdapter getAdapter(final Object element) {
			return adapter;
		}
	}

	@Test
	public void repeatedSetIsRemovedByASingleRemove() {
		final Object element = new Object();
		final ResolvingMonitor monitor = new ResolvingMonitor();
		monitor.resolved = element;
		final BreakpointRuntimeHandler handler = new BreakpointRuntimeHandler();
		handler.init(monitor, new ScriptedConnection().add(BreakpointRuntimeHandler.SET)
				.add(BreakpointRuntimeHandler.SET).add(BreakpointRuntimeHandler.REMOVE));
		handler.run();

		assertFalse(handler.shallSuspend(false, element, 0));
	}

	@Test
	public void pendingSetIsCancelledByRemove() {
		final Object element = new Object();
		final ResolvingMonitor monitor = new ResolvingMonitor();
		final BreakpointRuntimeHandler handler = new BreakpointRuntimeHandler();
		handler.init(monitor, new ScriptedConnection().add(BreakpointRuntimeHandler.SET)
				.add(BreakpointRuntimeHandler.REMOVE));
		handler.run();

		monitor.resolved = element;
		assertFalse(handler.shallSuspend(false, element, 0));
	}

	private static class UnresolvedMonitor extends DebugMonitor {
		private final ElementAdapter adapter = (ElementAdapter) Proxy.newProxyInstance(
				ElementAdapter.class.getClassLoader(), new Class<?>[]{ElementAdapter.class}, (proxy, method, args) -> null);

		@Override
		public ElementAdapter getAdapter(final Object element) {
			return adapter;
		}
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
