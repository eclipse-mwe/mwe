/*******************************************************************************
 * Copyright (c) 2026 itemis AG and others.
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.emf.mwe.tests.debug;

import java.lang.reflect.Method;

import org.eclipse.emf.mwe.internal.core.debug.mwe.WorkflowElementAdapter;
import org.junit.Assert;
import org.junit.Test;

public class WorkflowElementAdapterTest {

	@Test
	public void resourceMatchingRequiresPathSegmentBoundary() throws Exception {
		final WorkflowElementAdapter adapter = new WorkflowElementAdapter();
		final Method resourceMatches = WorkflowElementAdapter.class.getDeclaredMethod("resourceMatches", String.class,
				String.class);
		resourceMatches.setAccessible(true);

		Assert.assertTrue(matches(resourceMatches, adapter, "/xxxxx/demo.mwe", "demo.mwe"));
		Assert.assertTrue(matches(resourceMatches, adapter, "demo.mwe", "/xxxxx/demo.mwe"));
		Assert.assertFalse(matches(resourceMatches, adapter, "/xxxxx/mydemo.mwe", "demo.mwe"));
		Assert.assertFalse(matches(resourceMatches, adapter, "myworkflow.mwe", "workflow.mwe"));
	}

	private boolean matches(final Method resourceMatches, final WorkflowElementAdapter adapter, final String requested,
			final String runtime) throws Exception {
		return (Boolean) resourceMatches.invoke(adapter, requested, runtime);
	}

}
