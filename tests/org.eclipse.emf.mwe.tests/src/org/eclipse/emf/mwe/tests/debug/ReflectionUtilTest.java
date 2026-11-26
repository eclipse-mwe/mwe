/*******************************************************************************
 * Copyright (c) 2026 itemis AG and others.
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.emf.mwe.tests.debug;

import java.util.List;

import org.eclipse.emf.mwe.internal.core.debug.mwe.ReflectionUtil;
import org.junit.Assert;
import org.junit.Test;

public class ReflectionUtilTest extends Assert {

	static class Base {
		private String inherited = "base";
		private String shadowed = "base";
	}

	static class Derived extends Base {
		private String own = "derived";
		private String shadowed = "derived";
	}

	@Test
	public void inheritedFieldsAreCollected() {
		final Derived element = new Derived();
		final List<String> names = ReflectionUtil.getFieldNames(element);

		assertTrue(names.toString(), names.contains("own"));
		assertTrue(names.toString(), names.contains("inherited"));
		assertEquals("base", ReflectionUtil.getFieldValue(element, "inherited"));
	}

	@Test
	public void jdkClassesWithEncapsulatedFieldsDoNotThrow() {
		// must not throw InaccessibleObjectException for JDK types
		assertNotNull(ReflectionUtil.getFieldNames(new java.util.ArrayList<String>()));
		assertNotNull(ReflectionUtil.getFieldNames(Integer.valueOf(1)));
	}

	@Test
	public void subclassFieldWinsOverShadowedSuperclassField() {
		assertEquals("derived", ReflectionUtil.getFieldValue(new Derived(), "shadowed"));
	}

	@Test
	public void primitiveArraysAreInspectable() {
		final int[] array = { 4, 5 };
		assertTrue(ReflectionUtil.checkFields(array));
		assertFalse(ReflectionUtil.checkFields(new int[0]));
		assertEquals(java.util.Arrays.asList("[0]", "[1]"), ReflectionUtil.getFieldNames(array));
		assertEquals("int[2]", ReflectionUtil.getSimpleName(array));
		assertEquals("String[1]", ReflectionUtil.getSimpleName(new String[] { "a" }));
	}

	@Test
	public void failingToStringDoesNotThrow() {
		final Object bad = new Object() {
			@Override
			public String toString() {
				throw new IllegalStateException("boom");
			}
		};
		assertTrue(ReflectionUtil.getNameToString(bad).contains("boom"));
	}

	@Test
	public void hugeRepresentationsAreTransferable() throws Exception {
		final org.eclipse.emf.mwe.internal.core.debug.model.VarValueTO to = new org.eclipse.emf.mwe.internal.core.debug.model.VarValueTO(
				"v");
		final StringBuilder big = new StringBuilder();
		for (int i = 0; i < 70000; i++)
			big.append('\u00e4');
		to.stringRep = big.toString();
		to.simpleRep = "x";
		final java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
		to.writeContent(new java.io.DataOutputStream(bytes));

		final org.eclipse.emf.mwe.internal.core.debug.model.VarValueTO read = new org.eclipse.emf.mwe.internal.core.debug.model.VarValueTO();
		read.readContent(new java.io.DataInputStream(new java.io.ByteArrayInputStream(bytes.toByteArray())));
		assertTrue(read.stringRep.length() < 70000);
	}

	@Test
	public void hugeNamesAndMissingRepresentationsAreTransferable() throws Exception {
		final org.eclipse.emf.mwe.internal.core.debug.model.VarValueTO to = new org.eclipse.emf.mwe.internal.core.debug.model.VarValueTO(
				new String(new char[70000]).replace('\0', 'k'));
		final java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
		to.writeContent(new java.io.DataOutputStream(bytes));

		final org.eclipse.emf.mwe.internal.core.debug.model.VarValueTO read = new org.eclipse.emf.mwe.internal.core.debug.model.VarValueTO();
		read.readContent(new java.io.DataInputStream(new java.io.ByteArrayInputStream(bytes.toByteArray())));
		assertTrue(read.name.length() < 70000);
		assertEquals("null", read.simpleRep);
	}
}
