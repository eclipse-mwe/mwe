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
package org.eclipse.emf.mwe.internal.core.debug.communication;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.emf.mwe.internal.core.debug.communication.packages.AbstractPackage;
import org.eclipse.emf.mwe.internal.core.debug.communication.packages.BreakpointPackage;
import org.eclipse.emf.mwe.internal.core.debug.communication.packages.CommandPackage;
import org.eclipse.emf.mwe.internal.core.debug.communication.packages.ConfirmationPackage;
import org.eclipse.emf.mwe.internal.core.debug.communication.packages.EventPackage;
import org.eclipse.emf.mwe.internal.core.debug.communication.packages.EventPackageWithFrames;
import org.eclipse.emf.mwe.internal.core.debug.communication.packages.HandshakePackage;
import org.eclipse.emf.mwe.internal.core.debug.communication.packages.RegisterPackage;
import org.eclipse.emf.mwe.internal.core.debug.communication.packages.RequireVarPackage;
import org.eclipse.emf.mwe.internal.core.debug.communication.packages.VarDataPackage;

/**
 * This class implements a socket connection model. "Packages" (units of information) can be transfered between two
 * different virtual machines. During the initialisation of a socket connection a reader and a writer thread are
 * established. This class can be used both on the sender and receiver side.
 * <p>
 * The debugger wire protocol is intentionally closed: packet class names received from the peer are matched against
 * the built-in packet factory entries instead of being loaded reflectively. Adding a packet type therefore requires
 * updating both communicating runtimes. This restriction applies to packet types only; runtime handlers and element
 * adapters remain extension points and are registered separately.
 */
public class Connection {

	private static final InetAddress LOOPBACK_ADDRESS = InetAddress.getLoopbackAddress();

	private static final int CONNECT_TIMEOUT_MILLIS = 10000;

	private ServerSocket ssocket;

	private Socket socket;

	private DataOutputStream out;

	private DataInputStream in;

	private PackageSender sender;

	private PackageReceiver receiver;

	// -------------------------------------------------------------------------
	// *the* main operation methods

	public AbstractPackage listenForPackage(final Class<? extends AbstractPackage> type) throws InterruptedIOException {
		return receiver.getPackage(type, -1);
	}

	public AbstractPackage listenForPackage(final Class<? extends AbstractPackage> type, final int refId) throws InterruptedIOException {
		return receiver.getPackage(type, refId, -1);
	}

	public void markPackageProcessed(final AbstractPackage packet) {
		if (receiver != null) {
			receiver.markPackageProcessed(packet);
		}
	}

	public void awaitBreakpointPackagesProcessed(final AbstractPackage packet) throws InterruptedIOException {
		if (receiver != null) {
			receiver.awaitBreakpointPackagesProcessed(packet);
		}
	}

	public int sendPackage(final AbstractPackage packet) throws IOException {
		if (sender == null) {
			return -1;
		}
		return sender.sendPackage(packet);
	}

	// -------------------------------------------------------------------------
	// socket communication handling

	/**
	 * create a new <code>ServerSocket</code>.
	 * 
	 * @param port the communication port
	 * @throws IOException
	 */
	public void startListeningSocket(final int port) throws IOException {
		ssocket = new ServerSocket(port, 50, LOOPBACK_ADDRESS);
	}

	/**
	 * the server listens for a client to connect, creates new reader and writer threads and sends a first
	 * handshake packet to test the communication
	 * 
	 * @param timeout
	 * @throws IOException
	 */
	public void accept(final int timeout) throws IOException {
		ssocket.setSoTimeout(timeout);
		// will throw SocketTimeoutException, if nobody connects
		socket = ssocket.accept();
		establishReaderAndWriter();
		if (!writeHandshake()) {
			throw new IOException("handshake failed");
		}
	}

	/**
	 * the client establishes the connection here, creates new reader and writer threads and waits for a handshake
	 * packet to be received from the server
	 * 
	 * @param port
	 * @throws IOException
	 */
	public void connect(final int port) throws IOException {
		socket = connectToLoopback(port);
		establishReaderAndWriter();
		replyHandshake();
	}

	/**
	 * Connects to the debugger on a loopback address of this machine. The listening JVM and this runtime JVM may have
	 * different IPv4/IPv6 preferences, so the loopback address selected by one JVM is not necessarily usable by the
	 * other one.
	 *
	 * @param port the communication port
	 * @return the connected socket
	 * @throws IOException if no loopback address can reach the debugger
	 */
	private Socket connectToLoopback(final int port) throws IOException {
		IOException lastFailure = null;
		for (final InetAddress candidate : loopbackCandidates()) {
			final Socket candidateSocket = new Socket();
			try {
				candidateSocket.connect(new InetSocketAddress(candidate, port), CONNECT_TIMEOUT_MILLIS);
				return candidateSocket;
			} catch (final IOException e) {
				lastFailure = e;
				try {
					candidateSocket.close();
				} catch (final IOException ignored) {
					// Nothing left to do; try the next loopback candidate.
				}
			}
		}

		final ConnectException failure = new ConnectException("Couldn't reach the debugger on loopback port " + port);
		if (lastFailure != null) {
			failure.initCause(lastFailure);
		}
		throw failure;
	}

	private static List<InetAddress> loopbackCandidates() {
		final List<InetAddress> candidates = new ArrayList<InetAddress>();
		addLoopbackCandidate(candidates, LOOPBACK_ADDRESS);
		try {
			for (final InetAddress address : InetAddress.getAllByName("localhost")) {
				addLoopbackCandidate(candidates, address);
			}
		} catch (final UnknownHostException e) {
			// The JVM-selected loopback address above is still usable if localhost is not resolvable.
		}
		// "localhost" may resolve to a single address family, so keep the literals as a last resort.
		addLoopbackCandidate(candidates, literalAddress(new byte[]{127, 0, 0, 1}));
		addLoopbackCandidate(candidates, literalAddress(new byte[]{
				0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1}));
		return candidates;
	}

	private static void addLoopbackCandidate(final List<InetAddress> candidates, final InetAddress address) {
		if ((address != null) && address.isLoopbackAddress() && !candidates.contains(address)) {
			candidates.add(address);
		}
	}

	private static InetAddress literalAddress(final byte[] address) {
		try {
			return InetAddress.getByAddress(address);
		} catch (final UnknownHostException e) {
			return null;
		}
	}

	/**
	 * @return if the socket connection is still active
	 */
	public boolean isConnected() {
		return (socket != null) && !socket.isClosed();
	}

	/**
	 * close and dispose the socket and the (possibly waiting) sender thread
	 */
	public synchronized void close() {
		try {
			if (sender != null) {
				sender.close();
				sender = null;
			}

			if (socket != null) {
				socket.close();
				socket = null;
			}
			
			if(out!=null) { 
				out.close();
				out = null;
			}
			
			if(in!=null) {
				in.close();
				in = null;
			}
			
			if(ssocket != null) {
				ssocket.close();
				ssocket = null;
			}
		} catch (Exception e) {
			// IOExceptions and NullPointerExceptions may occur
		}
	}

	// -------------------------------------------------------------------------
	// the real sending and receiving of packages to be called from PackageReceiver and PackageSender only

	protected AbstractPackage readPackage() throws IOException {
		String className = in.readUTF();
		AbstractPackage packet = instantiatePackage(className);
		packet.readContent(in);
		// System.out.println(Thread.currentThread().getName() + "-RECEIVED-: " + packet);
		return packet;
	}

	
	private AbstractPackage instantiatePackage(final String className) throws IOException {
		if (BreakpointPackage.class.getName().equals(className)) {
			return new BreakpointPackage(0, null);
		}
		if (CommandPackage.class.getName().equals(className)) {
			return new CommandPackage(0);
		}
		if (ConfirmationPackage.class.getName().equals(className)) {
			return new ConfirmationPackage(0);
		}
		if (EventPackage.class.getName().equals(className)) {
			return new EventPackage(0);
		}
		if (EventPackageWithFrames.class.getName().equals(className)) {
			return new EventPackageWithFrames(0);
		}
		if (HandshakePackage.class.getName().equals(className)) {
			return new HandshakePackage();
		}
		if (RegisterPackage.class.getName().equals(className)) {
			return new RegisterPackage();
		}
		if (RequireVarPackage.class.getName().equals(className)) {
			return new RequireVarPackage(0, 0);
		}
		if (VarDataPackage.class.getName().equals(className)) {
			return new VarDataPackage();
		}
		throw new IOException("Unsupported debugger packet class: " + className);
	}

	protected void writePackage(final AbstractPackage packet) throws IOException {
		out.writeUTF(packet.getClass().getName());
		packet.writeContent(out);
		out.flush();
		// System.out.println(Thread.currentThread().getName() + "-SENT-----: " + packet);
	}

	// ********************************************************* private methods

	private void establishReaderAndWriter() throws IOException {
		out = new DataOutputStream(socket.getOutputStream());
		in = new DataInputStream(socket.getInputStream());

		// start receiver and sender in extra threads
		receiver = PackageReceiver.newPackageReceiver(this);
		sender = PackageSender.newPackageSender(this);

	}

	private boolean writeHandshake() throws IOException {
		AbstractPackage packet = new HandshakePackage();
		sendPackage(packet);
		return listenForPackage(HandshakePackage.class).getClass().equals(HandshakePackage.class);
	}

	private void replyHandshake() throws IOException {
		if (listenForPackage(HandshakePackage.class).getClass().equals(HandshakePackage.class)) {
			sendPackage(new HandshakePackage());
		} else {
			throw new IOException("handshake failed");
		}
	}

}
