/*******************************************************************************
 * Copyright (c) 2026 itemis AG and others.
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.emf.mwe.tests.debug;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.emf.mwe.internal.core.debug.communication.Connection;
import org.eclipse.emf.mwe.internal.core.debug.communication.packages.HandshakePackage;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

public class ConnectionTest extends Assert {

	@Test
	public void connectsToIpv6Loopback() throws Exception {
		final InetAddress ipv6Loopback = InetAddress.getByName("::1");
		Assume.assumeTrue("IPv6 loopback is unavailable", ipv6Loopback.isLoopbackAddress());

		final ServerSocket serverSocket;
		try {
			serverSocket = new ServerSocket(0, 50, ipv6Loopback);
		} catch (final IOException e) {
			Assume.assumeTrue("IPv6 loopback is unavailable: " + e.getMessage(), false);
			return;
		}
		serverSocket.setSoTimeout(10000);

		final AtomicReference<Throwable> serverFailure = new AtomicReference<Throwable>();
		final Thread serverThread = new Thread(() -> serveHandshake(serverSocket, serverFailure), "ConnectionTest server");
		serverThread.setDaemon(true);
		serverThread.start();

		final Connection connection = new Connection();
		try {
			connection.connect(serverSocket.getLocalPort());
			serverThread.join(10000);
			assertFalse("The test server did not complete the handshake", serverThread.isAlive());
			assertNull(serverFailure.get());
		} finally {
			connection.close();
			serverSocket.close();
			serverThread.join(1000);
		}
	}

	@Test
	public void flushDeliversQueuedPacketsBeforeClose() throws Exception {
		final int port;
		try (ServerSocket probe = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
			port = probe.getLocalPort();
		}
		final Connection server = new Connection();
		server.startListeningSocket(port);
		final AtomicReference<Throwable> failure = new AtomicReference<Throwable>();
		final Thread acceptor = new Thread(() -> {
			try {
				server.accept(10000);
			} catch (final Throwable t) {
				failure.set(t);
			}
		});
		acceptor.start();

		final Connection client = new Connection();
		try {
			client.connect(port);
			acceptor.join(10000);
			assertNull(String.valueOf(failure.get()), failure.get());

			final int count = 200;
			for (int i = 0; i < count; i++) {
				client.sendPackage(new org.eclipse.emf.mwe.internal.core.debug.communication.packages.RequireVarPackage(i, 0));
			}
			client.flush(5000);
			client.close();

			for (int i = 0; i < count; i++) {
				final org.eclipse.emf.mwe.internal.core.debug.communication.packages.RequireVarPackage received = (org.eclipse.emf.mwe.internal.core.debug.communication.packages.RequireVarPackage) server
						.listenForPackage(org.eclipse.emf.mwe.internal.core.debug.communication.packages.RequireVarPackage.class);
				assertEquals(i, received.frameId);
			}
		} finally {
			client.close();
			server.close();
		}
	}

	private static void serveHandshake(final ServerSocket serverSocket, final AtomicReference<Throwable> failure) {
		try (Socket socket = serverSocket.accept();
				DataInputStream in = new DataInputStream(socket.getInputStream());
				DataOutputStream out = new DataOutputStream(socket.getOutputStream())) {
			socket.setSoTimeout(10000);
			out.writeUTF(HandshakePackage.class.getName());
			new HandshakePackage().writeContent(out);
			out.flush();
			if (!HandshakePackage.class.getName().equals(in.readUTF())) {
				throw new AssertionError("The client did not send a handshake packet");
			}
		} catch (final Throwable e) {
			failure.set(e);
		}
	}
}
