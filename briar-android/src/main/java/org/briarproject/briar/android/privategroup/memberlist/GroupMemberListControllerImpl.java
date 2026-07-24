package org.briarproject.briar.android.privategroup.memberlist;

import org.briarproject.bramble.api.connection.ConnectionRegistry;
import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.crypto.CryptoExecutor;
import org.briarproject.bramble.api.db.DatabaseExecutor;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.identity.Author;
import org.briarproject.bramble.api.identity.IdentityManager;
import org.briarproject.bramble.api.identity.LocalAuthor;
import org.briarproject.bramble.api.lifecycle.LifecycleManager;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.system.Clock;
import org.briarproject.briar.android.controller.DbControllerImpl;
import org.briarproject.briar.android.controller.handler.ResultExceptionHandler;
import org.briarproject.briar.api.privategroup.AdminMessage;
import org.briarproject.briar.api.privategroup.GroupMember;
import org.briarproject.briar.api.privategroup.GroupMessageFactory;
import org.briarproject.briar.api.privategroup.PrivateGroupManager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

import javax.inject.Inject;

import static java.util.logging.Level.WARNING;
import static org.briarproject.bramble.util.LogUtils.logException;

class GroupMemberListControllerImpl extends DbControllerImpl
		implements GroupMemberListController {

	private static final Logger LOG =
			Logger.getLogger(GroupMemberListControllerImpl.class.getName());

	private final ConnectionRegistry connectionRegistry;
	private final PrivateGroupManager privateGroupManager;
	private final GroupMessageFactory groupMessageFactory;
	private final IdentityManager identityManager;
	private final Clock clock;
	private final Executor cryptoExecutor;

	@Inject
	GroupMemberListControllerImpl(@DatabaseExecutor Executor dbExecutor,
			@CryptoExecutor Executor cryptoExecutor,
			LifecycleManager lifecycleManager,
			ConnectionRegistry connectionRegistry,
			PrivateGroupManager privateGroupManager,
			GroupMessageFactory groupMessageFactory,
			IdentityManager identityManager, Clock clock) {
		super(dbExecutor, lifecycleManager);
		this.cryptoExecutor = cryptoExecutor;
		this.connectionRegistry = connectionRegistry;
		this.privateGroupManager = privateGroupManager;
		this.groupMessageFactory = groupMessageFactory;
		this.identityManager = identityManager;
		this.clock = clock;
	}

	@Override
	public void loadMembers(GroupId groupId,
			ResultExceptionHandler<Collection<MemberListItem>, DbException> handler) {
		runOnDbThread(() -> {
			try {
				Collection<MemberListItem> items = new ArrayList<>();
				Collection<GroupMember> members =
						privateGroupManager.getMembers(groupId);
				for (GroupMember m : members) {
					ContactId c = m.getContactId();
					boolean online = false;
					if (c != null)
						online = connectionRegistry.isConnected(c);
					items.add(new MemberListItem(m, online));
				}
				handler.onResult(items);
			} catch (DbException e) {
				logException(LOG, WARNING, e);
				handler.onException(e);
			}
		});
	}

	@Override
	public void appointAdmin(GroupId groupId, Author admin,
			ResultExceptionHandler<Void, DbException> handler) {
		runOnDbThread(() -> {
			try {
				LocalAuthor creator = identityManager.getLocalAuthor();
				long latest = privateGroupManager.getGroupCount(groupId)
						.getLatestMsgTime();
				long timestamp = Math.max(clock.currentTimeMillis(), latest + 1);
				cryptoExecutor.execute(() -> {
					AdminMessage message = groupMessageFactory
							.createAdminMessage(groupId, timestamp, creator,
									admin);
					runOnDbThread(() -> {
						try {
							privateGroupManager.addLocalAdmin(message);
							handler.onResult(null);
						} catch (DbException e) {
							logException(LOG, WARNING, e);
							handler.onException(e);
						}
					});
				});
			} catch (DbException e) {
				logException(LOG, WARNING, e);
				handler.onException(e);
			}
		});
	}

}
