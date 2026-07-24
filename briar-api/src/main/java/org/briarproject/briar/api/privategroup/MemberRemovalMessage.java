package org.briarproject.briar.api.privategroup;

import org.briarproject.bramble.api.identity.Author;
import org.briarproject.bramble.api.sync.Message;
import org.briarproject.nullsafety.NotNullByDefault;

import javax.annotation.concurrent.Immutable;

@Immutable
@NotNullByDefault
public class MemberRemovalMessage {

	private final Message message;
	private final Author admin;
	private final Author member;

	public MemberRemovalMessage(Message message, Author admin, Author member) {
		this.message = message;
		this.admin = admin;
		this.member = member;
	}

	public Message getMessage() {
		return message;
	}

	public Author getAdmin() {
		return admin;
	}

	public Author getMember() {
		return member;
	}

}
