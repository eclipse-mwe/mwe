package org.eclipse.emf.mwe2.language.mwe2.impl;

import org.eclipse.xtext.common.types.JvmType;

public class ReferrableImplCustom extends ReferrableImpl {

	@Override
	public JvmType getActualType() {
		return getType();
	}
	
}