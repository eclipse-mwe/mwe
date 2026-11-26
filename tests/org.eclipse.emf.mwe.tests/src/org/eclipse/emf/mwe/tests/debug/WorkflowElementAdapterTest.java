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
import java.util.Collections;

import org.eclipse.emf.mwe.core.container.CompositeComponent;
import org.eclipse.emf.mwe.core.issues.IssuesImpl;
import org.eclipse.emf.mwe.internal.core.Workflow;
import org.eclipse.emf.mwe.internal.core.ast.util.WorkflowFactory;
import org.eclipse.emf.mwe.internal.core.debug.mwe.WorkflowElementAdapter;
import org.eclipse.emf.mwe.core.debug.model.SyntaxElement;
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

	@Test
	public void breakpointOnFirstLineOfMultiLineTagIsFound() {
		final String resource = "test/res/debug/multiline.mwe";
		final Workflow workflow = new WorkflowFactory().parseInitAndCreate(resource,
				Collections.<String, String>emptyMap(), WorkflowFactory.getDefaultConverter(), new IssuesImpl());
		final WorkflowElementAdapter adapter = new WorkflowElementAdapter();
		adapter.canHandle(workflow.getComponents().get(0));

		final SyntaxElement se = new SyntaxElement();
		se.resource = resource;
		se.line = 2;
		Assert.assertSame(workflow.getComponents().get(0), adapter.findElement(se, null, 0));

		se.line = 3;
		final Object multi = adapter.findElement(se, null, 0);
		Assert.assertSame(workflow.getComponents().get(1), multi);
		Assert.assertEquals(3, adapter.createElement(multi).line);
	}

	@Test
	public void includedWorkflowLocationsUseTheResourcePath() {
		final String resource = "test/res/debug/include.mwe";
		final String included = "test/res/debug/multiline.mwe";
		final Workflow workflow = new WorkflowFactory().parseInitAndCreate(resource,
				Collections.<String, String>emptyMap(), WorkflowFactory.getDefaultConverter(), new IssuesImpl());
		final WorkflowElementAdapter adapter = new WorkflowElementAdapter();
		final CompositeComponent include = (CompositeComponent) workflow.getComponents().get(0);
		adapter.canHandle(include);

		final SyntaxElement end = adapter.createEndElementTO(include);
		Assert.assertEquals(included, end.resource);
		Assert.assertEquals(7, end.line);

		final SyntaxElement se = new SyntaxElement();
		se.resource = "/project/src/" + included;
		se.line = 3;
		Assert.assertSame(include.getComponents().get(1), adapter.findElement(se, null, 0));
	}

	@Test
	public void collectionsAndMapsExposeTheirElements() {
		final WorkflowElementAdapter adapter = new WorkflowElementAdapter();
		final java.util.List<String> list = java.util.Arrays.asList("a", "b");
		Assert.assertTrue(adapter.checkVariableHasMembers(list));
		Assert.assertFalse(adapter.checkVariableHasMembers(new java.util.ArrayList<String>()));
		Assert.assertEquals(2, adapter.getVariables(list).size());
		Assert.assertEquals("[1]", adapter.getVariables(list).get(1).name);

		final java.util.Map<String, Integer> map = java.util.Collections.singletonMap("k", 5);
		Assert.assertTrue(adapter.checkVariableHasMembers(map));
		Assert.assertEquals("k", adapter.getVariables(map).get(0).name);
		Assert.assertEquals(Integer.valueOf(5), adapter.getVariables(map).get(0).value);
	}

	private boolean matches(final Method resourceMatches, final WorkflowElementAdapter adapter, final String requested,
			final String runtime) throws Exception {
		return (Boolean) resourceMatches.invoke(adapter, requested, runtime);
	}

}
