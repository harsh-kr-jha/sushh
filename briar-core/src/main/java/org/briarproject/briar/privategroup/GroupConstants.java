package org.briarproject.briar.privategroup;

import static org.briarproject.briar.client.MessageTrackerConstants.MSG_KEY_READ;

interface GroupConstants {

	// Metadata keys
	String KEY_TYPE = "type";
	String KEY_TIMESTAMP = "timestamp";
	String KEY_READ = MSG_KEY_READ;
	String KEY_PARENT_MSG_ID = "parentMsgId";
	String KEY_PREVIOUS_MSG_ID = "previousMsgId";
	String KEY_MEMBER = "member";
	String KEY_INVITER = "inviter";
	String KEY_INITIAL_JOIN_MSG = "initialJoinMsg";
	String KEY_TARGET_MEMBER = "targetMember";
	String KEY_PINNED_MSG_ID = "pinnedMessageId";

	String GROUP_KEY_MEMBERS = "members";
	String GROUP_KEY_ADMINS = "admins";
	String GROUP_KEY_PINNED_MSG_ID = "pinnedMessageId";
	String GROUP_KEY_PIN_TIMESTAMP = "pinTimestamp";
	String GROUP_KEY_KICKED = "kicked";
	String GROUP_KEY_OUR_GROUP = "ourGroup";
	String GROUP_KEY_CREATOR_ID = "creatorId";
	String GROUP_KEY_DISSOLVED = "dissolved";
	String GROUP_KEY_VISIBILITY = "visibility";

}
