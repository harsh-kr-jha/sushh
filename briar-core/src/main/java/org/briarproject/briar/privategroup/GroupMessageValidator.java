package org.briarproject.briar.privategroup;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.client.BdfMessageContext;
import org.briarproject.bramble.api.client.BdfMessageValidator;
import org.briarproject.bramble.api.client.ClientHelper;
import org.briarproject.bramble.api.data.BdfDictionary;
import org.briarproject.bramble.api.data.BdfList;
import org.briarproject.bramble.api.data.MetadataEncoder;
import org.briarproject.bramble.api.identity.Author;
import org.briarproject.bramble.api.sync.Group;
import org.briarproject.bramble.api.sync.InvalidMessageException;
import org.briarproject.bramble.api.sync.Message;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.bramble.api.system.Clock;
import org.briarproject.briar.api.privategroup.PrivateGroup;
import org.briarproject.briar.api.privategroup.PrivateGroupFactory;
import org.briarproject.briar.api.privategroup.invitation.GroupInvitationFactory;
import org.briarproject.nullsafety.NotNullByDefault;

import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Collection;

import javax.annotation.concurrent.Immutable;

import static org.briarproject.bramble.api.identity.AuthorConstants.MAX_SIGNATURE_LENGTH;
import static org.briarproject.bramble.util.ValidationUtils.checkLength;
import static org.briarproject.bramble.util.ValidationUtils.checkSize;
import static org.briarproject.briar.api.privategroup.GroupMessageFactory.SIGNING_LABEL_ADMIN;
import static org.briarproject.briar.api.privategroup.GroupMessageFactory.SIGNING_LABEL_JOIN;
import static org.briarproject.briar.api.privategroup.GroupMessageFactory.SIGNING_LABEL_POST;
import static org.briarproject.briar.api.privategroup.MessageType.ADMIN;
import static org.briarproject.briar.api.privategroup.MessageType.JOIN;
import static org.briarproject.briar.api.privategroup.MessageType.POST;
import static org.briarproject.briar.api.privategroup.PrivateGroupConstants.MAX_GROUP_POST_TEXT_LENGTH;
import static org.briarproject.briar.api.privategroup.invitation.GroupInvitationFactory.SIGNING_LABEL_INVITE;
import static org.briarproject.briar.privategroup.GroupConstants.KEY_INITIAL_JOIN_MSG;
import static org.briarproject.briar.privategroup.GroupConstants.KEY_INVITER;
import static org.briarproject.briar.privategroup.GroupConstants.KEY_MEMBER;
import static org.briarproject.briar.privategroup.GroupConstants.KEY_PARENT_MSG_ID;
import static org.briarproject.briar.privategroup.GroupConstants.KEY_PREVIOUS_MSG_ID;
import static org.briarproject.briar.privategroup.GroupConstants.KEY_READ;
import static org.briarproject.briar.privategroup.GroupConstants.KEY_TIMESTAMP;
import static org.briarproject.briar.privategroup.GroupConstants.KEY_TYPE;

@Immutable
@NotNullByDefault
class GroupMessageValidator extends BdfMessageValidator {

	private final PrivateGroupFactory privateGroupFactory;
	private final GroupInvitationFactory groupInvitationFactory;

	GroupMessageValidator(PrivateGroupFactory privateGroupFactory,
			ClientHelper clientHelper, MetadataEncoder metadataEncoder,
			Clock clock, GroupInvitationFactory groupInvitationFactory) {
		super(clientHelper, metadataEncoder, clock);
		this.privateGroupFactory = privateGroupFactory;
		this.groupInvitationFactory = groupInvitationFactory;
	}

	@Override
	protected BdfMessageContext validateMessage(Message m, Group g,
			BdfList body) throws InvalidMessageException, FormatException {

		checkSize(body, 3, 6);

		// Message type (int)
		int type = body.getInt(0);
		if (type == JOIN.getInt()) {
			checkSize(body, 4);
		} else if (type == POST.getInt()) {
			checkSize(body, 6);
		} else if (type == ADMIN.getInt()) {
			checkSize(body, 3);
		} else {
			throw new InvalidMessageException("Unknown Message Type");
		}

		// Member (author)
		BdfList memberList = body.getList(1);
		Author member = clientHelper.parseAndValidateAuthor(memberList);

		BdfMessageContext c;
		if (type == JOIN.getInt()) {
			c = validateJoin(m, g, body, member);
			addMessageMetadata(c, memberList, m.getTimestamp());
		} else if (type == POST.getInt()) {
			c = validatePost(m, g, body, member);
			addMessageMetadata(c, memberList, m.getTimestamp());
		} else if (type == ADMIN.getInt()) {
			c = validateAdmin(m, g, body, member);
			addMessageMetadata(c, memberList, m.getTimestamp());
		} else {
			throw new AssertionError();
		}
		c.getDictionary().put(KEY_TYPE, type);
		return c;
	}

	private BdfMessageContext validateJoin(Message m, Group g, BdfList body,
			Author member) throws FormatException {
		// Message type, member, optional invite, member's signature
		checkSize(body, 4);
		BdfList inviteList = body.getOptionalList(2);
		byte[] memberSignature = body.getRaw(3);
		checkLength(memberSignature, 1, MAX_SIGNATURE_LENGTH);

		// Invite is null if the member is the creator of the private group
		PrivateGroup pg = privateGroupFactory.parsePrivateGroup(g);
		Author creator = pg.getCreator();
		boolean isCreator = member.equals(creator);
		if (isCreator) {
			if (inviteList != null) throw new FormatException();
		} else {
			if (inviteList == null) throw new FormatException();
			// Timestamp, optional inviter, inviter's signature
			checkSize(inviteList, 2, 3);
			// Join timestamp must be greater than invite timestamp
			long inviteTimestamp = inviteList.getLong(0);
			if (m.getTimestamp() <= inviteTimestamp)
				throw new FormatException();
			Author inviter = creator;
			byte[] inviterSignature;
			if (inviteList.size() == 3) {
				BdfList inviterList = inviteList.getList(1);
				inviter = clientHelper.parseAndValidateAuthor(inviterList);
				inviterSignature = inviteList.getRaw(2);
			} else {
				inviterSignature = inviteList.getRaw(1);
			}
			checkLength(inviterSignature, 1, MAX_SIGNATURE_LENGTH);
			// The invite token is signed by the member who sent the invitation
			BdfList token = groupInvitationFactory.createInviteToken(
					inviter.getId(), member.getId(), g.getId(),
					inviteTimestamp);
			try {
				clientHelper.verifySignature(inviterSignature,
						SIGNING_LABEL_INVITE,
						token, inviter.getPublicKey());
			} catch (GeneralSecurityException e) {
				throw new FormatException();
			}
		}

		// Verify the member's signature
		BdfList memberList = body.getList(1); // Already validated
		BdfList signed = BdfList.of(
				g.getId(),
				m.getTimestamp(),
				memberList,
				inviteList
		);
		try {
			clientHelper.verifySignature(memberSignature, SIGNING_LABEL_JOIN,
					signed, member.getPublicKey());
		} catch (GeneralSecurityException e) {
			throw new FormatException();
		}

		// Return the metadata and no dependencies
		BdfDictionary meta = new BdfDictionary();
		meta.put(KEY_INITIAL_JOIN_MSG, isCreator);
		if (!isCreator && inviteList != null && inviteList.size() == 3) {
			meta.put(KEY_INVITER, inviteList.getList(1));
		}
		return new BdfMessageContext(meta);
	}

	private BdfMessageContext validateAdmin(Message m, Group g, BdfList body,
			Author admin) throws FormatException {
		// Message type, appointed admin, creator's signature
		checkSize(body, 3);
		byte[] signature = body.getRaw(2);
		checkLength(signature, 1, MAX_SIGNATURE_LENGTH);
		PrivateGroup pg = privateGroupFactory.parsePrivateGroup(g);
		BdfList adminList = body.getList(1);
		BdfList signed = BdfList.of(g.getId(), m.getTimestamp(), adminList);
		try {
			clientHelper.verifySignature(signature, SIGNING_LABEL_ADMIN,
					signed, pg.getCreator().getPublicKey());
		} catch (GeneralSecurityException e) {
			throw new FormatException();
		}
		return new BdfMessageContext(new BdfDictionary());
	}

	private BdfMessageContext validatePost(Message m, Group g, BdfList body,
			Author member) throws FormatException {
		// Message type, member, optional parent ID, previous message ID,
		// text, signature
		checkSize(body, 6);
		byte[] parentId = body.getOptionalRaw(2);
		checkLength(parentId, MessageId.LENGTH);
		byte[] previousMessageId = body.getRaw(3);
		checkLength(previousMessageId, MessageId.LENGTH);
		String text = body.getString(4);
		checkLength(text, 1, MAX_GROUP_POST_TEXT_LENGTH);
		byte[] signature = body.getRaw(5);
		checkLength(signature, 1, MAX_SIGNATURE_LENGTH);

		// Verify the member's signature
		BdfList memberList = body.getList(1); // Already validated
		BdfList signed = BdfList.of(
				g.getId(),
				m.getTimestamp(),
				memberList,
				parentId,
				previousMessageId,
				text
		);
		try {
			clientHelper.verifySignature(signature, SIGNING_LABEL_POST,
					signed, member.getPublicKey());
		} catch (GeneralSecurityException e) {
			throw new FormatException();
		}

		// The parent post, if any, and the member's previous message are
		// dependencies
		Collection<MessageId> dependencies = new ArrayList<>();
		if (parentId != null) dependencies.add(new MessageId(parentId));
		dependencies.add(new MessageId(previousMessageId));

		// Return the metadata and dependencies
		BdfDictionary meta = new BdfDictionary();
		if (parentId != null) meta.put(KEY_PARENT_MSG_ID, parentId);
		meta.put(KEY_PREVIOUS_MSG_ID, previousMessageId);
		return new BdfMessageContext(meta, dependencies);
	}

	private void addMessageMetadata(BdfMessageContext c, BdfList member,
			long timestamp) {
		c.getDictionary().put(KEY_MEMBER, member);
		c.getDictionary().put(KEY_TIMESTAMP, timestamp);
		c.getDictionary().put(KEY_READ, false);
	}

}
