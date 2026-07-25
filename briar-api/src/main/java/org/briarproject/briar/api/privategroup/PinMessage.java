package org.briarproject.briar.api.privategroup;

import org.briarproject.bramble.api.identity.Author;
import org.briarproject.bramble.api.sync.Message;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.nullsafety.NotNullByDefault;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

@Immutable
@NotNullByDefault
public class PinMessage {

	private final Message message;
	private final Author admin;
	@Nullable
	private final MessageId pinnedMessageId;

	public PinMessage(Message message, Author admin,
			@Nullable MessageId pinnedMessageId) {
		this.message = message;
		this.admin = admin;
		this.pinnedMessageId = pinnedMessageId;
	}

	public Message getMessage() {
		return message;
	}

	public Author getAdmin() {
		return admin;
	}

	@Nullable
	public MessageId getPinnedMessageId() {
		return pinnedMessageId;
	}

}
