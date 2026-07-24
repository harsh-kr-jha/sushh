package org.briarproject.briar.api.privategroup;

import org.briarproject.bramble.api.identity.Author;
import org.briarproject.bramble.api.sync.Message;
import org.briarproject.nullsafety.NotNullByDefault;

import javax.annotation.concurrent.Immutable;

@Immutable
@NotNullByDefault
public class AdminMessage {

	private final Message message;
	private final Author admin;

	public AdminMessage(Message message, Author admin) {
		this.message = message;
		this.admin = admin;
	}

	public Message getMessage() {
		return message;
	}

	public Author getAdmin() {
		return admin;
	}

}
