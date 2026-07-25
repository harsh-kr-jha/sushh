package org.briarproject.briar.privategroup;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.client.BdfIncomingMessageHook;
import org.briarproject.bramble.api.client.ClientHelper;
import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.contact.ContactManager;
import org.briarproject.bramble.api.data.BdfDictionary;
import org.briarproject.bramble.api.data.BdfEntry;
import org.briarproject.bramble.api.data.BdfList;
import org.briarproject.bramble.api.data.MetadataParser;
import org.briarproject.bramble.api.db.DatabaseComponent;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.db.Transaction;
import org.briarproject.bramble.api.event.Event;
import org.briarproject.bramble.api.identity.Author;
import org.briarproject.bramble.api.identity.AuthorId;
import org.briarproject.bramble.api.identity.IdentityManager;
import org.briarproject.bramble.api.identity.LocalAuthor;
import org.briarproject.bramble.api.sync.Group;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.Message;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.briar.api.client.MessageTracker;
import org.briarproject.briar.api.client.MessageTracker.GroupCount;
import org.briarproject.briar.api.client.ProtocolStateException;
import org.briarproject.briar.api.identity.AuthorInfo;
import org.briarproject.briar.api.identity.AuthorInfo.Status;
import org.briarproject.briar.api.identity.AuthorManager;
import org.briarproject.briar.api.privategroup.AdminMessage;
import org.briarproject.briar.api.privategroup.GroupMember;
import org.briarproject.briar.api.privategroup.GroupMessage;
import org.briarproject.briar.api.privategroup.GroupMessageHeader;
import org.briarproject.briar.api.privategroup.JoinMessageHeader;
import org.briarproject.briar.api.privategroup.MessageType;
import org.briarproject.briar.api.privategroup.MemberRemovalMessage;
import org.briarproject.briar.api.privategroup.PinMessage;
import org.briarproject.briar.api.privategroup.PrivateGroup;
import org.briarproject.briar.api.privategroup.PrivateGroupFactory;
import org.briarproject.briar.api.privategroup.PrivateGroupManager;
import org.briarproject.briar.api.privategroup.Visibility;
import org.briarproject.briar.api.privategroup.event.ContactRelationshipRevealedEvent;
import org.briarproject.briar.api.privategroup.event.GroupAdminAddedEvent;
import org.briarproject.briar.api.privategroup.event.GroupAdminRemovedEvent;
import org.briarproject.briar.api.privategroup.event.GroupDissolvedEvent;
import org.briarproject.briar.api.privategroup.event.GroupMemberRemovedEvent;
import org.briarproject.briar.api.privategroup.event.GroupMessageAddedEvent;
import org.briarproject.briar.api.privategroup.event.GroupPinnedMessageChangedEvent;
import org.briarproject.nullsafety.NotNullByDefault;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.annotation.concurrent.ThreadSafe;
import javax.annotation.Nullable;
import javax.inject.Inject;

import static org.briarproject.bramble.api.sync.validation.IncomingMessageHook.DeliveryAction.ACCEPT_SHARE;
import static org.briarproject.briar.api.identity.AuthorInfo.Status.UNVERIFIED;
import static org.briarproject.briar.api.identity.AuthorInfo.Status.VERIFIED;
import static org.briarproject.briar.api.privategroup.MessageType.ADMIN;
import static org.briarproject.briar.api.privategroup.MessageType.ADMIN_REMOVED;
import static org.briarproject.briar.api.privategroup.MessageType.ADMIN_REQUEST;
import static org.briarproject.briar.api.privategroup.MessageType.IMAGE;
import static org.briarproject.briar.api.privategroup.MessageType.JOIN;
import static org.briarproject.briar.api.privategroup.MessageType.MEMBER_REMOVED;
import static org.briarproject.briar.api.privategroup.MessageType.PIN;
import static org.briarproject.briar.api.privategroup.MessageType.POST;
import static org.briarproject.briar.api.privategroup.Visibility.INVISIBLE;
import static org.briarproject.briar.api.privategroup.Visibility.REVEALED_BY_CONTACT;
import static org.briarproject.briar.api.privategroup.Visibility.REVEALED_BY_US;
import static org.briarproject.briar.api.privategroup.Visibility.VISIBLE;
import static org.briarproject.briar.privategroup.GroupConstants.GROUP_KEY_ADMINS;
import static org.briarproject.briar.privategroup.GroupConstants.GROUP_KEY_CREATOR_ID;
import static org.briarproject.briar.privategroup.GroupConstants.GROUP_KEY_DISSOLVED;
import static org.briarproject.briar.privategroup.GroupConstants.GROUP_KEY_KICKED;
import static org.briarproject.briar.privategroup.GroupConstants.GROUP_KEY_MEMBERS;
import static org.briarproject.briar.privategroup.GroupConstants.GROUP_KEY_OUR_GROUP;
import static org.briarproject.briar.privategroup.GroupConstants.GROUP_KEY_PINNED_MSG_ID;
import static org.briarproject.briar.privategroup.GroupConstants.GROUP_KEY_PIN_TIMESTAMP;
import static org.briarproject.briar.privategroup.GroupConstants.GROUP_KEY_VISIBILITY;
import static org.briarproject.briar.privategroup.GroupConstants.KEY_INITIAL_JOIN_MSG;
import static org.briarproject.briar.privategroup.GroupConstants.KEY_INVITER;
import static org.briarproject.briar.privategroup.GroupConstants.KEY_MEMBER;
import static org.briarproject.briar.privategroup.GroupConstants.KEY_PARENT_MSG_ID;
import static org.briarproject.briar.privategroup.GroupConstants.KEY_PINNED_MSG_ID;
import static org.briarproject.briar.privategroup.GroupConstants.KEY_PREVIOUS_MSG_ID;
import static org.briarproject.briar.privategroup.GroupConstants.KEY_READ;
import static org.briarproject.briar.privategroup.GroupConstants.KEY_TIMESTAMP;
import static org.briarproject.briar.privategroup.GroupConstants.KEY_TARGET_MEMBER;
import static org.briarproject.briar.privategroup.GroupConstants.KEY_TYPE;

@ThreadSafe
@NotNullByDefault
class PrivateGroupManagerImpl extends BdfIncomingMessageHook
		implements PrivateGroupManager {

	private final PrivateGroupFactory privateGroupFactory;
	private final ContactManager contactManager;
	private final IdentityManager identityManager;
	private final AuthorManager authorManager;
	private final MessageTracker messageTracker;
	private final List<PrivateGroupHook> hooks;

	@Inject
	PrivateGroupManagerImpl(ClientHelper clientHelper,
			MetadataParser metadataParser, DatabaseComponent db,
			PrivateGroupFactory privateGroupFactory,
			ContactManager contactManager, IdentityManager identityManager,
			AuthorManager authorManager, MessageTracker messageTracker) {
		super(db, clientHelper, metadataParser);
		this.privateGroupFactory = privateGroupFactory;
		this.contactManager = contactManager;
		this.identityManager = identityManager;
		this.authorManager = authorManager;
		this.messageTracker = messageTracker;
		hooks = new CopyOnWriteArrayList<>();
	}

	@Override
	public void addPrivateGroup(PrivateGroup group, GroupMessage joinMsg,
			boolean creator) throws DbException {
		Transaction txn = db.startTransaction(false);
		try {
			addPrivateGroup(txn, group, joinMsg, creator);
			db.commitTransaction(txn);
		} finally {
			db.endTransaction(txn);
		}
	}

	@Override
	public void addPrivateGroup(Transaction txn, PrivateGroup group,
			GroupMessage joinMsg, boolean creator) throws DbException {
		try {
			db.addGroup(txn, group.getGroup());
			AuthorId creatorId = group.getCreator().getId();
			BdfDictionary meta = BdfDictionary.of(
					new BdfEntry(GROUP_KEY_MEMBERS, new BdfList()),
					new BdfEntry(GROUP_KEY_ADMINS,
							BdfList.of(clientHelper.toList(group.getCreator()))),
					new BdfEntry(GROUP_KEY_CREATOR_ID, creatorId),
					new BdfEntry(GROUP_KEY_OUR_GROUP, creator),
					new BdfEntry(GROUP_KEY_DISSOLVED, false),
					new BdfEntry(GROUP_KEY_KICKED, false)
			);
			clientHelper.mergeGroupMetadata(txn, group.getId(), meta);
			joinPrivateGroup(txn, joinMsg, creator);
		} catch (FormatException e) {
			throw new DbException(e);
		}
	}

	private void joinPrivateGroup(Transaction txn, GroupMessage m,
			boolean creator) throws DbException, FormatException {
		BdfDictionary meta = new BdfDictionary();
		meta.put(KEY_TYPE, JOIN.getInt());
		meta.put(KEY_INITIAL_JOIN_MSG, creator);
		addMessageMetadata(meta, m);
		clientHelper.addLocalMessage(txn, m.getMessage(), meta, true, false);
		messageTracker.trackOutgoingMessage(txn, m.getMessage());
		addMember(txn, m.getMessage().getGroupId(), m.getMember(), VISIBLE);
		setPreviousMsgId(txn, m.getMessage().getGroupId(),
				m.getMessage().getId());
		attachJoinMessageAddedEvent(txn, m.getMessage(), meta, true);
	}

	@Override
	public void removePrivateGroup(Transaction txn, GroupId g)
			throws DbException {
		for (PrivateGroupHook hook : hooks) {
			hook.removingGroup(txn, g);
		}
		Group group = db.getGroup(txn, g);
		db.removeGroup(txn, group);
	}

	@Override
	public void removePrivateGroup(GroupId g) throws DbException {
		db.transaction(false, txn -> removePrivateGroup(txn, g));
	}

	@Override
	public MessageId getPreviousMsgId(GroupId g) throws DbException {
		return db.transactionWithResult(true,
				txn -> getPreviousMsgId(txn, g));
	}

	public MessageId getPreviousMsgId(Transaction txn, GroupId g)
			throws DbException {
		try {
			BdfDictionary d = clientHelper.getGroupMetadataAsDictionary(txn, g);
			byte[] previousMsgIdBytes = d.getRaw(KEY_PREVIOUS_MSG_ID);
			return new MessageId(previousMsgIdBytes);
		} catch (FormatException e) {
			throw new DbException(e);
		}
	}

	private void setPreviousMsgId(Transaction txn, GroupId g,
			MessageId previousMsgId) throws DbException, FormatException {
		BdfDictionary d = BdfDictionary
				.of(new BdfEntry(KEY_PREVIOUS_MSG_ID, previousMsgId));
		clientHelper.mergeGroupMetadata(txn, g, d);
	}

	@Override
	public void markGroupDissolved(Transaction txn, GroupId g)
			throws DbException {
		BdfDictionary meta = BdfDictionary.of(
				new BdfEntry(GROUP_KEY_DISSOLVED, true)
		);
		try {
			clientHelper.mergeGroupMetadata(txn, g, meta);
		} catch (FormatException e) {
			throw new DbException(e);
		}
		Event e = new GroupDissolvedEvent(g);
		txn.attach(e);
	}

	@Override
	public GroupMessageHeader addLocalMessage(GroupMessage m)
			throws DbException {
		return db.transactionWithResult(false, txn -> addLocalMessage(txn, m));
	}

	@Override
	public GroupMessageHeader addLocalMessage(Transaction txn, GroupMessage m)
			throws DbException {
		try {
			GroupId g = m.getMessage().getGroupId();
			MessageType type = m.getType();
			if (type == ADMIN_REQUEST) {
				if (!isMember(txn, g, m.getMember()) ||
						isAdmin(txn, g, m.getMember())) {
					throw new ProtocolStateException();
				}
			} else if (type == POST || type == IMAGE) {
				if (!isAdmin(txn, g, m.getMember()))
					throw new ProtocolStateException();
			} else {
				throw new ProtocolStateException();
			}
			// store message and metadata
			BdfDictionary meta = new BdfDictionary();
			meta.put(KEY_TYPE, type.getInt());
			if (m.getParent() != null)
				meta.put(KEY_PARENT_MSG_ID, m.getParent());
			addMessageMetadata(meta, m);
			clientHelper
					.addLocalMessage(txn, m.getMessage(), meta, true, false);
			// track message
			setPreviousMsgId(txn, g, m.getMessage().getId());
			messageTracker.trackOutgoingMessage(txn, m.getMessage());
			// broadcast event
			attachGroupMessageAddedEvent(txn, m.getMessage(), meta, true);
			AuthorInfo authorInfo = authorManager.getMyAuthorInfo(txn);
			return new GroupMessageHeader(m.getMessage().getGroupId(),
					m.getMessage().getId(), m.getParent(),
					m.getMessage().getTimestamp(), m.getMember(), authorInfo,
					true, type);
		} catch (FormatException e) {
			throw new DbException(e);
		}
	}

	@Override
	public void addLocalAdmin(AdminMessage message) throws DbException {
		db.transaction(false, txn -> addLocalAdmin(txn, message));
	}

	@Override
	public void addLocalAdmin(Transaction txn, AdminMessage adminMessage)
			throws DbException {
		try {
			Message message = adminMessage.getMessage();
			GroupId groupId = message.getGroupId();
			PrivateGroup group = getPrivateGroup(txn, groupId);
			LocalAuthor localAuthor = identityManager.getLocalAuthor(txn);
			if (!group.getCreator().getId().equals(localAuthor.getId()) ||
					!isMember(txn, groupId, adminMessage.getAdmin())) {
				throw new ProtocolStateException();
			}
			BdfDictionary meta = new BdfDictionary();
			meta.put(KEY_TYPE, ADMIN.getInt());
			meta.put(KEY_MEMBER,
					clientHelper.toList(adminMessage.getAdmin()));
			meta.put(KEY_TIMESTAMP, message.getTimestamp());
			meta.put(KEY_READ, true);
			clientHelper.addLocalMessage(txn, message, meta, true, false);
			addAdmin(txn, groupId, adminMessage.getAdmin(), message.getId());
		} catch (FormatException e) {
			throw new DbException(e);
		}
	}

	@Override
	public void addLocalAdminRemoval(AdminMessage message) throws DbException {
		db.transaction(false, txn -> addLocalAdminRemoval(txn, message));
	}

	@Override
	public void addLocalAdminRemoval(Transaction txn,
			AdminMessage adminMessage) throws DbException {
		try {
			Message message = adminMessage.getMessage();
			GroupId groupId = message.getGroupId();
			PrivateGroup group = getPrivateGroup(txn, groupId);
			LocalAuthor localAuthor = identityManager.getLocalAuthor(txn);
			Author admin = adminMessage.getAdmin();
			if (!group.getCreator().getId().equals(localAuthor.getId()) ||
					group.getCreator().getId().equals(admin.getId()) ||
					!isAdmin(txn, groupId, admin)) {
				throw new ProtocolStateException();
			}
			BdfDictionary meta = new BdfDictionary();
			meta.put(KEY_TYPE, ADMIN_REMOVED.getInt());
			meta.put(KEY_MEMBER, clientHelper.toList(admin));
			meta.put(KEY_TIMESTAMP, message.getTimestamp());
			meta.put(KEY_READ, true);
			clientHelper.addLocalMessage(txn, message, meta, true, false);
			removeAdmin(txn, groupId, admin);
		} catch (FormatException e) {
			throw new DbException(e);
		}
	}

	@Override
	public void addLocalMemberRemoval(MemberRemovalMessage message)
			throws DbException {
		db.transaction(false, txn -> addLocalMemberRemoval(txn, message));
	}

	@Override
	public void addLocalMemberRemoval(Transaction txn,
			MemberRemovalMessage removal) throws DbException {
		try {
			Message message = removal.getMessage();
			GroupId groupId = message.getGroupId();
			checkCanRemoveMember(txn, groupId, removal.getAdmin(),
					removal.getMember());
			BdfDictionary meta = new BdfDictionary();
			meta.put(KEY_TYPE, MEMBER_REMOVED.getInt());
			meta.put(KEY_MEMBER, clientHelper.toList(removal.getAdmin()));
			meta.put(KEY_TARGET_MEMBER,
					clientHelper.toList(removal.getMember()));
			meta.put(KEY_TIMESTAMP, message.getTimestamp());
			meta.put(KEY_READ, true);
			clientHelper.addLocalMessage(txn, message, meta, true, false);
			removeMember(txn, groupId, removal.getMember());
		} catch (FormatException e) {
			throw new DbException(e);
		}
	}

	@Override
	public void addLocalPin(PinMessage message) throws DbException {
		db.transaction(false, txn -> addLocalPin(txn, message));
	}

	@Override
	public void addLocalPin(Transaction txn, PinMessage pin)
			throws DbException {
		try {
			Message message = pin.getMessage();
			GroupId groupId = message.getGroupId();
			if (!isAdmin(txn, groupId, pin.getAdmin()))
				throw new ProtocolStateException();
			MessageId pinnedId = pin.getPinnedMessageId();
			if (pinnedId != null && !isPinnable(txn, pinnedId))
				throw new ProtocolStateException();
			BdfDictionary meta = new BdfDictionary();
			meta.put(KEY_TYPE, PIN.getInt());
			meta.put(KEY_MEMBER, clientHelper.toList(pin.getAdmin()));
			if (pinnedId != null) meta.put(KEY_PINNED_MSG_ID, pinnedId);
			meta.put(KEY_TIMESTAMP, message.getTimestamp());
			meta.put(KEY_READ, true);
			clientHelper.addLocalMessage(txn, message, meta, true, false);
			setPinnedMessage(txn, groupId, pinnedId, message.getTimestamp());
		} catch (FormatException e) {
			throw new DbException(e);
		}
	}

	private void addMessageMetadata(BdfDictionary meta, GroupMessage m) {
		meta.put(KEY_MEMBER, clientHelper.toList(m.getMember()));
		meta.put(KEY_TIMESTAMP, m.getMessage().getTimestamp());
		meta.put(KEY_READ, true);
	}

	@Override
	public PrivateGroup getPrivateGroup(GroupId g) throws DbException {
		PrivateGroup privateGroup;
		Transaction txn = db.startTransaction(true);
		try {
			privateGroup = getPrivateGroup(txn, g);
			db.commitTransaction(txn);
		} finally {
			db.endTransaction(txn);
		}
		return privateGroup;
	}

	@Override
	public PrivateGroup getPrivateGroup(Transaction txn, GroupId g)
			throws DbException {
		try {
			Group group = db.getGroup(txn, g);
			return privateGroupFactory.parsePrivateGroup(group);
		} catch (FormatException e) {
			throw new DbException(e);
		}
	}

	@Override
	public Collection<PrivateGroup> getPrivateGroups(Transaction txn)
			throws DbException {
		Collection<Group> groups = db.getGroups(txn, CLIENT_ID, MAJOR_VERSION);
		Collection<PrivateGroup> privateGroups = new ArrayList<>(groups.size());
		try {
			for (Group g : groups) {
				privateGroups.add(privateGroupFactory.parsePrivateGroup(g));
			}
		} catch (FormatException e) {
			throw new DbException(e);
		}
		return privateGroups;
	}

	@Override
	public boolean isOurPrivateGroup(Transaction txn, PrivateGroup g)
			throws DbException {
		LocalAuthor localAuthor = identityManager.getLocalAuthor(txn);
		return localAuthor.getId().equals(g.getCreator().getId());
	}

	@Override
	public Collection<PrivateGroup> getPrivateGroups() throws DbException {
		return db.transactionWithResult(true, this::getPrivateGroups);
	}

	@Override
	public boolean isDissolved(Transaction txn, GroupId g) throws DbException {
		try {
			BdfDictionary meta =
					clientHelper.getGroupMetadataAsDictionary(txn, g);
			return meta.getBoolean(GROUP_KEY_DISSOLVED);
		} catch (FormatException e) {
			throw new DbException(e);
		}
	}

	@Override
	public boolean isDissolved(GroupId g) throws DbException {
		return db.transactionWithResult(true, txn -> isDissolved(txn, g));
	}

	@Override
	public String getMessageText(MessageId m) throws DbException {
		try {
			return getMessageText(clientHelper.getMessageAsList(m));
		} catch (FormatException e) {
			throw new DbException(e);
		}
	}

	@Override
	public String getMessageText(Transaction txn, MessageId m)
			throws DbException {
		try {
			return getMessageText(clientHelper.getMessageAsList(txn, m));
		} catch (FormatException e) {
			throw new DbException(e);
		}
	}

	private String getMessageText(BdfList body) throws FormatException {
		MessageType type = MessageType.valueOf(body.getInt(0));
		if (type == ADMIN_REQUEST) return body.getString(3);
		if (type == IMAGE) {
			String caption = body.getOptionalString(4);
			return caption == null ? "" : caption;
		}
		return body.getString(4);
	}

	@Override
	public byte[] getMessageImage(Transaction txn, MessageId m)
			throws DbException {
		try {
			BdfList body = clientHelper.getMessageAsList(txn, m);
			if (MessageType.valueOf(body.getInt(0)) != IMAGE)
				throw new DbException();
			return body.getRaw(6);
		} catch (FormatException e) {
			throw new DbException(e);
		}
	}

	@Override
	@Nullable
	public MessageId getPinnedMessageId(Transaction txn, GroupId g)
			throws DbException {
		try {
			BdfDictionary meta =
					clientHelper.getGroupMetadataAsDictionary(txn, g);
			byte[] id = meta.getOptionalRaw(GROUP_KEY_PINNED_MSG_ID);
			return id == null ? null : new MessageId(id);
		} catch (FormatException e) {
			throw new DbException(e);
		}
	}

	@Override
	public Collection<GroupMessageHeader> getHeaders(GroupId g)
			throws DbException {
		return db.transactionWithResult(true, txn -> getHeaders(txn, g));
	}

	@Override
	public List<GroupMessageHeader> getHeaders(Transaction txn, GroupId g)
			throws DbException {
		List<GroupMessageHeader> headers = new ArrayList<>();
		try {
			Map<MessageId, BdfDictionary> metadata =
					clientHelper.getMessageMetadataAsDictionary(txn, g);
			LocalAuthor localAuthor = identityManager.getLocalAuthor(txn);
			boolean localAdmin = isAdmin(txn, g, localAuthor);
			// get all authors we need to get the information for
			Set<AuthorId> authors = new HashSet<>();
			for (BdfDictionary meta : metadata.values()) {
				if (!meta.containsKey(KEY_TYPE)) continue;
				MessageType type =
						MessageType.valueOf(meta.getInt(KEY_TYPE));
				if (isVisibleMessage(type))
					authors.add(getAuthor(meta).getId());
			}
			// get information for all authors
			Map<AuthorId, AuthorInfo> authorInfos = new HashMap<>();
			for (AuthorId id : authors) {
				authorInfos.put(id, authorManager.getAuthorInfo(txn, id));
			}
			// parse the metadata
			for (Entry<MessageId, BdfDictionary> entry : metadata.entrySet()) {
				BdfDictionary meta = entry.getValue();
				if (!meta.containsKey(KEY_TYPE)) continue;
				MessageType type =
						MessageType.valueOf(meta.getInt(KEY_TYPE));
				if (type == ADMIN_REQUEST && !localAdmin &&
						!getAuthor(meta).getId().equals(localAuthor.getId())) {
					continue;
				}
				if (!isVisibleMessage(type)) {
					continue;
				} else if (type == JOIN) {
					headers.add(getJoinMessageHeader(txn, g, entry.getKey(),
							meta, authorInfos));
				} else {
					headers.add(getGroupMessageHeader(txn, g, entry.getKey(),
							meta, authorInfos));
				}
			}
			return headers;
		} catch (FormatException e) {
			throw new DbException(e);
		}
	}

	private GroupMessageHeader getGroupMessageHeader(Transaction txn, GroupId g,
			MessageId id, BdfDictionary meta,
			Map<AuthorId, AuthorInfo> authorInfos)
			throws DbException, FormatException {

		MessageId parentId = null;
		if (meta.containsKey(KEY_PARENT_MSG_ID)) {
			parentId = new MessageId(meta.getRaw(KEY_PARENT_MSG_ID));
		}
		long timestamp = meta.getLong(KEY_TIMESTAMP);

		Author member = getAuthor(meta);
		AuthorInfo authorInfo;
		if (authorInfos.containsKey(member.getId())) {
			authorInfo = authorInfos.get(member.getId());
		} else {
			authorInfo = authorManager.getAuthorInfo(txn, member.getId());
		}
		boolean read = meta.getBoolean(KEY_READ);

		MessageType type = MessageType.valueOf(meta.getInt(KEY_TYPE));
		return new GroupMessageHeader(g, id, parentId, timestamp, member,
				authorInfo, read, type);
	}

	private boolean isVisibleMessage(MessageType type) {
		return type == JOIN || type == POST || type == IMAGE ||
				type == ADMIN_REQUEST;
	}

	private JoinMessageHeader getJoinMessageHeader(Transaction txn, GroupId g,
			MessageId id, BdfDictionary meta,
			Map<AuthorId, AuthorInfo> authorInfos)
			throws DbException, FormatException {

		GroupMessageHeader header =
				getGroupMessageHeader(txn, g, id, meta, authorInfos);
		boolean creator = meta.getBoolean(KEY_INITIAL_JOIN_MSG);
		return new JoinMessageHeader(header, creator);
	}

	@Override
	public Collection<GroupMember> getMembers(GroupId g) throws DbException {
		return db.transactionWithResult(true, txn -> getMembers(txn, g));
	}

	@Override
	public Collection<GroupMember> getMembers(Transaction txn, GroupId g)
			throws DbException {
		Collection<GroupMember> members = new ArrayList<>();
		Map<Author, Visibility> authors = getMemberAuthors(txn, g);
		LocalAuthor la = identityManager.getLocalAuthor(txn);
		PrivateGroup privateGroup = getPrivateGroup(txn, g);
		Set<AuthorId> admins = getAdminIds(txn, g);
		for (Entry<Author, Visibility> m : authors.entrySet()) {
			Author a = m.getKey();
			AuthorInfo authorInfo = authorManager.getAuthorInfo(txn, a.getId());
			Status status = authorInfo.getStatus();
			Visibility v = m.getValue();
			ContactId c = null;
			if (v != INVISIBLE &&
					(status == VERIFIED || status == UNVERIFIED)) {
				c = contactManager.getContact(txn, a.getId(), la.getId())
						.getId();
			}
			boolean isCreator = privateGroup.getCreator().equals(a);
			boolean isAdmin = admins.contains(a.getId());
			members.add(new GroupMember(a, authorInfo, isCreator, isAdmin, c,
					v));
		}
		return members;
	}

	private Map<Author, Visibility> getMemberAuthors(Transaction txn, GroupId g)
			throws DbException {
		try {
			BdfDictionary meta =
					clientHelper.getGroupMetadataAsDictionary(txn, g);
			BdfList list = meta.getList(GROUP_KEY_MEMBERS);
			Map<Author, Visibility> members = new HashMap<>(list.size());
			for (int i = 0; i < list.size(); i++) {
				BdfDictionary d = list.getDictionary(i);
				Author member = getAuthor(d);
				Visibility v = getVisibility(d);
				members.put(member, v);
			}
			return members;
		} catch (FormatException e) {
			throw new DbException(e);
		}
	}

	@Override
	public boolean isMember(Transaction txn, GroupId g, Author a)
			throws DbException {
		for (Author member : getMemberAuthors(txn, g).keySet()) {
			if (member.equals(a)) return true;
		}
		return false;
	}

	@Override
	public boolean isAdmin(Transaction txn, GroupId g, Author a)
			throws DbException {
		return getAdminIds(txn, g).contains(a.getId());
	}

	@Override
	public boolean isAdmin(GroupId g, Author a) throws DbException {
		return db.transactionWithResult(true, txn -> isAdmin(txn, g, a));
	}

	private Set<AuthorId> getAdminIds(Transaction txn, GroupId g)
			throws DbException {
		try {
			BdfDictionary meta =
					clientHelper.getGroupMetadataAsDictionary(txn, g);
			Set<AuthorId> admins = new HashSet<>();
			if (meta.containsKey(GROUP_KEY_ADMINS)) {
				BdfList list = meta.getList(GROUP_KEY_ADMINS);
				for (int i = 0; i < list.size(); i++) {
					Author admin =
							clientHelper.parseAndValidateAuthor(list.getList(i));
					admins.add(admin.getId());
				}
			} else {
				admins.add(getPrivateGroup(txn, g).getCreator().getId());
			}
			return admins;
		} catch (FormatException e) {
			throw new DbException(e);
		}
	}

	@Override
	public GroupCount getGroupCount(Transaction txn, GroupId g)
			throws DbException {
		return messageTracker.getGroupCount(txn, g);
	}

	@Override
	public GroupCount getGroupCount(GroupId g) throws DbException {
		return messageTracker.getGroupCount(g);
	}

	@Override
	public void setReadFlag(Transaction txn, GroupId g, MessageId m,
			boolean read) throws DbException {
		messageTracker.setReadFlag(txn, g, m, read);
	}

	@Override
	public void setReadFlag(GroupId g, MessageId m, boolean read)
			throws DbException {
		db.transaction(false, txn -> setReadFlag(txn, g, m, read));
	}

	@Override
	public void relationshipRevealed(Transaction txn, GroupId g, AuthorId a,
			boolean byContact) throws FormatException, DbException {
		BdfDictionary meta = clientHelper.getGroupMetadataAsDictionary(txn, g);
		BdfList members = meta.getList(GROUP_KEY_MEMBERS);
		Visibility v = INVISIBLE;
		boolean foundMember = false, changed = false;
		for (int i = 0; i < members.size(); i++) {
			BdfDictionary d = members.getDictionary(i);
			if (a.equals(getAuthor(d).getId())) {
				foundMember = true;
				// Don't update the visibility if the contact is already visible
				if (getVisibility(d) == INVISIBLE) {
					changed = true;
					v = byContact ? REVEALED_BY_CONTACT : REVEALED_BY_US;
					d.put(GROUP_KEY_VISIBILITY, v.getInt());
				}
				break;
			}
		}
		if (!foundMember) throw new ProtocolStateException();
		if (changed) {
			clientHelper.mergeGroupMetadata(txn, g, meta);
			LocalAuthor la = identityManager.getLocalAuthor(txn);
			ContactId c = contactManager.getContact(txn, a, la.getId()).getId();
			Event e = new ContactRelationshipRevealedEvent(g, a, c, v);
			txn.attach(e);
		}
	}

	@Override
	public void registerPrivateGroupHook(PrivateGroupHook hook) {
		hooks.add(hook);
	}

	@Override
	protected DeliveryAction incomingMessage(Transaction txn, Message m,
			BdfList body, BdfDictionary meta)
			throws DbException, FormatException {

		MessageType type = MessageType.valueOf(meta.getInt(KEY_TYPE));
		switch (type) {
			case JOIN:
				handleJoinMessage(txn, m, meta);
				return ACCEPT_SHARE;
			case POST:
				handleGroupMessage(txn, m, meta);
				return ACCEPT_SHARE;
			case ADMIN:
				handleAdminMessage(txn, m, meta);
				return ACCEPT_SHARE;
			case ADMIN_REMOVED:
				handleAdminRemovedMessage(txn, m, meta);
				return ACCEPT_SHARE;
			case MEMBER_REMOVED:
				handleMemberRemovedMessage(txn, m, meta);
				return ACCEPT_SHARE;
			case ADMIN_REQUEST:
				handleSequencedMessage(txn, m, meta, false);
				return ACCEPT_SHARE;
			case IMAGE:
				handleSequencedMessage(txn, m, meta, true);
				return ACCEPT_SHARE;
			case PIN:
				handlePinMessage(txn, m, meta);
				return ACCEPT_SHARE;
			default:
				// the validator should only let valid types pass
				throw new RuntimeException("Unknown MessageType");
		}
	}

	private void handleJoinMessage(Transaction txn, Message m,
			BdfDictionary meta) throws FormatException, DbException {
		if (meta.containsKey(KEY_INVITER)) {
			Author inviter = clientHelper.parseAndValidateAuthor(
					meta.getList(KEY_INVITER));
			if (!isMember(txn, m.getGroupId(), inviter))
				throw new FormatException();
		}
		// find out if contact relationship is visible and then add new member
		Author member = getAuthor(meta);
		BdfDictionary groupMeta = clientHelper
				.getGroupMetadataAsDictionary(txn, m.getGroupId());
		boolean ourGroup = groupMeta.getBoolean(GROUP_KEY_OUR_GROUP);
		Visibility v = VISIBLE;
		if (!ourGroup) {
			AuthorId creatorId = new AuthorId(
					groupMeta.getRaw(GROUP_KEY_CREATOR_ID));
			if (!creatorId.equals(member.getId()))
				v = INVISIBLE;
		}
		addMember(txn, m.getGroupId(), member, v);
		// track message and broadcast event
		messageTracker.trackIncomingMessage(txn, m);
		attachJoinMessageAddedEvent(txn, m, meta, false);
	}

	private void handleGroupMessage(Transaction txn, Message m,
			BdfDictionary meta) throws FormatException, DbException {
		handleSequencedMessage(txn, m, meta, true);
	}

	private void handleSequencedMessage(Transaction txn, Message m,
			BdfDictionary meta, boolean adminOnly)
			throws FormatException, DbException {
		Author author = getAuthor(meta);
		if (!isMember(txn, m.getGroupId(), author))
			throw new FormatException();
		if (adminOnly && !isAdmin(txn, m.getGroupId(), author))
			throw new FormatException();
		if (!adminOnly && isAdmin(txn, m.getGroupId(), author))
			throw new FormatException();
		// timestamp must be greater than the timestamps of parent post
		long timestamp = meta.getLong(KEY_TIMESTAMP);
		byte[] parentIdBytes = meta.getOptionalRaw(KEY_PARENT_MSG_ID);
		if (parentIdBytes != null) {
			MessageId parentId = new MessageId(parentIdBytes);
			BdfDictionary parentMeta = clientHelper
					.getMessageMetadataAsDictionary(txn, parentId);
			if (timestamp <= parentMeta.getLong(KEY_TIMESTAMP))
				throw new FormatException();
			MessageType parentType =
					MessageType.valueOf(parentMeta.getInt(KEY_TYPE));
			if (parentType != POST && parentType != IMAGE)
				throw new FormatException();
		}
		// and the member's previous message
		byte[] previousMsgIdBytes = meta.getRaw(KEY_PREVIOUS_MSG_ID);
		MessageId previousMsgId = new MessageId(previousMsgIdBytes);
		BdfDictionary previousMeta = clientHelper
				.getMessageMetadataAsDictionary(txn, previousMsgId);
		if (timestamp <= previousMeta.getLong(KEY_TIMESTAMP))
			throw new FormatException();
		// previous message must be from same member
		if (!author.equals(getAuthor(previousMeta)))
			throw new FormatException();
		// previous message must be a POST or JOIN
		MessageType previousType =
				MessageType.valueOf(previousMeta.getInt(KEY_TYPE));
		if (previousType != JOIN && previousType != POST &&
				previousType != ADMIN && previousType != IMAGE &&
				previousType != ADMIN_REQUEST)
			throw new FormatException();
		// Admin requests are deliberately surfaced only to channel admins.
		LocalAuthor localAuthor = identityManager.getLocalAuthor(txn);
		if (adminOnly || isAdmin(txn, m.getGroupId(), localAuthor)) {
			messageTracker.trackIncomingMessage(txn, m);
			attachGroupMessageAddedEvent(txn, m, meta, false);
		}
	}

	private void handleAdminMessage(Transaction txn, Message m,
			BdfDictionary meta) throws DbException, FormatException {
		Author admin = getAuthor(meta);
		if (!isMember(txn, m.getGroupId(), admin))
			throw new FormatException();
		addAdmin(txn, m.getGroupId(), admin, m.getId());
	}

	private void handleAdminRemovedMessage(Transaction txn, Message m,
			BdfDictionary meta) throws DbException, FormatException {
		Author admin = getAuthor(meta);
		PrivateGroup group = getPrivateGroup(txn, m.getGroupId());
		if (group.getCreator().getId().equals(admin.getId()))
			throw new FormatException();
		removeAdmin(txn, m.getGroupId(), admin);
	}

	private void handleMemberRemovedMessage(Transaction txn, Message m,
			BdfDictionary meta) throws DbException, FormatException {
		Author admin = getAuthor(meta);
		Author member = clientHelper.parseAndValidateAuthor(
				meta.getList(KEY_TARGET_MEMBER));
		checkCanRemoveMember(txn, m.getGroupId(), admin, member);
		removeMember(txn, m.getGroupId(), member);
	}

	private void handlePinMessage(Transaction txn, Message m,
			BdfDictionary meta) throws DbException, FormatException {
		Author admin = getAuthor(meta);
		if (!isAdmin(txn, m.getGroupId(), admin))
			throw new FormatException();
		MessageId pinnedId = null;
		if (meta.containsKey(KEY_PINNED_MSG_ID)) {
			pinnedId = new MessageId(meta.getRaw(KEY_PINNED_MSG_ID));
			if (!isPinnable(txn, pinnedId)) throw new FormatException();
		}
		setPinnedMessage(txn, m.getGroupId(), pinnedId, m.getTimestamp());
	}

	private void addAdmin(Transaction txn, GroupId groupId, Author admin,
			MessageId appointmentId) throws DbException, FormatException {
		BdfDictionary groupMeta =
				clientHelper.getGroupMetadataAsDictionary(txn, groupId);
		BdfList admins;
		if (groupMeta.containsKey(GROUP_KEY_ADMINS)) {
			admins = groupMeta.getList(GROUP_KEY_ADMINS);
		} else {
			// Groups created by older clients implicitly have one admin.
			admins = new BdfList();
			admins.add(clientHelper.toList(
					getPrivateGroup(txn, groupId).getCreator()));
		}
		for (int i = 0; i < admins.size(); i++) {
			Author existing =
					clientHelper.parseAndValidateAuthor(admins.getList(i));
			if (existing.getId().equals(admin.getId())) return;
		}
		admins.add(clientHelper.toList(admin));
		groupMeta.put(GROUP_KEY_ADMINS, admins);
		clientHelper.mergeGroupMetadata(txn, groupId, groupMeta);
		LocalAuthor localAuthor = identityManager.getLocalAuthor(txn);
		if (localAuthor.getId().equals(admin.getId()))
			setPreviousMsgId(txn, groupId, appointmentId);
		txn.attach(new GroupAdminAddedEvent(groupId, admin));
	}

	private void removeAdmin(Transaction txn, GroupId groupId, Author admin)
			throws DbException, FormatException {
		PrivateGroup group = getPrivateGroup(txn, groupId);
		if (group.getCreator().getId().equals(admin.getId()))
			throw new FormatException();
		BdfDictionary groupMeta =
				clientHelper.getGroupMetadataAsDictionary(txn, groupId);
		BdfList oldAdmins = groupMeta.getList(GROUP_KEY_ADMINS);
		BdfList newAdmins = new BdfList();
		boolean removed = false;
		for (int i = 0; i < oldAdmins.size(); i++) {
			BdfList list = oldAdmins.getList(i);
			Author existing = clientHelper.parseAndValidateAuthor(list);
			if (existing.getId().equals(admin.getId())) {
				removed = true;
			} else {
				newAdmins.add(list);
			}
		}
		if (!removed) return;
		groupMeta.put(GROUP_KEY_ADMINS, newAdmins);
		clientHelper.mergeGroupMetadata(txn, groupId, groupMeta);
		txn.attach(new GroupAdminRemovedEvent(groupId, admin));
	}

	private void checkCanRemoveMember(Transaction txn, GroupId groupId,
			Author admin, Author member) throws DbException, FormatException {
		if (!isAdmin(txn, groupId, admin) ||
				!isMember(txn, groupId, member)) {
			throw new FormatException();
		}
		PrivateGroup group = getPrivateGroup(txn, groupId);
		if (group.getCreator().getId().equals(member.getId()))
			throw new FormatException();
		if (isAdmin(txn, groupId, member) &&
				!group.getCreator().getId().equals(admin.getId())) {
			throw new FormatException();
		}
	}

	private void removeMember(Transaction txn, GroupId groupId, Author member)
			throws DbException, FormatException {
		BdfDictionary groupMeta =
				clientHelper.getGroupMetadataAsDictionary(txn, groupId);
		BdfList oldMembers = groupMeta.getList(GROUP_KEY_MEMBERS);
		BdfList newMembers = new BdfList();
		boolean removed = false;
		for (int i = 0; i < oldMembers.size(); i++) {
			BdfDictionary entry = oldMembers.getDictionary(i);
			if (getAuthor(entry).getId().equals(member.getId())) {
				removed = true;
			} else {
				newMembers.add(entry);
			}
		}
		if (!removed) return;
		groupMeta.put(GROUP_KEY_MEMBERS, newMembers);
		LocalAuthor localAuthor = identityManager.getLocalAuthor(txn);
		if (localAuthor.getId().equals(member.getId())) {
			groupMeta.put(GROUP_KEY_DISSOLVED, true);
			groupMeta.put(GROUP_KEY_KICKED, true);
			txn.attach(new GroupDissolvedEvent(groupId));
		}
		clientHelper.mergeGroupMetadata(txn, groupId, groupMeta);
		if (isAdmin(txn, groupId, member))
			removeAdmin(txn, groupId, member);
		txn.attach(new GroupMemberRemovedEvent(groupId, member));
	}

	private boolean isPinnable(Transaction txn, MessageId messageId)
			throws DbException, FormatException {
		BdfDictionary meta =
				clientHelper.getMessageMetadataAsDictionary(txn, messageId);
		MessageType type = MessageType.valueOf(meta.getInt(KEY_TYPE));
		return type == POST || type == IMAGE;
	}

	private void setPinnedMessage(Transaction txn, GroupId groupId,
			@Nullable MessageId messageId, long timestamp)
			throws DbException, FormatException {
		BdfDictionary groupMeta =
				clientHelper.getGroupMetadataAsDictionary(txn, groupId);
		long currentTimestamp =
				groupMeta.getLong(GROUP_KEY_PIN_TIMESTAMP, Long.MIN_VALUE);
		if (timestamp < currentTimestamp) return;
		groupMeta.put(GROUP_KEY_PIN_TIMESTAMP, timestamp);
		groupMeta.put(GROUP_KEY_PINNED_MSG_ID, messageId == null
				? BdfDictionary.NULL_VALUE : messageId);
		clientHelper.mergeGroupMetadata(txn, groupId, groupMeta);
		txn.attach(new GroupPinnedMessageChangedEvent(groupId, messageId));
	}

	private void attachGroupMessageAddedEvent(Transaction txn, Message m,
			BdfDictionary meta, boolean local)
			throws DbException, FormatException {
		GroupMessageHeader header = getGroupMessageHeader(txn, m.getGroupId(),
				m.getId(), meta, Collections.emptyMap());
		String text = getMessageText(clientHelper.toList(m));
		txn.attach(new GroupMessageAddedEvent(m.getGroupId(), header, text,
				local));
	}

	private void attachJoinMessageAddedEvent(Transaction txn, Message m,
			BdfDictionary meta, boolean local)
			throws DbException, FormatException {
		JoinMessageHeader header = getJoinMessageHeader(txn, m.getGroupId(),
				m.getId(), meta, Collections.emptyMap());
		txn.attach(new GroupMessageAddedEvent(m.getGroupId(), header, "",
				local));
	}

	private void addMember(Transaction txn, GroupId g, Author a, Visibility v)
			throws DbException, FormatException {

		BdfDictionary meta = clientHelper.getGroupMetadataAsDictionary(txn, g);
		BdfList members = meta.getList(GROUP_KEY_MEMBERS);
		members.add(BdfDictionary.of(
				new BdfEntry(KEY_MEMBER, clientHelper.toList(a)),
				new BdfEntry(GROUP_KEY_VISIBILITY, v.getInt())
		));
		clientHelper.mergeGroupMetadata(txn, g, meta);
		for (PrivateGroupHook hook : hooks) {
			hook.addingMember(txn, g, a);
		}
	}

	private Author getAuthor(BdfDictionary meta) throws FormatException {
		return clientHelper.parseAndValidateAuthor(meta.getList(KEY_MEMBER));
	}

	private Visibility getVisibility(BdfDictionary meta)
			throws FormatException {
		return Visibility.valueOf(meta.getInt(GROUP_KEY_VISIBILITY));
	}

}
