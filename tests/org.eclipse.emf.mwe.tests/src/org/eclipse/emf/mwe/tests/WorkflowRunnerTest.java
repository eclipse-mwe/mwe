/*******************************************************************************
 * Copyright (c) 2007, 2026 itemis AG (http://www.itemis.eu) and others.
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.emf.mwe.tests;

import java.util.Collections;
import java.util.Map;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.ParseException;
import org.eclipse.emf.mwe.core.WorkflowEngine;
import org.eclipse.emf.mwe.core.WorkflowContext;
import org.eclipse.emf.mwe.core.WorkflowRunner;
import org.eclipse.emf.mwe.core.monitor.NullProgressMonitor;
import org.eclipse.emf.mwe.core.monitor.ProgressMonitor2;
import org.junit.Assert;
import org.junit.Test;

public class WorkflowRunnerTest extends Assert {
	@Test public void testBug155854 () throws Exception {
		WorkflowEngine runner = new WorkflowEngine();
		try {
			boolean result = runner.prepare("invalidfile.mwe", new NullProgressMonitor(), Collections.<String,String>emptyMap());
			assertFalse ("prepare() should indicate an error", result);
		} catch (IllegalArgumentException exc) {
			assertFalse ("Should not get this error as an IllegalArgumentException", exc.getMessage().startsWith("Couldn't load"));
		} 
		
	}
	
	@Test public void testSeveralParams() throws Exception {
		try {
			WorkflowRunner.main(new String[]{"test/res/complex/aCompWithSeveralParams.mwe", "-p", "s=hallo", "-p", "i=123"});
		} catch (Exception e) {
			e.printStackTrace();
			fail("no errors expected");
		}

	}

	@Test public void testParamValueEndingWithWorkflowExtension() throws Exception {
		WorkflowRunner.main(new String[]{
				"test/res/complex/aCompWithSeveralParams.mwe",
				"-p", "s=hallo",
				"-p", "i=123",
				"-p", "modelFile=input.mwe"});
	}

	@Test public void testWorkflowFileAfterMonitorOption() throws Exception {
		RecordingProgressMonitor.lastArgs = null;
		final String[] args = new String[]{
				"-m", RecordingProgressMonitor.class.getName(),
				"monitor-arg",
				"test/res/complex/aCompWithSeveralParams.mwe",
				"-p", "s=hallo",
				"-p", "i=123"};

		assertTrue(new WorkflowRunner().doRun(TestWorkflowRunner.parse(args), args));
		assertArrayEquals(new String[]{RecordingProgressMonitor.class.getName(), "monitor-arg"},
				RecordingProgressMonitor.lastArgs);
		assertFalse(contains(RecordingProgressMonitor.lastArgs, "test/res/complex/aCompWithSeveralParams.mwe"));
	}

	@Test public void testCmdLineProcessorWithWorkflowAfterMonitorOption() throws Exception {
		RecordingProgressMonitor.lastArgs = null;
		RecordingCommandLineProcessor.lastArgs = null;
		final String[] args = new String[]{
				"-c", RecordingCommandLineProcessor.class.getName(),
				"-m", RecordingProgressMonitor.class.getName(),
				"monitor-arg",
				"test/res/complex/aCompWithSeveralParams.mwe",
				"-p", "s=hallo",
				"-p", "i=123"};

		assertTrue(new WorkflowRunner().doRun(TestWorkflowRunner.parse(args), args));
		assertArrayEquals(new String[]{"-p", "s=hallo", "-p", "i=123"}, RecordingCommandLineProcessor.lastArgs);
		assertArrayEquals(new String[]{RecordingProgressMonitor.class.getName(), "monitor-arg"},
				RecordingProgressMonitor.lastArgs);
	}

	@Test public void testCmdLineProcessorWithParamValueEndingWithWorkflowExtension() throws Exception {
		RecordingCommandLineProcessor.lastArgs = null;
		final String[] args = new String[]{
				"test/res/complex/aCompWithSeveralParams.mwe",
				"-c", RecordingCommandLineProcessor.class.getName(),
				"-p", "modelFile=input.mwe"};

		assertTrue(new WorkflowRunner().doRun(TestWorkflowRunner.parse(args), args));
		assertArrayEquals(new String[]{"-p", "modelFile=input.mwe"}, RecordingCommandLineProcessor.lastArgs);
	}

	@Test public void testCmdLineProcessorWithDashPrefixedMonitorArgument() throws Exception {
		RecordingProgressMonitor.lastArgs = null;
		RecordingCommandLineProcessor.lastArgs = null;
		final String[] args = new String[]{
				"test/res/complex/aCompWithSeveralParams.mwe",
				"-c", RecordingCommandLineProcessor.class.getName(),
				"-m", RecordingProgressMonitor.class.getName(), "-verbose",
				"-p", "s=hallo"};

		assertTrue(new WorkflowRunner().doRun(TestWorkflowRunner.parse(args), args));
		assertArrayEquals(new String[]{"-p", "s=hallo"}, RecordingCommandLineProcessor.lastArgs);
		assertArrayEquals(new String[]{RecordingProgressMonitor.class.getName(), "-verbose"},
				RecordingProgressMonitor.lastArgs);
	}

	@Test public void testCmdLineProcessorWithOwnPositionalArgument() throws Exception {
		RecordingCommandLineProcessor.lastArgs = null;
		final String[] args = new String[]{
				"-c", RecordingCommandLineProcessor.class.getName(),
				"extra", "test/res/complex/aCompWithSeveralParams.mwe"};

		assertTrue(new WorkflowRunner().doRun(TestWorkflowRunner.parse(args), args));
		assertArrayEquals(new String[]{"extra"}, RecordingCommandLineProcessor.lastArgs);
	}

	@Test public void testCmdLineProcessorWithCommaSeparatedMonitorValues() throws Exception {
		RecordingProgressMonitor.lastArgs = null;
		RecordingCommandLineProcessor.lastArgs = null;
		final String[] args = new String[]{
				"-c", RecordingCommandLineProcessor.class.getName(),
				"extra", "-m", RecordingProgressMonitor.class.getName() + ",monitor-arg",
				"test/res/complex/aCompWithSeveralParams.mwe",
				"-p", "s=hallo"};

		assertTrue(new WorkflowRunner().doRun(TestWorkflowRunner.parse(args), args));
		assertArrayEquals(new String[]{"extra", "-p", "s=hallo"}, RecordingCommandLineProcessor.lastArgs);
		assertArrayEquals(new String[]{RecordingProgressMonitor.class.getName(), "monitor-arg"},
				RecordingProgressMonitor.lastArgs);
	}

	@Test public void testMonitorOptionRequiresMonitorClass() throws Exception {
		final String[] args = new String[]{"-m", "test/res/complex/aCompWithSeveralParams.mwe"};
		final TestWorkflowRunner runner = new TestWorkflowRunner();

		assertFalse(runner.doRun(TestWorkflowRunner.parse(args), args));
		assertTrue(runner.wrongCall);
	}

	private boolean contains(final String[] args, final String value) {
		for (final String arg : args) {
			if (value.equals(arg)) {
				return true;
			}
		}
		return false;
	}

	public static class RecordingProgressMonitor extends NullProgressMonitor implements ProgressMonitor2 {
		private static String[] lastArgs;

		public RecordingProgressMonitor() {
		}

		@Override
		public void init(final String[] args) {
			lastArgs = args.clone();
		}
	}

	public static class RecordingCommandLineProcessor {
		private static String[] lastArgs;

		public RecordingCommandLineProcessor() {
		}

		@SuppressWarnings("rawtypes")
		public void processCmdLine(final String[] args, final Map params, final WorkflowContext context) {
			lastArgs = args.clone();
			params.put("s", "hallo");
			params.put("i", "123");
		}
	}

	private static class TestWorkflowRunner extends WorkflowRunner {
		private boolean wrongCall;

		private static CommandLine parse(final String[] args) throws ParseException {
			return getCommandLine(args);
		}

		@Override
		protected void wrongCall(final CommandLine line) {
			wrongCall = true;
		}
	}

}
