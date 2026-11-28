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
package org.eclipse.emf.mwe.internal.core.debug.processing.handlers;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.eclipse.emf.mwe.core.debug.model.SyntaxElement;
import org.eclipse.emf.mwe.core.debug.processing.ElementAdapter;
import org.eclipse.emf.mwe.internal.core.debug.communication.Connection;
import org.eclipse.emf.mwe.internal.core.debug.communication.packages.BreakpointPackage;
import org.eclipse.emf.mwe.internal.core.debug.processing.DebugMonitor;
import org.eclipse.emf.mwe.internal.core.debug.processing.ProcessHandler;
import org.eclipse.emf.mwe.internal.core.debug.processing.RuntimeHandler;

/**
 * This class handles the communication of Breakpoints on the runtime side. It
 * listens in an extra thread for set and removal of breakpoints. The
 * <code>DebugMonitor</code> uses this class to suspend the runtime process at
 * breakpoints.
 */
public class BreakpointRuntimeHandler implements RuntimeHandler, ProcessHandler, Runnable {

	private static final Log logger = LogFactory.getLog(BreakpointRuntimeHandler.class);

	public static final int SET = 1;

	public static final int REMOVE = 2;

	protected Connection connection;

	protected DebugMonitor monitor;

	private final List<Object> breakpoints = new ArrayList<Object>();

	private final List<SyntaxElement> breakpointTOs = new ArrayList<SyntaxElement>();

	private final List<SyntaxElement> toBeRemovedTOs = new ArrayList<SyntaxElement>();

	// guards the breakpoint lists: they are modified by the listener thread and by the workflow thread
	private final Object lock = new Object();

	// -------------------------------------------------------------------------

	/**
	 * @see org.eclipse.emf.mwe.internal.core.debug.processing.RuntimeHandler#init(org.eclipse.emf.mwe.internal.core.debug.processing.DebugMonitor,
	 *      org.eclipse.emf.mwe.internal.core.debug.communication.Connection)
	 */
	@Override
	public void init(final DebugMonitor monitor, final Connection connection) {
		this.monitor = monitor;
		this.connection = connection;
		if (monitor != null) {
			monitor.addProcessHandler(this);
		}
	}

	/**
	 * @see org.eclipse.emf.mwe.internal.core.debug.processing.RuntimeHandler#startListener()
	 */
	@Override
	public void startListener() {
		Thread thread = new Thread(this, getClass().getSimpleName());
		thread.setDaemon(true);
		thread.start();
	}

	/**
	 * @see java.lang.Runnable#run()
	 */
	@Override
	public void run() {
		try {
			while (true) {
				final BreakpointPackage packet = (BreakpointPackage) connection.listenForPackage(BreakpointPackage.class);
				try {
					handle(packet);
				}
				catch (final RuntimeException e) {
					// A malformed or not-yet-resolvable breakpoint must not terminate
					// the listener. The confirmation barrier relies on this thread
					// continuing to consume subsequent packets.
					logger.error("Could not handle debugger breakpoint packet", e);
				}
				finally {
					connection.markPackageProcessed(packet);
				}
			}
		}
		catch (IOException e) {
		}
	}

	private void handle(final BreakpointPackage packet) {
		synchronized (lock) {
			switch (packet.type) {
				case SET:
					doSet(packet.se, null, 0, true);
					break;
				case REMOVE:
					doRemove(packet.se, null, 0);
					break;
				default:
					break;
			}
		}
	}

	// -------------------------------------------------------------------------

	private void doSet(final SyntaxElement se, final Object actual, final int flag, final boolean reconcile) {
		ElementAdapter adapter = monitor.getAdapter(se);
		if (adapter == null)
			return;
		Object element = adapter.findElement(se, actual, flag);
		if (reconcile) {
			// a newer SET supersedes pending requests for the same location
			removeMatching(toBeRemovedTOs, se);
			removeMatching(breakpointTOs, se);
		}
		// breakpoints may be set before the syntax element structure is
		// instantiated
		// in this case we store the SyntaxElement and try it again during
		// shallSuspend(...)
		if (element == null) {
			breakpointTOs.add(se);
		}
		else if (!breakpoints.contains(element)) {
			breakpoints.add(element);
		}
	}

	private void doRemove(final SyntaxElement se, final Object actual, final int flag) {
		ElementAdapter adapter = monitor.getAdapter(se);
		if (adapter == null)
			return;
		Object element = adapter.findElement(se, actual, flag);
		removeMatching(breakpointTOs, se);
		if (element == null) {
			removeMatching(toBeRemovedTOs, se);
			toBeRemovedTOs.add(se);
		}
		else {
			breakpoints.remove(element);
		}
	}

	private void removeMatching(final List<SyntaxElement> list, final SyntaxElement se) {
		for (java.util.Iterator<SyntaxElement> it = list.iterator(); it.hasNext();) {
			if (se.equalsBP(it.next())) {
				it.remove();
			}
		}
	}

	// -------------------------------------------------------------------------
	// process listener implementation

	@Override
	public boolean isLastCall() {
		return false;
	}

	/**
	 * returns true if a breakpoint is rgeistered for that element
	 * 
	 * @see org.eclipse.emf.mwe.internal.core.debug.processing.ProcessHandler#shallSuspend(boolean,
	 *      java.lang.Object, int)
	 */
	@Override
	public boolean shallSuspend(final boolean lastState, final Object element, final int flag) {
		synchronized (lock) {
			if (!toBeRemovedTOs.isEmpty()) {
				List<SyntaxElement> temp = new ArrayList<SyntaxElement>();
				temp.addAll(toBeRemovedTOs);
				toBeRemovedTOs.clear();
				for (SyntaxElement se : temp) {
					doRemove(se, element, flag);
				}
			}
			if (!breakpointTOs.isEmpty()) {
				List<SyntaxElement> temp = new ArrayList<SyntaxElement>();
				temp.addAll(breakpointTOs);
				breakpointTOs.clear();
				for (SyntaxElement se : temp) {
					// pending requests are already reconciled, retrying them must stay cheap
					doSet(se, element, flag, false);
				}
			}
			return lastState || breakpoints.contains(element);
		}
	}

	/**
	 * no contribution here
	 * 
	 * @see org.eclipse.emf.mwe.internal.core.debug.processing.ProcessHandler#shallHandle(boolean,
	 *      java.lang.Object, int)
	 */
	@Override
	public boolean shallHandle(final boolean lastState, final Object element, final int flag) {
		return lastState;
	}

	/**
	 * no contribution here
	 * 
	 * @see org.eclipse.emf.mwe.internal.core.debug.processing.ProcessHandler#shallInterrupt(boolean)
	 */
	@Override
	public boolean shallInterrupt(final boolean lastState) {
		return lastState;
	}

}
