package org.briarproject.briar.android.sushh.broadcast;

import java.util.Arrays;

public final class OwnerApproval {

	private final String ownerId;
	private final byte[] signature;

	public OwnerApproval(String ownerId, byte[] signature) {
		if (ownerId == null || ownerId.trim().isEmpty()) {
			throw new IllegalArgumentException("Owner ID is required");
		}
		if (signature == null || signature.length == 0) {
			throw new IllegalArgumentException("Signature is required");
		}
		this.ownerId = ownerId;
		this.signature = Arrays.copyOf(signature, signature.length);
	}

	public String getOwnerId() {
		return ownerId;
	}

	public byte[] getSignature() {
		return Arrays.copyOf(signature, signature.length);
	}
}
