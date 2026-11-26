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
}
