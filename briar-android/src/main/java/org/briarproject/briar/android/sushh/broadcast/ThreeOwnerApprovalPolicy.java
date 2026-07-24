package org.briarproject.briar.android.sushh.broadcast;

import java.security.GeneralSecurityException;
import java.security.PublicKey;
import java.security.Signature;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class ThreeOwnerApprovalPolicy {

	public enum Result {
		APPROVED,
		EXPIRED,
		INSUFFICIENT_VALID_APPROVALS
	}

	private final int requiredApprovals;

	public ThreeOwnerApprovalPolicy() {
		this(3);
	}

	public ThreeOwnerApprovalPolicy(int requiredApprovals) {
		if (requiredApprovals < 1) {
			throw new IllegalArgumentException(
					"At least one approval is required");
		}
		this.requiredApprovals = requiredApprovals;
	}

	public Result verify(BroadcastEnvelope broadcast,
			Map<String, PublicKey> authorisedOwners,
			Set<String> revokedOwners, long now) {
		if (broadcast.isExpired(now)) return Result.EXPIRED;
		byte[] digest = broadcast.payloadDigest();
		Set<String> acceptedOwners = new HashSet<>();
		for (OwnerApproval approval : broadcast.getApprovals()) {
			String ownerId = approval.getOwnerId();
			if (acceptedOwners.contains(ownerId) ||
					revokedOwners.contains(ownerId)) continue;
			PublicKey ownerKey = authorisedOwners.get(ownerId);
			if (ownerKey == null) continue;
			if (hasValidSignature(ownerKey, digest,
					approval.getSignature())) {
				acceptedOwners.add(ownerId);
				if (acceptedOwners.size() >= requiredApprovals) {
					return Result.APPROVED;
				}
			}
		}
		return Result.INSUFFICIENT_VALID_APPROVALS;
	}

	private boolean hasValidSignature(PublicKey key, byte[] digest,
			byte[] signatureBytes) {
		try {
			Signature signature = Signature.getInstance("SHA256withECDSA");
			signature.initVerify(key);
			signature.update(digest);
			return signature.verify(signatureBytes);
		} catch (GeneralSecurityException e) {
			return false;
		}
	}
}
