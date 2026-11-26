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
package org.eclipse.emf.mwe.internal.ui.debug.model;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.core.resources.IMarkerDelta;
import org.eclipse.emf.mwe.core.debug.model.SyntaxElement;
import org.eclipse.core.resources.IWorkspaceRunnable;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.debug.core.DebugException;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.model.IBreakpoint;
import org.eclipse.debug.core.model.IDebugTarget;
import org.eclipse.debug.core.model.IMemoryBlock;
import org.eclipse.debug.core.model.IProcess;
import org.eclipse.debug.core.model.IThread;
import org.eclipse.emf.mwe.internal.core.debug.communication.Connection;
import org.eclipse.emf.mwe.internal.core.debug.model.VarValueTO;
import org.eclipse.emf.mwe.internal.ui.debug.processing.DebugModelManager;
import org.eclipse.emf.mwe.internal.ui.eclipse.launch.MWELaunchConfigurationConstants;
import org.eclipse.emf.mwe.internal.ui.workflow.Activator;
import org.eclipse.emf.mwe.ui.debug.model.MWEBreakpoint;

/**
 * MWE Debug Target implementation<br>
 * It holds the debug threads. There is only one thread in the MWE debug model.<br>
 * It holds also a variable values cache.
 * <br>
 * For Info: A debug target handles the suspend, resume, terminate commands and the breakpoint handling.
 * 
 */
public class DebugTarget extends DebugElement implements IDebugTarget {

	private IProcess fProcess;

	private final Process fSystemProcess;

	private ILaunch fLaunch;

	private DebugThread fThread;

	private DebugModelManager dmm;

	private Map<Integer, DebugValue> valueCache = new HashMap<Integer, DebugValue>();

	protected boolean suspended;

	private final Map<IBreakpoint, SyntaxElement> installedBreakpoints = new java.util.concurrent.ConcurrentHashMap<IBreakpoint, SyntaxElement>();

	public static DebugTarget newDebugTarget(final ILaunch launch, final IProcess process, final Process systemProcess,
			final Connection conn) throws CoreException {
		final DebugTarget[] target = new DebugTarget[1];
		IWorkspaceRunnable r = new IWorkspaceRunnable() {
			@Override
			public void run(IProgressMonitor m) throws DebugException {
				target[0] = new DebugTarget(launch, process, systemProcess, conn);
			}
		};
		try {
			// run as atomic unit of work; fire resource change events only once
			ResourcesPlugin.getWorkspace().run(r, null, 0, null);
		} catch (CoreException e) {
			Activator.logError(e);
			Activator.showError(e.getStatus());
			throw e;
		}
		return target[0];
	}

	private static final long TERMINATE_GRACE_PERIOD_MILLIS = 3000;

	private DebugTarget(ILaunch launch, IProcess process, Process systemProcess, Connection conn) throws DebugException {
		super(null);
		fLaunch = launch;
		target = this;
		fProcess = process;
		fSystemProcess = systemProcess;
		fThread = new DebugThread(this);
		dmm = DebugModelManager.newDebugModelManager(this);
		// the runtime may send events as soon as the manager is initialized
		launch.addDebugTarget(this);
		try {
			dmm.init(conn);
		} catch (RuntimeException | DebugException e) {
			launch.removeDebugTarget(this);
			throw e;
		}

		DebugPlugin.getDefault().getBreakpointManager().addBreakpointListener(this);
	}

	// ******************************************** debug model related elements
	@Override
	public ILaunch getLaunch() {
		return fLaunch;
	}

	@Override
	public IProcess getProcess() {
		return fProcess;
	}

	@Override
	public IThread[] getThreads() {
		final DebugThread thread = fThread;
		return thread == null ? new IThread[0] : new IThread[] { thread };
	}

	public DebugThread getThread() {
		return fThread;
	}

	public void removeThread() {
		fThread = null;
	}

	@Override
	public boolean hasThreads() {
		return fThread != null;
	}

	@Override
	public String getName() {
		try {
			return "Workflow: "
					+ fLaunch.getLaunchConfiguration().getAttribute(
							MWELaunchConfigurationConstants.ATTR_MWE_WORKFLOW_FILE, "");
		} catch (CoreException e) {
			// should not occur
		}
		return "";
	}

	@Override
	public DebugModelManager getDebugModelManager() {
		return dmm;
	}

	public DebugValue getDebugValue(VarValueTO varTO, int frameId) {
		DebugValue value = null;
		int valueId = varTO.valueId;

		if (valueId == 0)
			// don't cache primitives
			value = new DebugValue(this, varTO, frameId);
		else {
			value = valueCache.get(valueId);
			if (value == null) {
				value = new DebugValue(this, varTO, frameId);
				valueCache.put(valueId, value);
			}
			// sub variables are requested in the context of the frame that showed the value last
			value.setFrameId(frameId);
		}
		return value;
	}

	public void updateDebugValues(List<VarValueTO> vars) {
		for (VarValueTO varTO : vars) {
			DebugValue value = valueCache.get(varTO.valueId);
			if (value != null && value.isDirty()) {
				value.setVarTO(varTO);
			}
		}
	}

	// ************************ ISuspendResume implementation and other commands

	@Override
	public boolean canResume() {
		return suspended && !isTerminated();
	}

	@Override
	public boolean canSuspend() {
		return  !suspended && !isTerminated();
	}

	@Override
	public boolean isSuspended() {
		return suspended;
	}

	@Override
	public void resume() throws DebugException {
		dmm.requireResume();
	}

	@Override
	public void suspend() throws DebugException {
		dmm.requireSuspend();
	}

	public void setSuspended(boolean value) {
		suspended = value;
	}

	public void setVariablesDirty() {
		for (DebugValue entry : valueCache.values())
			if (entry != null)
				entry.setDirty(true);
			}

	// ***************** Breakpoint handling, IBreakpointListener implementation

	public void installDeferredBreakpoints() {
		IBreakpoint[] breakpoints = DebugPlugin.getDefault().getBreakpointManager().getBreakpoints(
				MWEBreakpoint.DEBUG_MODEL_ID);
		for (IBreakpoint element : breakpoints) {
			breakpointAdded(element);
		}
	}

	@Override
	public boolean supportsBreakpoint(final IBreakpoint breakpoint) {
		return breakpoint.getModelIdentifier().equals(MWEBreakpoint.DEBUG_MODEL_ID);
	}

	@Override
	public void breakpointAdded(final IBreakpoint breakpoint) {
		if (supportsBreakpoint(breakpoint)) {
			try {
				if (breakpoint.isEnabled()) {
					dmm.requireSetBreakpoint((MWEBreakpoint) breakpoint);
					installedBreakpoints.put(breakpoint, ((MWEBreakpoint) breakpoint).createTO());
				}
			} catch (CoreException e) {
				// in case connection broke
			}
		}
	}

	@Override
	public void breakpointRemoved(final IBreakpoint breakpoint, final IMarkerDelta delta) {
		if (supportsBreakpoint(breakpoint)) {
			try {
				// the marker may already be gone, so prefer the location that was sent to the runtime
				final SyntaxElement installed = installedBreakpoints.remove(breakpoint);
				if (installed != null) {
					dmm.requireRemoveBreakpoint(installed);
				} else {
					final SyntaxElement old = ((MWEBreakpoint) breakpoint).createOldTO(delta);
					dmm.requireRemoveBreakpoint(old != null ? old : ((MWEBreakpoint) breakpoint).createTO());
				}
			} catch (CoreException e) {
				// in case connection broke
			}
		}
	}

	@Override
	public void breakpointChanged(final IBreakpoint breakpoint, final IMarkerDelta delta) {
		if (supportsBreakpoint(breakpoint)) {
			try {
				if (breakpoint.isEnabled()) {
					removeMovedLocation((MWEBreakpoint) breakpoint, delta);
					breakpointAdded(breakpoint);
				} else {
					breakpointRemoved(breakpoint, null);
				}
			} catch (CoreException e) {
				// in case connection broke
			}
		}
	}

	// the runtime knows breakpoints by location, so a moved one would otherwise stay armed at its old place
	private void removeMovedLocation(final MWEBreakpoint breakpoint, final IMarkerDelta delta) throws CoreException {
		final SyntaxElement installed = installedBreakpoints.get(breakpoint);
		final SyntaxElement oldLocation = installed != null ? installed : breakpoint.createOldTO(delta);
		final SyntaxElement newLocation = breakpoint.createTO();
		if (oldLocation != null && !oldLocation.equalsBP(newLocation)) {
			dmm.requireRemoveBreakpoint(oldLocation);
		}
	}

	// *********************************************** ITerminate implementation

	@Override
	public boolean canTerminate() {
		return getProcess().canTerminate();
	}

	@Override
	public boolean isTerminated() {
		return getProcess().isTerminated();
	}

	@Override
	public void terminate() throws DebugException {
		try {
			dmm.requireTerminate();
		} finally {
			// the runtime checks the request only between tasks, so a blocked component would survive it
			scheduleProcessKill();
		}
	}

	private void scheduleProcessKill() {
		final Job job = new Job("Terminate MWE debug process") {
			@Override
			protected IStatus run(final IProgressMonitor monitor) {
				try {
					if (!fProcess.isTerminated()) {
						// IProcess.terminate() only requests a normal destroy, which a hung JVM can ignore
						if (fSystemProcess != null) {
							fSystemProcess.destroyForcibly();
						} else {
							fProcess.terminate();
						}
					}
				} catch (DebugException e) {
					Activator.logError(e);
				}
				return Status.OK_STATUS;
			}
		};
		job.setSystem(true);
		job.schedule(TERMINATE_GRACE_PERIOD_MILLIS);
	}

	// ********************************************** IDisconnect implementation

	@Override
	public boolean canDisconnect() {
		return false;
	}

	@Override
	public void disconnect() {
		// not supported
	}

	@Override
	public boolean isDisconnected() {
		return false;
	}

	// ************************************ IMemoryBlockRetrieval implementation

	@Override
	public boolean supportsStorageRetrieval() {
		return false;
	}

	@Override
	public IMemoryBlock getMemoryBlock(final long startAddress, final long length) {
		return null;
	}

}
