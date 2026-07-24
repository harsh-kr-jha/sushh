package org.briarproject.briar.api.privategroup.event;

import org.briarproject.bramble.api.event.Event;
import org.briarproject.bramble.api.identity.Author;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.nullsafety.NotNullByDefault;

import javax.annotation.concurrent.Immutable;

@Immutable
@NotNullByDefault
public class GroupAdminRemovedEvent extends Event {

	private final GroupId groupId;
	private final Author admin;

	public GroupAdminRemovedEvent(GroupId groupId, Author admin) {
		this.groupId = groupId;
		this.admin = admin;
	}

	public GroupId getGroupId() {
		return groupId;
	}

	public Author getAdmin() {
		return admin;
	}

}
