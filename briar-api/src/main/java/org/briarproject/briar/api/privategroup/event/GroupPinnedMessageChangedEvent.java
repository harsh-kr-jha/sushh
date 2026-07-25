package org.briarproject.briar.api.privategroup.event;

import org.briarproject.bramble.api.event.Event;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.nullsafety.NotNullByDefault;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

@Immutable
@NotNullByDefault
public class GroupPinnedMessageChangedEvent extends Event {

	private final GroupId groupId;
	@Nullable
	private final MessageId messageId;

	public GroupPinnedMessageChangedEvent(GroupId groupId,
			@Nullable MessageId messageId) {
		this.groupId = groupId;
		this.messageId = messageId;
	}

	public GroupId getGroupId() {
		return groupId;
	}

	@Nullable
	public MessageId getMessageId() {
		return messageId;
	}

}
