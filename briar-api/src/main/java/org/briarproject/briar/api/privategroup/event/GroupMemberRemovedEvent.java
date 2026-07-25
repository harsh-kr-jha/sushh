package org.briarproject.briar.api.privategroup.event;

import org.briarproject.bramble.api.event.Event;
import org.briarproject.bramble.api.identity.Author;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.nullsafety.NotNullByDefault;

import javax.annotation.concurrent.Immutable;

@Immutable
@NotNullByDefault
public class GroupMemberRemovedEvent extends Event {

	private final GroupId groupId;
	private final Author member;

	public GroupMemberRemovedEvent(GroupId groupId, Author member) {
		this.groupId = groupId;
		this.member = member;
	}

	public GroupId getGroupId() {
		return groupId;
	}

	public Author getMember() {
		return member;
	}

}
