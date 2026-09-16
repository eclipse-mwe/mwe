/*******************************************************************************
 * Copyright (c) 2005, 2009 committers of openArchitectureWare and others.
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Clemens Kadura (zAJKa) - Initial API and implementation
 *******************************************************************************/
package org.eclipse.emf.mwe.internal.ui.debug.sourcelookup;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.debug.core.sourcelookup.AbstractSourceLookupParticipant;
import org.eclipse.emf.mwe.internal.ui.debug.model.DebugStackFrame;

/**
 * The source lookup participant knows how to translate a stack frame into a source file name
 */
public class MWESourceLookupParticipant extends AbstractSourceLookupParticipant {

	@Override
	public Object[] findSourceElements(final Object object) throws CoreException {
		final String sourceName = getSourceName(object);
		if (sourceName != null) {
			final IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();
			final IPath path = new Path(sourceName);
			IFile file = path.segmentCount() >= 2 ? root.getFile(path) : null;
			if ((file == null || !file.exists()) && path.isAbsolute()) {
				file = root.getFileForLocation(path);
			}
			if (file != null && file.exists()) {
				return new Object[] { file };
			}
		}
		return super.findSourceElements(object);
	}

	@Override
	public String getSourceName(final Object object) throws CoreException {
		if (object instanceof DebugStackFrame) {
			return ((DebugStackFrame) object).getResource();
		}
		return null;
	}
}
