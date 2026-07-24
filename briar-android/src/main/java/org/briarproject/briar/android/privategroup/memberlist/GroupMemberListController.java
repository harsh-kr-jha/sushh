package org.briarproject.briar.android.privategroup.memberlist;

import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.identity.Author;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.briar.android.controller.DbController;
import org.briarproject.briar.android.controller.handler.ResultExceptionHandler;

import java.util.Collection;

public interface GroupMemberListController extends DbController {

	void loadMembers(GroupId groupId,
			ResultExceptionHandler<Collection<MemberListItem>, DbException> handler);

	void appointAdmin(GroupId groupId, Author admin,
			ResultExceptionHandler<Void, DbException> handler);

	void removeAdmin(GroupId groupId, Author admin,
			ResultExceptionHandler<Void, DbException> handler);

	void removeMember(GroupId groupId, Author member,
			ResultExceptionHandler<Void, DbException> handler);

}
