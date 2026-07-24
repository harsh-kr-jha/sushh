package org.briarproject.briar.android.sushh.broadcast;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class BroadcastEnvelope {

	private final String broadcastId;
	private final String channelId;
	private final String authorDeviceId;
	private final String body;
	private final long createdAt;
	private final long expiresAt;
	private final AlertSeverity severity;
	private final LocationScope location;
	private final byte[] attachmentDigest;
	private final List<OwnerApproval> approvals;

	public BroadcastEnvelope(String broadcastId, String channelId,
			String authorDeviceId, String body, long createdAt, long expiresAt,
			AlertSeverity severity, LocationScope location,
			byte[] attachmentDigest, List<OwnerApproval> approvals) {
		if (isBlank(broadcastId) || isBlank(channelId) ||
				isBlank(authorDeviceId)) {
			throw new IllegalArgumentException(
					"Broadcast, channel and author IDs are required");
		}
		if (body == null) throw new IllegalArgumentException("Body is required");
		if (expiresAt <= createdAt) {
			throw new IllegalArgumentException(
					"Expiry must be later than creation");
		}
		this.broadcastId = broadcastId;
		this.channelId = channelId;
		this.authorDeviceId = authorDeviceId;
		this.body = body;
		this.createdAt = createdAt;
		this.expiresAt = expiresAt;
		this.severity = severity == null ? AlertSeverity.STANDARD : severity;
		this.location = location == null ? LocationScope.global() : location;
		this.attachmentDigest = attachmentDigest == null ? new byte[0] :
				Arrays.copyOf(attachmentDigest, attachmentDigest.length);
		List<OwnerApproval> copy = approvals == null ? new ArrayList<>() :
				new ArrayList<>(approvals);
		this.approvals = Collections.unmodifiableList(copy);
	}

	private static boolean isBlank(String value) {
		return value == null || value.trim().isEmpty();
	}

	public byte[] canonicalPayload() {
		try {
			ByteArrayOutputStream bytes = new ByteArrayOutputStream();
			DataOutputStream out = new DataOutputStream(bytes);
			out.writeUTF(broadcastId);
			out.writeUTF(channelId);
			out.writeUTF(authorDeviceId);
			out.writeUTF(body);
			out.writeLong(createdAt);
			out.writeLong(expiresAt);
			out.writeUTF(severity.name());
			location.writeCanonical(out);
			out.writeInt(attachmentDigest.length);
			out.write(attachmentDigest);
			out.flush();
			return bytes.toByteArray();
		} catch (IOException e) {
			throw new IllegalStateException("Could not encode broadcast", e);
		}
	}

	public byte[] payloadDigest() {
		try {
			return MessageDigest.getInstance("SHA-256")
					.digest(canonicalPayload());
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 is unavailable", e);
		}
	}

	public boolean isExpired(long now) {
		return now >= expiresAt;
	}

	public boolean targets(LocationScope participantLocation) {
		return location.includes(participantLocation);
	}

	public String getBroadcastId() {
		return broadcastId;
	}

	public String getChannelId() {
		return channelId;
	}

	public AlertSeverity getSeverity() {
		return severity;
	}

	public LocationScope getLocation() {
		return location;
	}

	public List<OwnerApproval> getApprovals() {
		return approvals;
	}
}
