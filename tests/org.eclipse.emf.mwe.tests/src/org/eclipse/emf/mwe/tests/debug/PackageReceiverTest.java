/*******************************************************************************
 * Copyright (c) 2026 itemis AG and others.
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.emf.mwe.tests.debug;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

import org.eclipse.emf.mwe.internal.core.debug.communication.Connection;
import org.eclipse.emf.mwe.internal.core.debug.communication.PackageReceiver;
import org.eclipse.emf.mwe.internal.core.debug.communication.packages.AbstractPackage;
import org.eclipse.emf.mwe.internal.core.debug.communication.packages.HandshakePackage;
import org.junit.Assert;
import org.junit.Test;

public class PackageReceiverTest extends Assert {

	@Test
	public void closeDoesNotDiscardBufferedPackets() throws Exception {
		final Constructor<PackageReceiver> constructor = PackageReceiver.class.getDeclaredConstructor(Connection.class);
		constructor.setAccessible(true);
		final PackageReceiver receiver = constructor.newInstance(new Connection());

		final Method addPackageToList = PackageReceiver.class.getDeclaredMethod("addPackageToList", AbstractPackage.class);
		addPackageToList.setAccessible(true);
		final HandshakePackage packet = new HandshakePackage();
		addPackageToList.invoke(receiver, packet);

		final Method close = PackageReceiver.class.getDeclaredMethod("close");
		close.setAccessible(true);
		close.invoke(receiver);

		assertSame(packet, receiver.getPackage(HandshakePackage.class, -1));
	}
}
