package org.briarproject.briar.android.contact.add.nearby;

import org.briarproject.bramble.api.contact.Contact;
import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.contact.ContactManager;
import org.briarproject.bramble.api.db.DatabaseExecutor;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.db.TransactionManager;
import org.briarproject.bramble.api.identity.Author;
import org.briarproject.bramble.api.identity.IdentityManager;
import org.briarproject.bramble.api.identity.LocalAuthor;
import org.briarproject.bramble.api.lifecycle.LifecycleManager;
import org.briarproject.bramble.api.versioning.ClientVersioningManager;
import org.briarproject.briar.android.controller.DbControllerImpl;
import org.briarproject.briar.android.controller.handler.ResultExceptionHandler;
import org.briarproject.briar.api.privategroup.PrivateGroup;
import org.briarproject.briar.api.privategroup.invitation.GroupInvitationManager;
import org.briarproject.nullsafety.NotNullByDefault;

import java.util.concurrent.Executor;
import java.util.logging.Logger;

import javax.inject.Inject;

import static java.util.logging.Level.WARNING;
import static org.briarproject.bramble.util.LogUtils.logException;

@NotNullByDefault
class NearbyChannelJoinController extends DbControllerImpl {

	private static final Logger LOG =
			Logger.getLogger(NearbyChannelJoinController.class.getName());

	private final ContactManager contactManager;
	private final IdentityManager identityManager;
	private final TransactionManager db;
	private final ClientVersioningManager clientVersioningManager;
	private final GroupInvitationManager groupInvitationManager;

	@Inject
	NearbyChannelJoinController(@DatabaseExecutor Executor dbExecutor,
			LifecycleManager lifecycleManager, ContactManager contactManager,
			IdentityManager identityManager,
			TransactionManager db,
			ClientVersioningManager clientVersioningManager,
			GroupInvitationManager groupInvitationManager) {
		super(dbExecutor, lifecycleManager);
		this.contactManager = contactManager;
		this.identityManager = identityManager;
		this.db = db;
		this.clientVersioningManager = clientVersioningManager;
		this.groupInvitationManager = groupInvitationManager;
	}

	void getContactId(Author remoteAuthor,
			ResultExceptionHandler<ContactId, DbException> handler) {
		runOnDbThread(() -> {
			try {
				LocalAuthor localAuthor = identityManager.getLocalAuthor();
				Contact contact = contactManager.getContact(
						remoteAuthor.getId(), localAuthor.getId());
				handler.onResult(contact.getId());
			} catch (DbException e) {
				logException(LOG, WARNING, e);
				handler.onException(e);
			}
		});
	}

	void supportsChannelJoin(ContactId contactId,
			ResultExceptionHandler<Boolean, DbException> handler) {
		runOnDbThread(() -> {
			try {
				int minorVersion = db.transactionWithResult(true, txn ->
						clientVersioningManager.getClientMinorVersion(txn,
								contactId, GroupInvitationManager.CLIENT_ID,
								GroupInvitationManager.MAJOR_VERSION));
				handler.onResult(minorVersion >=
						GroupInvitationManager.MINOR_VERSION);
			} catch (DbException e) {
				logException(LOG, WARNING, e);
				handler.onException(e);
			}
		});
	}

	void acceptInvitation(ContactId contactId, PrivateGroup channel,
			ResultExceptionHandler<Void, DbException> handler) {
		runOnDbThread(() -> {
			try {
				groupInvitationManager.respondToInvitation(contactId, channel,
						true);
				handler.onResult(null);
			} catch (DbException e) {
				logException(LOG, WARNING, e);
				handler.onException(e);
			}
		});
	}
}
