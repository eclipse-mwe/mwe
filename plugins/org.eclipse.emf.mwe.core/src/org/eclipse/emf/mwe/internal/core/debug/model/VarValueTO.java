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
package org.eclipse.emf.mwe.internal.core.debug.model;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Transfer object class for variable description in a normalized form.
 */
public class VarValueTO {

	public String name;

	public int valueId;

	public String stringRep;

	public String simpleRep;

	public boolean hasMembers;

	// -------------------------------------------------------------------------

	public VarValueTO() {
	}

	public VarValueTO(String name) {
		this.name = name;
	}

	// -------------------------------------------------------------------------

	public void writeContent(DataOutputStream out) throws IOException {
		out.writeUTF(name);
		out.writeInt(valueId);
		out.writeUTF(limit(stringRep));
		if (simpleRep == null) {
			System.out.println("simpleRep of " + stringRep + " is null.");
//			simpleRep = "null";
		}
		out.writeUTF(limit(simpleRep));
		out.writeBoolean(hasMembers);
	}

	// writeUTF accepts at most 65535 encoded bytes and a character takes up to 3 of them
	static final int MAX_REP_LENGTH = 20000;

	private static String limit(final String value) {
		if (value == null || value.length() <= MAX_REP_LENGTH)
			return value;
		return value.substring(0, MAX_REP_LENGTH) + "...";
	}

	public void readContent(DataInputStream in) throws IOException {
		name = in.readUTF();
		valueId = in.readInt();
		stringRep = in.readUTF();
		simpleRep = in.readUTF();
		hasMembers = in.readBoolean();
	}

}
