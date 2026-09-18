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
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.eclipse.emf.mwe.internal.core.debug.communication.packages.AbstractPackage;
import org.eclipse.emf.mwe.internal.core.debug.communication.packages.BreakpointPackage;
import org.eclipse.emf.mwe.internal.core.debug.communication.packages.ConfirmationPackage;

/**
 * This Runnable listens for incoming packages asynchroniously and keeps them, until they are required by
 * customers.
 */
public class PackageReceiver implements Runnable {
	/** Maximum time allowed for an earlier breakpoint packet to be processed. */
	private static final long BREAKPOINT_PROCESSING_TIMEOUT_MILLIS = 10000L;

	private final ArrayList<AbstractPackage> receivedPackages;

	private final Map<AbstractPackage, Long> packetSequences;

	private final Set<Long> pendingBreakpointSequences;

	private long nextPacketSequence;
	
	private static final Log logger = LogFactory.getLog(PackageReceiver.class);

	private final Connection connection;

	private boolean interrupt = false;

	private Exception exception;

	/**
	 * create a new instance and starts the runnable in a new thread
	 * 
	 * @param connection the <code>Connection</code> that controls this data receiver.
	 * @return the instance
	 */
	public static PackageReceiver newPackageReceiver(final Connection connection) {
		PackageReceiver receiver = new PackageReceiver(connection);
		Thread thread = new Thread(receiver, "PackageReceiver");
		thread.setDaemon(true);
		thread.start();
		return receiver;
	}

	private PackageReceiver(final Connection connection) {
		this.connection = connection;
		receivedPackages = new ArrayList<AbstractPackage>();
		packetSequences = new IdentityHashMap<AbstractPackage, Long>();
		pendingBreakpointSequences = new HashSet<Long>();
	}

	/**
	 * Get a packet of the requested type, wait the specified time until it arrives, if time is negative, wait
	 * until socket will be closed
	 * 
	 * @param type the packet type
	 * @param timeToWait until timeout
	 * @return the received packet
	 * @throws InterruptedIOException
	 */
	public AbstractPackage getPackage(final Class<? extends AbstractPackage> type, final long timeToWait) throws InterruptedIOException {
		return getPackage(type, 0, timeToWait);
	}

	/**
	 * Get a packet of the requested type with a specific id, wait the specified time until it arrives, if time is
	 * negative, wait until socket will be closed
	 * 
	 * @param type the packet type
	 * @param refId the id the receiving packet must have
	 * @param timeToWait until timeout
	 * @return the received packet
	 * @throws InterruptedIOException
	 * @throws InterruptedIOException
	 */
	public AbstractPackage getPackage(final Class<? extends AbstractPackage> type, final int refId, final long timeToWait) throws InterruptedIOException {
		AbstractPackage packet = null;

		//sync to be able to wait
		synchronized (receivedPackages) {
			long remainingTime = timeToWait;
			long timeBeforeWait;

			// Wait until type is available.
			while (((packet = popReceivedPackage(type, refId)) == null) && connection.isConnected() && !interrupt
					&& ((timeToWait < 0) || (remainingTime > 0))) {
				timeBeforeWait = System.currentTimeMillis();
				try {
					waitForPackageAvailable(remainingTime);
				} catch (InterruptedException e) {
				}
				if (timeToWait >= 0) {
					remainingTime -= System.currentTimeMillis() - timeBeforeWait;
				}
			}
		}
		checkForException(packet);
		return packet;
	}

	/**
	 * Marks a package as fully handled by the consumer that retrieved it. This is used to preserve the ordering
	 * between breakpoint commands and the startup confirmation without changing the wire protocol.
	 */
	public void markPackageProcessed(final AbstractPackage packet) {
		synchronized (receivedPackages) {
			final Long sequence = packetSequences.get(packet);
			if (sequence != null && packet instanceof BreakpointPackage) {
				pendingBreakpointSequences.remove(sequence);
				packetSequences.remove(packet);
				receivedPackages.notifyAll();
			}
		}
	}

	/**
	 * Waits until every breakpoint packet received before the supplied packet has been processed.
	 */
	public void awaitBreakpointPackagesProcessed(final AbstractPackage packet) throws InterruptedIOException {
		synchronized (receivedPackages) {
			final Long boundary = packetSequences.get(packet);
			if (boundary == null) {
				return;
			}
			final long deadline = System.nanoTime()
					+ TimeUnit.MILLISECONDS.toNanos(BREAKPOINT_PROCESSING_TIMEOUT_MILLIS);
			while (hasPendingBreakpointBefore(boundary) && connection.isConnected() && !interrupt) {
				final long remaining = deadline - System.nanoTime();
				if (remaining <= 0) {
					break;
				}
				try {
					TimeUnit.NANOSECONDS.timedWait(receivedPackages, remaining);
				}
				catch (final InterruptedException e) {
					Thread.currentThread().interrupt();
					throw new InterruptedIOException("Interrupted while waiting for breakpoint processing");
				}
			}
			if (hasPendingBreakpointBefore(boundary)) {
				if (connection.isConnected() && !interrupt) {
					throw new InterruptedIOException("Timed out waiting for breakpoint processing");
				}
				throw new InterruptedIOException("Connection closed while waiting for breakpoint processing");
			}
			packetSequences.remove(packet);
		}
	}

	private boolean hasPendingBreakpointBefore(final long boundary) {
		for (final Long sequence : pendingBreakpointSequences) {
			if (sequence.longValue() < boundary) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Marks the receiver as closed and wakes waiting consumers. Packets that were
	 * received before the close remain available so consumers can drain the queue;
	 * a consumer that cannot find its requested packet receives the close
	 * exception instead.
	 */
	private void close() {
		interrupt = true;
		synchronized (receivedPackages) {
			receivedPackages.notifyAll();
		}
	}

	private void waitForPackageAvailable(final long timeToWait) throws InterruptedException {
		if (timeToWait == 0) {
			return;
		} else if (timeToWait < 0) {
			receivedPackages.wait();
		} else {
			receivedPackages.wait(timeToWait);
		}
	}

	/**
	 * Returns the first packet of the specified type and removes it from the packet list.
	 * The caller must hold the {@code receivedPackages} monitor. Breakpoint and confirmation
	 * packets retain their sequence metadata until their respective consumers process them.
	 */
	private AbstractPackage popReceivedPackage(final Class<? extends AbstractPackage> type, final int refId) {
		for (AbstractPackage packet : receivedPackages) {
			if (!type.isInstance(packet) || ((refId != 0) && (packet.refId != refId))) {
				continue;
			}
			receivedPackages.remove(packet);
			if (!(packet instanceof BreakpointPackage) && !(packet instanceof ConfirmationPackage)) {
				packetSequences.remove(packet);
			}
			return packet;
		}
		return null;
	}

	private void checkForException(final AbstractPackage packet) throws InterruptedIOException {
		if (packet != null) {
			return;
		}
		if ((exception != null) && !(exception instanceof IOException)) {
			// print stack trace if it is not an IOException
			logger.error(exception.getMessage(), exception);
		}
		if (interrupt) {
			throw new InterruptedIOException("packet receiver is going to close");
		}
		if (exception != null) {
			throw new InterruptedIOException(exception.getMessage());
		}
		throw new InterruptedIOException("timeout reading a packet");
	}

	// ******************************************************** runnable methods

	/* (non-Javadoc)
	 * @see java.lang.Runnable#run()
	 */
	@Override
	public void run() {
		try {
			while (!interrupt && connection.isConnected()) {
				readAvailablePackage();
			}
		} catch (Exception e) {
			exception = e;
			close();
		}
	}

	private void readAvailablePackage() throws IOException {
		addPackageToList(connection.readPackage());
	}

	private void addPackageToList(final AbstractPackage packet) {
		synchronized (receivedPackages) {
			final long sequence = ++nextPacketSequence;
			packetSequences.put(packet, Long.valueOf(sequence));
			if (packet instanceof BreakpointPackage) {
				pendingBreakpointSequences.add(Long.valueOf(sequence));
			}
			receivedPackages.add(packet);
			receivedPackages.notifyAll();
		}
	}

}
