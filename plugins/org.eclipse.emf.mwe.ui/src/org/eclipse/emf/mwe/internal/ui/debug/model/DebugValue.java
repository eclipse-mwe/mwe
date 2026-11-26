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

import java.util.ArrayList;
import java.util.List;

import org.eclipse.debug.core.DebugException;
import org.eclipse.debug.core.model.IValue;
import org.eclipse.debug.core.model.IVariable;
import org.eclipse.emf.mwe.internal.core.debug.model.VarValueTO;
import org.eclipse.emf.mwe.internal.ui.debug.model.ui.VariableSorter;

/**
 * MWE Debug Value implementation<br>
 * A DebugValue has (beside it's own String representation) a list of sub-variables.
 * 
 */
public class DebugValue extends DebugElement implements IValue {

	private VarValueTO varTO;

	// the stack frame in whose context the sub variables are collected
	private int frameId;

	private boolean dirty;

	private List<DebugVariable> variables = new ArrayList<DebugVariable>();

	// -------------------------------------------------------------------------

	public DebugValue(DebugTarget target, VarValueTO varTO, int frameId) {
		super(target);
		this.varTO = varTO;
		this.frameId = frameId;
	}

	// -------------------------------------------------------------------------

	public void setFrameId(int frameId) {
		this.frameId = frameId;
	}

	public void setVarTO(VarValueTO varTO) {
		this.varTO = varTO;
	}

	public int getVarTOId() {
		return varTO.valueId;
	}

	public boolean isDirty() {
		return dirty;
	}

	public void setDirty(boolean dirty) {
		this.dirty = dirty;
	}

	@Override
	public String getReferenceTypeName() {
		// not used in our debug model
		return "";
	}

	@Override
	public String getValueString() {
		return varTO.simpleRep + (varTO.valueId > 0 ? " (id=" + varTO.valueId + ")" : "");
	}

	public String getDetailRep() {
		return varTO.stringRep;
	}

	@Override
	public boolean isAllocated() {
		return true;
	}

	@Override
	public boolean hasVariables() {
		return varTO.hasMembers;
	}

	@Override
	public synchronized IVariable[] getVariables() throws DebugException {
		if (varTO.hasMembers && variables.isEmpty() || dirty) {
			// require variable value information from runtime process for the first time and after one step
			List<VarValueTO> vars = getDebugModelManager().requireSubVariables(frameId, varTO.valueId);
			if (dirty) {
				// it's an update
				List<DebugVariable> oldVars = new ArrayList<DebugVariable>();
				oldVars.addAll(variables);
				for (VarValueTO varTO : vars) {
					boolean varFound = false;
					for (DebugVariable var : variables) {
						if (var.getName().equals(varTO.name)) {
							varFound = true;
							// every matched variable is still valid, regardless of how it is updated
							oldVars.remove(var);
							// cached values are shared between variables, so a changed value is never updated in place
							if (varTO.valueId == 0 || var.getValue0().getVarTOId() != varTO.valueId) {
								var.setValue(varTO, frameId);
							}
							break;
						}
					}
					if (!varFound) {
						DebugVariable var = new DebugVariable(getDebugTarget0(), varTO, frameId);
						variables.add(var);
					}
				}
				for (DebugVariable var : oldVars)
					// remove remaining obsolete vars
					variables.remove(var);
			} else
				// first time: create new variables
				for (VarValueTO varTO : vars) {
					DebugVariable var = new DebugVariable(getDebugTarget0(), varTO, frameId);
					variables.add(var);
				}
			dirty = false;
			VariableSorter.sort(variables);

		}
		return variables.toArray(new IVariable[0]);
	}

}
