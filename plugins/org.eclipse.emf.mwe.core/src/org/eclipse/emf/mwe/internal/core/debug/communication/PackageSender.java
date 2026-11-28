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
package org.eclipse.emf.mwe.internal.core.debug.communication;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.ArrayList;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.eclipse.emf.mwe.internal.core.debug.communication.packages.AbstractPackage;

/**
 * This Runnable sends packages out asynchroniously as they arrive from customers.
 */
public class PackageSender implements Runnable {

	private final ArrayList<AbstractPackage> outgoingPackages;
	
	private static final Log logger = LogFactory.getLog(PackageSender.class);

	private final Connection connection;

	private volatile boolean interrupt = false;

	// guarded by outgoingPackages: packets were taken from the queue but are not written yet
	private boolean writing = false;

	/**
	 * create a new instance and starts the runnable in a new thread
	 * 
	 * @param connection the <code>Connection</code> that controls this data receiver.
	 * @return the instance
	 */
	public static PackageSender newPackageSender(final Connection connection) {
		PackageSender sender = new PackageSender(connection);
		Thread thread = new Thread(sender, "PackageSender");
		thread.setDaemon(true);
		thread.start();
		return sender;
	}

	private PackageSender(final Connection connection) {
		this.connection = connection;
		outgoingPackages = new ArrayList<AbstractPackage>();
	}

	/**
	 * Add a packet to be sent to the other side.
	 * 
	 * @param packet the packet
	 * @return the packet id after it was sent
	 * @throws InterruptedIOException
	 */
	public int sendPackage(final AbstractPackage packet) throws InterruptedIOException {
		if (!connection.isConnected()) {
			throw new InterruptedIOException();
		// log.debug("send: " + packet);
		}

		synchronized (outgoingPackages) {
			outgoingPackages.add(packet);
			outgoingPackages.notifyAll();
		}
		return packet.getId();
	}

	/**
	 * Wait until all queued packets have been written, e.g. before closing the connection.
	 * 
	 * @param timeoutMillis the maximum time to wait
	 */
	public void awaitSent(final long timeoutMillis) {
		final long deadline = System.currentTimeMillis() + timeoutMillis;
		synchronized (outgoingPackages) {
			while ((!outgoingPackages.isEmpty() || writing) && !interrupt) {
				final long remaining = deadline - System.currentTimeMillis();
				if (remaining <= 0) {
					return;
				}
				try {
					outgoingPackages.wait(remaining);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					return;
				}
			}
		}
	}

	/**
	 * stop the sender thread.
	 */
	public void close() {
		interrupt = true;
		synchronized (outgoingPackages) {
			outgoingPackages.notifyAll();
		}
	}

	// ******************************************************** runnable methods

	/* (non-Javadoc)
	 * @see java.lang.Runnable#run()
	 */
	@Override
	public void run() {
		while (!interrupt && connection.isConnected()) {
			try {
				sendAvailablePackages();
			} catch (Exception e) {
				if (!(e instanceof IOException)) {
					logger.error(e.getMessage(), e);
				}
				interrupt = true;
			}
		}
		connection.close();
	}

	private void sendAvailablePackages() throws IOException {
		ArrayList<AbstractPackage> packagesToSend = new ArrayList<AbstractPackage>();
		synchronized (outgoingPackages) {
			while (!interrupt && outgoingPackages.isEmpty()) {
				try {
					outgoingPackages.wait();
				} catch (InterruptedException e) {
				  interrupt = true;
				}
			}
			packagesToSend.addAll(outgoingPackages);
			outgoingPackages.clear();
			writing = !packagesToSend.isEmpty();
		}

		try {
			for (AbstractPackage packet : packagesToSend) {
				connection.writePackage(packet);
			}
		}
		finally {
			synchronized (outgoingPackages) {
				writing = false;
				outgoingPackages.notifyAll();
			}
		}
	}

}
