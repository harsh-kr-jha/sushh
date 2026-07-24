package org.briarproject.briar.api.privategroup;

import org.briarproject.bramble.api.identity.Author;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.briar.api.client.PostHeader;
import org.briarproject.briar.api.identity.AuthorInfo;
import org.briarproject.nullsafety.NotNullByDefault;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

@Immutable
@NotNullByDefault
public class GroupMessageHeader extends PostHeader {

	private final GroupId groupId;
	private final MessageType type;

	public GroupMessageHeader(GroupId groupId, MessageId id,
			@Nullable MessageId parentId, long timestamp,
			Author author, AuthorInfo authorInfo, boolean read) {
		this(groupId, id, parentId, timestamp, author, authorInfo, read,
				MessageType.POST);
	}

	public GroupMessageHeader(GroupId groupId, MessageId id,
			@Nullable MessageId parentId, long timestamp,
			Author author, AuthorInfo authorInfo, boolean read,
			MessageType type) {
		super(id, parentId, timestamp, author, authorInfo, read);
		this.groupId = groupId;
		this.type = type;
	}

	public GroupId getGroupId() {
		return groupId;
	}

	public MessageType getType() {
		return type;
	}

}
