package org.briarproject.briar.api.privategroup;

import org.briarproject.bramble.api.identity.Author;
import org.briarproject.bramble.api.sync.Message;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.briar.api.client.ThreadedMessage;
import org.briarproject.nullsafety.NotNullByDefault;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

@Immutable
@NotNullByDefault
public class GroupMessage extends ThreadedMessage {

	private final MessageType type;

	public GroupMessage(Message message, @Nullable MessageId parent,
			Author member) {
		this(message, parent, member, MessageType.POST);
	}

	public GroupMessage(Message message, @Nullable MessageId parent,
			Author member, MessageType type) {
		super(message, parent, member);
		this.type = type;
	}

	public Author getMember() {
		return super.getAuthor();
	}

	public MessageType getType() {
		return type;
	}

}
