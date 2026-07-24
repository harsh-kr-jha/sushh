package org.briarproject.briar.api.privategroup;

import org.briarproject.bramble.api.crypto.CryptoExecutor;
import org.briarproject.bramble.api.identity.Author;
import org.briarproject.bramble.api.identity.LocalAuthor;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.nullsafety.NotNullByDefault;

import javax.annotation.Nullable;

import static org.briarproject.briar.api.privategroup.PrivateGroupManager.CLIENT_ID;

@NotNullByDefault
public interface GroupMessageFactory {

	String SIGNING_LABEL_JOIN = CLIENT_ID.getString() + "/JOIN";
	String SIGNING_LABEL_POST = CLIENT_ID.getString() + "/POST";
	String SIGNING_LABEL_ADMIN = CLIENT_ID.getString() + "/ADMIN";
	String SIGNING_LABEL_ADMIN_REMOVED =
			CLIENT_ID.getString() + "/ADMIN_REMOVED";
	String SIGNING_LABEL_MEMBER_REMOVED =
			CLIENT_ID.getString() + "/MEMBER_REMOVED";
	String SIGNING_LABEL_ADMIN_REQUEST =
			CLIENT_ID.getString() + "/ADMIN_REQUEST";
	String SIGNING_LABEL_IMAGE = CLIENT_ID.getString() + "/IMAGE";
	String SIGNING_LABEL_PIN = CLIENT_ID.getString() + "/PIN";

	/**
	 * Creates a join announcement message for the creator of a group.
	 *
	 * @param groupId The ID of the private group that is being joined
	 * @param timestamp The timestamp to be used in the join announcement
	 * @param creator The creator's identity
	 */
	@CryptoExecutor
	GroupMessage createJoinMessage(GroupId groupId, long timestamp,
			LocalAuthor creator);

	/**
	 * Creates a join announcement message for a joining member.
	 *
	 * @param groupId The ID of the private group that is being joined
	 * @param timestamp The timestamp to be used in the join announcement,
	 * which must be greater than the timestamp of the invitation message
	 * @param member The member's identity
	 * @param inviteTimestamp The timestamp of the invitation message
	 * @param creatorSignature The creator's signature from the invitation
	 * message
	 */
	@CryptoExecutor
	GroupMessage createJoinMessage(GroupId groupId, long timestamp,
			LocalAuthor member, long inviteTimestamp, byte[] creatorSignature);

	/**
	 * Creates a join announcement for a member invited by another channel
	 * member.
	 */
	@CryptoExecutor
	GroupMessage createJoinMessage(GroupId groupId, long timestamp,
			LocalAuthor member, long inviteTimestamp, Author inviter,
			byte[] inviterSignature);

	/**
	 * Creates a private group post.
	 *
	 * @param groupId The ID of the private group
	 * @param timestamp Must be greater than the timestamps of the parent
	 * post, if any, and the member's previous message
	 * @param parentId The ID of the parent post, or null if the post has no
	 * parent
	 * @param author The author of the post
	 * @param text The text of the post
	 * @param previousMsgId The ID of the author's previous message
	 * in this group
	 */
	@CryptoExecutor
	GroupMessage createGroupMessage(GroupId groupId, long timestamp,
			@Nullable MessageId parentId, LocalAuthor author, String text,
			MessageId previousMsgId);

	/**
	 * Creates a creator-signed announcement appointing a channel admin.
	 */
	@CryptoExecutor
	AdminMessage createAdminMessage(GroupId groupId, long timestamp,
			LocalAuthor creator, Author admin);

	/**
	 * Creates a creator-signed announcement removing a channel admin.
	 */
	@CryptoExecutor
	AdminMessage createAdminRemovalMessage(GroupId groupId, long timestamp,
			LocalAuthor creator, Author admin);

	/**
	 * Creates an admin-signed announcement removing a channel member.
	 */
	@CryptoExecutor
	MemberRemovalMessage createMemberRemovalMessage(GroupId groupId,
			long timestamp, LocalAuthor admin, Author member);

	/**
	 * Creates a message from a read-only member to all channel admins.
	 */
	@CryptoExecutor
	GroupMessage createAdminRequestMessage(GroupId groupId, long timestamp,
			LocalAuthor member, String text, MessageId previousMsgId);

	/**
	 * Creates an image post with an optional caption.
	 */
	@CryptoExecutor
	GroupMessage createImageMessage(GroupId groupId, long timestamp,
			@Nullable MessageId parentId, LocalAuthor author,
			@Nullable String caption, String contentType, byte[] image,
			MessageId previousMsgId);

	/**
	 * Creates an admin-signed pin or unpin announcement.
	 */
	@CryptoExecutor
	PinMessage createPinMessage(GroupId groupId, long timestamp,
			LocalAuthor admin, @Nullable MessageId messageId);

}
