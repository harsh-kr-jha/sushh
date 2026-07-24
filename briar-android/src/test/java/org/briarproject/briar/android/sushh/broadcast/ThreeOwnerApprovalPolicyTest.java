package org.briarproject.briar.android.sushh.broadcast;

import org.junit.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.spec.ECGenParameterSpec;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.briarproject.briar.android.sushh.broadcast.ThreeOwnerApprovalPolicy.Result.APPROVED;
import static org.briarproject.briar.android.sushh.broadcast.ThreeOwnerApprovalPolicy.Result.EXPIRED;
import static org.briarproject.briar.android.sushh.broadcast.ThreeOwnerApprovalPolicy.Result.INSUFFICIENT_VALID_APPROVALS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ThreeOwnerApprovalPolicyTest {

	private static final long CREATED = 1_000L;
	private static final long EXPIRES = 10_000L;

	@Test
	public void acceptsThreeIndependentOwnerSignatures() throws Exception {
		List<KeyPair> owners = owners(3);
		BroadcastEnvelope draft = draft(Collections.emptyList());
		List<OwnerApproval> approvals = new ArrayList<>();
		Map<String, java.security.PublicKey> keys = new HashMap<>();
		for (int i = 0; i < owners.size(); i++) {
			String ownerId = "owner-" + i;
			keys.put(ownerId, owners.get(i).getPublic());
			approvals.add(new OwnerApproval(ownerId,
					sign(owners.get(i), draft.payloadDigest())));
		}
		BroadcastEnvelope approved = draft(approvals);

		assertEquals(APPROVED, new ThreeOwnerApprovalPolicy().verify(
				approved, keys, new HashSet<>(), CREATED + 1));
	}

	@Test
	public void rejectsTwoSignaturesAndDuplicateOwner() throws Exception {
		List<KeyPair> owners = owners(2);
		BroadcastEnvelope draft = draft(Collections.emptyList());
		List<OwnerApproval> approvals = new ArrayList<>();
		Map<String, java.security.PublicKey> keys = new HashMap<>();
		for (int i = 0; i < owners.size(); i++) {
			String ownerId = "owner-" + i;
			keys.put(ownerId, owners.get(i).getPublic());
			approvals.add(new OwnerApproval(ownerId,
					sign(owners.get(i), draft.payloadDigest())));
		}
		approvals.add(approvals.get(0));

		assertEquals(INSUFFICIENT_VALID_APPROVALS,
				new ThreeOwnerApprovalPolicy().verify(draft(approvals), keys,
						new HashSet<>(), CREATED + 1));
	}

	@Test
	public void rejectsRevokedOwnerAndExpiredBroadcast() throws Exception {
		List<KeyPair> owners = owners(3);
		BroadcastEnvelope draft = draft(Collections.emptyList());
		List<OwnerApproval> approvals = new ArrayList<>();
		Map<String, java.security.PublicKey> keys = new HashMap<>();
		for (int i = 0; i < owners.size(); i++) {
			String ownerId = "owner-" + i;
			keys.put(ownerId, owners.get(i).getPublic());
			approvals.add(new OwnerApproval(ownerId,
					sign(owners.get(i), draft.payloadDigest())));
		}
		Set<String> revoked = new HashSet<>();
		revoked.add("owner-1");
		BroadcastEnvelope approved = draft(approvals);

		assertEquals(INSUFFICIENT_VALID_APPROVALS,
				new ThreeOwnerApprovalPolicy().verify(
						approved, keys, revoked, CREATED + 1));
		assertEquals(EXPIRED, new ThreeOwnerApprovalPolicy().verify(
				approved, keys, new HashSet<>(), EXPIRES));
	}

	@Test
	public void targetsLocationHierarchy() {
		LocationScope district = new LocationScope(
				"India", "Bihar", "Purnea", "");
		assertTrue(district.includes(new LocationScope(
				"India", "Bihar", "Purnea", "East")));
		assertFalse(district.includes(new LocationScope(
				"India", "Bihar", "Patna", "Central")));
	}

	private static BroadcastEnvelope draft(List<OwnerApproval> approvals) {
		return new BroadcastEnvelope("broadcast-1", "channel-1", "hub-1",
				"Urgent field update", CREATED, EXPIRES,
				AlertSeverity.URGENT,
				new LocationScope("India", "Bihar", "Purnea", ""),
				new byte[]{1, 2, 3}, approvals);
	}

	private static List<KeyPair> owners(int count) throws Exception {
		KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
		generator.initialize(new ECGenParameterSpec("secp256r1"));
		List<KeyPair> owners = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			owners.add(generator.generateKeyPair());
		}
		return owners;
	}

	private static byte[] sign(KeyPair owner, byte[] digest) throws Exception {
		Signature signature = Signature.getInstance("SHA256withECDSA");
		signature.initSign(owner.getPrivate());
		signature.update(digest);
		return signature.sign();
	}
}
