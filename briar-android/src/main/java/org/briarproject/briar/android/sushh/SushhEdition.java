package org.briarproject.briar.android.sushh;

import org.briarproject.briar.BuildConfig;

/**
 * Describes the UI surface compiled into this APK. Protocol authorization must
 * still be verified with owner-issued credentials.
 */
public final class SushhEdition {

	private SushhEdition() {
	}

	public static boolean isHub() {
		return BuildConfig.SUSHH_HUB;
	}

	public static boolean canDraftBroadcasts() {
		return isHub();
	}

	public static boolean canApproveBroadcasts(SushhRole role) {
		return isHub() && role == SushhRole.OWNER;
	}

	public static boolean canPublishToChannel(SushhRole role) {
		return isHub() &&
				(role == SushhRole.OWNER || role == SushhRole.ADMIN);
	}

	public static boolean canMessageHub(SushhRole role) {
		return role == SushhRole.PARTICIPANT || role == SushhRole.OWNER ||
				role == SushhRole.ADMIN;
	}

	public static boolean canRelay(SushhRole role) {
		return true;
	}
}
