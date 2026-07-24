package org.briarproject.briar.privategroup;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.client.ClientHelper;
import org.briarproject.bramble.api.data.BdfList;
import org.briarproject.bramble.api.identity.Author;
import org.briarproject.bramble.api.identity.LocalAuthor;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.Message;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.briar.api.privategroup.AdminMessage;
import org.briarproject.briar.api.privategroup.GroupMessage;
import org.briarproject.briar.api.privategroup.GroupMessageFactory;
import org.briarproject.briar.api.privategroup.MemberRemovalMessage;
import org.briarproject.briar.api.privategroup.PinMessage;
import org.briarproject.nullsafety.NotNullByDefault;

import java.security.GeneralSecurityException;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import javax.inject.Inject;

import static org.briarproject.briar.api.privategroup.MessageType.ADMIN;
import static org.briarproject.briar.api.privategroup.MessageType.ADMIN_REMOVED;
import static org.briarproject.briar.api.privategroup.MessageType.ADMIN_REQUEST;
import static org.briarproject.briar.api.privategroup.MessageType.IMAGE;
import static org.briarproject.briar.api.privategroup.MessageType.JOIN;
import static org.briarproject.briar.api.privategroup.MessageType.MEMBER_REMOVED;
import static org.briarproject.briar.api.privategroup.MessageType.PIN;
import static org.briarproject.briar.api.privategroup.MessageType.POST;

@Immutable
@NotNullByDefault
class GroupMessageFactoryImpl implements GroupMessageFactory {

	private final ClientHelper clientHelper;

	@Inject
	GroupMessageFactoryImpl(ClientHelper clientHelper) {
		this.clientHelper = clientHelper;
	}

	@Override
	public GroupMessage createJoinMessage(GroupId groupId, long timestamp,
			LocalAuthor creator) {

		return createJoinMessage(groupId, timestamp, creator, null);
	}

	@Override
	public GroupMessage createJoinMessage(GroupId groupId, long timestamp,
			LocalAuthor member, long inviteTimestamp, byte[] creatorSignature) {

		BdfList invite = BdfList.of(inviteTimestamp, creatorSignature);
		return createJoinMessage(groupId, timestamp, member, invite);
	}

	@Override
	public GroupMessage createJoinMessage(GroupId groupId, long timestamp,
			LocalAuthor member, long inviteTimestamp, Author inviter,
			byte[] inviterSignature) {
		BdfList invite = BdfList.of(inviteTimestamp,
				clientHelper.toList(inviter), inviterSignature);
		return createJoinMessage(groupId, timestamp, member, invite);
	}

	private GroupMessage createJoinMessage(GroupId groupId, long timestamp,
			LocalAuthor member, @Nullable BdfList invite) {
		try {
			// Generate the signature
			BdfList memberList = clientHelper.toList(member);
			BdfList toSign = BdfList.of(
					groupId,
					timestamp,
					memberList,
					invite
			);
			byte[] memberSignature = clientHelper.sign(SIGNING_LABEL_JOIN,
					toSign, member.getPrivateKey());

			// Compose the message
			BdfList body = BdfList.of(
					JOIN.getInt(),
					memberList,
					invite,
					memberSignature
			);
			Message m = clientHelper.createMessage(groupId, timestamp, body);
			return new GroupMessage(m, null, member);
		} catch (GeneralSecurityException e) {
			throw new IllegalArgumentException(e);
		} catch (FormatException e) {
			throw new AssertionError(e);
		}
	}

	@Override
	public GroupMessage createGroupMessage(GroupId groupId, long timestamp,
			@Nullable MessageId parentId, LocalAuthor member, String text,
			MessageId previousMsgId) {
		try {
			// Generate the signature
			BdfList memberList = clientHelper.toList(member);
			BdfList toSign = BdfList.of(
					groupId,
					timestamp,
					memberList,
					parentId,
					previousMsgId,
					text
			);
			byte[] signature = clientHelper.sign(SIGNING_LABEL_POST, toSign,
					member.getPrivateKey());

			// Compose the message
			BdfList body = BdfList.of(
					POST.getInt(),
					memberList,
					parentId,
					previousMsgId,
					text,
					signature
			);
			Message m = clientHelper.createMessage(groupId, timestamp, body);
			return new GroupMessage(m, parentId, member);
		} catch (GeneralSecurityException e) {
			throw new IllegalArgumentException(e);
		} catch (FormatException e) {
			throw new AssertionError(e);
		}
	}

	@Override
	public AdminMessage createAdminMessage(GroupId groupId, long timestamp,
			LocalAuthor creator, Author admin) {
		return createAdminChangeMessage(groupId, timestamp, creator, admin,
				ADMIN, SIGNING_LABEL_ADMIN);
	}

	@Override
	public AdminMessage createAdminRemovalMessage(GroupId groupId,
			long timestamp, LocalAuthor creator, Author admin) {
		return createAdminChangeMessage(groupId, timestamp, creator, admin,
				ADMIN_REMOVED, SIGNING_LABEL_ADMIN_REMOVED);
	}

	private AdminMessage createAdminChangeMessage(GroupId groupId,
			long timestamp, LocalAuthor creator, Author admin,
			org.briarproject.briar.api.privategroup.MessageType type,
			String signingLabel) {
		try {
			BdfList adminList = clientHelper.toList(admin);
			BdfList toSign = BdfList.of(groupId, timestamp, adminList);
			byte[] signature = clientHelper.sign(signingLabel, toSign,
					creator.getPrivateKey());
			BdfList body = BdfList.of(type.getInt(), adminList, signature);
			Message message =
					clientHelper.createMessage(groupId, timestamp, body);
			return new AdminMessage(message, admin);
		} catch (GeneralSecurityException e) {
			throw new IllegalArgumentException(e);
		} catch (FormatException e) {
			throw new AssertionError(e);
		}
	}

	@Override
	public MemberRemovalMessage createMemberRemovalMessage(GroupId groupId,
			long timestamp, LocalAuthor admin, Author member) {
		try {
			BdfList adminList = clientHelper.toList(admin);
			BdfList memberList = clientHelper.toList(member);
			BdfList toSign = BdfList.of(groupId, timestamp, adminList,
					memberList);
			byte[] signature = clientHelper.sign(SIGNING_LABEL_MEMBER_REMOVED,
					toSign, admin.getPrivateKey());
			BdfList body = BdfList.of(MEMBER_REMOVED.getInt(), adminList,
					memberList, signature);
			Message message =
					clientHelper.createMessage(groupId, timestamp, body);
			return new MemberRemovalMessage(message, admin, member);
		} catch (GeneralSecurityException e) {
			throw new IllegalArgumentException(e);
		} catch (FormatException e) {
			throw new AssertionError(e);
		}
	}

	@Override
	public GroupMessage createAdminRequestMessage(GroupId groupId,
			long timestamp, LocalAuthor member, String text,
			MessageId previousMsgId) {
		try {
			BdfList memberList = clientHelper.toList(member);
			BdfList toSign = BdfList.of(groupId, timestamp, memberList,
					previousMsgId, text);
			byte[] signature = clientHelper.sign(SIGNING_LABEL_ADMIN_REQUEST,
					toSign, member.getPrivateKey());
			BdfList body = BdfList.of(ADMIN_REQUEST.getInt(), memberList,
					previousMsgId, text, signature);
			Message message =
					clientHelper.createMessage(groupId, timestamp, body);
			return new GroupMessage(message, null, member, ADMIN_REQUEST);
		} catch (GeneralSecurityException e) {
			throw new IllegalArgumentException(e);
		} catch (FormatException e) {
			throw new AssertionError(e);
		}
	}

	@Override
	public GroupMessage createImageMessage(GroupId groupId, long timestamp,
			@Nullable MessageId parentId, LocalAuthor author,
			@Nullable String caption, String contentType, byte[] image,
			MessageId previousMsgId) {
		try {
			BdfList authorList = clientHelper.toList(author);
			BdfList toSign = BdfList.of(groupId, timestamp, authorList,
					parentId, previousMsgId, caption, contentType, image);
			byte[] signature = clientHelper.sign(SIGNING_LABEL_IMAGE, toSign,
					author.getPrivateKey());
			BdfList body = BdfList.of(IMAGE.getInt(), authorList, parentId,
					previousMsgId, caption, contentType, image, signature);
			Message message =
					clientHelper.createMessage(groupId, timestamp, body);
			return new GroupMessage(message, parentId, author, IMAGE);
		} catch (GeneralSecurityException e) {
			throw new IllegalArgumentException(e);
		} catch (FormatException e) {
			throw new AssertionError(e);
		}
	}

	@Override
	public PinMessage createPinMessage(GroupId groupId, long timestamp,
			LocalAuthor admin, @Nullable MessageId messageId) {
		try {
			BdfList adminList = clientHelper.toList(admin);
			BdfList toSign = BdfList.of(groupId, timestamp, adminList,
					messageId);
			byte[] signature = clientHelper.sign(SIGNING_LABEL_PIN, toSign,
					admin.getPrivateKey());
			BdfList body = BdfList.of(PIN.getInt(), adminList, messageId,
					signature);
			Message message =
					clientHelper.createMessage(groupId, timestamp, body);
			return new PinMessage(message, admin, messageId);
		} catch (GeneralSecurityException e) {
			throw new IllegalArgumentException(e);
		} catch (FormatException e) {
			throw new AssertionError(e);
		}
	}

}
