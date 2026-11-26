/*******************************************************************************
 * Copyright (c) 2005 - 2007 committers of openArchitectureWare and others.
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     committers of openArchitectureWare - initial API and implementation
 *******************************************************************************/
package org.eclipse.emf.mwe.internal.core.debug.processing;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.eclipse.emf.mwe.core.debug.processing.ElementAdapter;
import org.eclipse.emf.mwe.core.resources.ResourceLoaderFactory;
import org.eclipse.emf.mwe.internal.core.debug.communication.Connection;
import org.eclipse.emf.mwe.internal.core.debug.communication.packages.RegisterPackage;

/**
 * This manager class listens for requests from the debug server to instantiate
 * handlers or adapters.<br>
 * It initiates the handlers and registers adapters at the
 * <code>DebugMonitor</code> instance.
 */
public class RuntimeHandlerManager implements Runnable {

	private static final long REGISTRATION_TIMEOUT_MILLIS = 20_000L;

	private Connection connection;

	private final DebugMonitor monitor;

	private static final Log logger = LogFactory.getLog(RuntimeHandlerManager.class);

	private final CountDownLatch registrationComplete = new CountDownLatch(1);

	private volatile Exception registrationFailure;

	private volatile boolean registrationSucceeded;

	// -------------------------------------------------------------------------

	public RuntimeHandlerManager(final DebugMonitor monitor) {
		this.monitor = monitor;
	}

	// -------------------------------------------------------------------------

	public void setConnection(final Connection connection) {
		this.connection = connection;
	}

	/**
	 * start listener thread.
	 */
	public void startListener() {
		Thread thread = new Thread(this, getClass().getSimpleName());
		thread.setDaemon(true);
		thread.start();
	}

	/**
	 * Wait until the debugger has registered all runtime handlers and adapters.
	 * The adapter registration packet is sent last by the Eclipse debugger and
	 * therefore acts as the initialization barrier for the existing protocol.
	 * The packet is sent even when no runtime adapters are contributed.
	 *
	 * @throws IOException
	 *             if registration fails or does not complete in time
	 */
	public void awaitRegistration() throws IOException {
		try {
			if (!registrationComplete.await(REGISTRATION_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) {
				throw new IOException("Timed out waiting for debugger handler registration");
			}
		}
		catch (final InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IOException("Interrupted while waiting for debugger handler registration", e);
		}
		if (registrationFailure != null) {
			throw new IOException("Debugger handler registration failed", registrationFailure);
		}
	}

	// -------------------------------------------------------------------------

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Runnable#run()
	 */
	@Override
	public void run() {
		try {
			while (true) {
				listenAndRegisterClasses();
			}
		}
		catch (Exception e) {
			// Once the ADAPTERS packet has opened the barrier, a later socket
			// shutdown is normal and must not turn into a registration failure.
			if (!registrationSucceeded) {
				registrationFailure = e;
				registrationComplete.countDown();
			}
			if (!(e instanceof IOException)) {
				logger.error(e.getMessage(), e);
			}
		}
	}

	private void listenAndRegisterClasses() throws InstantiationException, IllegalAccessException,
			ClassNotFoundException, IOException, IllegalArgumentException, InvocationTargetException, NoSuchMethodException, SecurityException {

		RegisterPackage packet = (RegisterPackage) connection.listenForPackage(RegisterPackage.class);

		if (packet.type == RegisterPackage.HANDLERS) {
			for (String className : packet.classNames) {
				final RuntimeHandler handler = createInstance(className, RuntimeHandler.class);
				if (handler != null) {
					handler.init(monitor, connection);
					handler.startListener();
				}
			}
		}
		else if (packet.type == RegisterPackage.ADAPTERS) {
			for (String className : packet.classNames) {
				final ElementAdapter adapter = createInstance(className, ElementAdapter.class);
				if (adapter != null) {
					monitor.addAdapter(adapter);
				}
			}
			registrationSucceeded = true;
			registrationComplete.countDown();
		}
		else {
			throw new IOException("Unknown debugger registration packet type: " + packet.type);
		}
	}

	// handlers and adapters contributed by extensions are optional: one that can't be loaded must not abort the
	// debug session, otherwise the registration barrier would never open
	private <T> T createInstance(final String className, final Class<T> type) {
		try {
			final Class<?> clazz = ResourceLoaderFactory.createResourceLoader().loadClass(className);
			if (clazz == null) {
				throw new ClassNotFoundException("Couldn't find " + className + " in the class path.");
			}
			return type.cast(clazz.getDeclaredConstructor().newInstance());
		}
		catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
			logger.error("Ignoring debugger extension " + className + ": " + e, e);
			return null;
		}
	}

}
