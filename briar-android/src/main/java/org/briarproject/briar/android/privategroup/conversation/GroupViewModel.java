package org.briarproject.briar.android.privategroup.conversation;

import android.app.Application;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.crypto.CryptoExecutor;
import org.briarproject.bramble.api.db.DatabaseExecutor;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.db.Transaction;
import org.briarproject.bramble.api.db.TransactionManager;
import org.briarproject.bramble.api.event.Event;
import org.briarproject.bramble.api.event.EventBus;
import org.briarproject.bramble.api.identity.Author;
import org.briarproject.bramble.api.identity.IdentityManager;
import org.briarproject.bramble.api.identity.LocalAuthor;
import org.briarproject.bramble.api.lifecycle.LifecycleManager;
import org.briarproject.bramble.api.lifecycle.IoExecutor;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.bramble.api.system.AndroidExecutor;
import org.briarproject.bramble.api.system.Clock;
import org.briarproject.briar.android.sharing.SharingController;
import org.briarproject.briar.android.attachment.media.ImageCompressor;
import org.briarproject.briar.android.threaded.ThreadListViewModel;
import org.briarproject.briar.api.android.AndroidNotificationManager;
import org.briarproject.briar.api.client.MessageTracker;
import org.briarproject.briar.api.client.MessageTracker.GroupCount;
import org.briarproject.briar.api.privategroup.GroupMember;
import org.briarproject.briar.api.privategroup.GroupMessage;
import org.briarproject.briar.api.privategroup.GroupMessageFactory;
import org.briarproject.briar.api.privategroup.GroupMessageHeader;
import org.briarproject.briar.api.privategroup.JoinMessageHeader;
import org.briarproject.briar.api.privategroup.PrivateGroup;
import org.briarproject.briar.api.privategroup.PrivateGroupManager;
import org.briarproject.briar.api.privategroup.PinMessage;
import org.briarproject.briar.api.privategroup.event.GroupAdminRemovedEvent;
import org.briarproject.briar.api.privategroup.event.GroupAdminAddedEvent;
import org.briarproject.briar.api.privategroup.event.ContactRelationshipRevealedEvent;
import org.briarproject.briar.api.privategroup.event.GroupDissolvedEvent;
import org.briarproject.briar.api.privategroup.event.GroupInvitationResponseReceivedEvent;
import org.briarproject.briar.api.privategroup.event.GroupPinnedMessageChangedEvent;
import org.briarproject.briar.api.privategroup.event.GroupMessageAddedEvent;
import org.briarproject.briar.api.privategroup.invitation.GroupInvitationResponse;
import org.briarproject.nullsafety.MethodsNotNullByDefault;
import org.briarproject.nullsafety.ParametersNotNullByDefault;

import java.util.ArrayList;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

import javax.annotation.Nullable;
import javax.inject.Inject;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import static java.lang.Math.max;
import static java.util.logging.Logger.getLogger;
import static org.briarproject.bramble.util.LogUtils.logDuration;
import static org.briarproject.bramble.util.LogUtils.now;
import static org.briarproject.bramble.api.sync.SyncConstants.MAX_MESSAGE_BODY_LENGTH;
import static org.briarproject.briar.android.attachment.media.ImageCompressor.MIME_TYPE;
import static org.briarproject.briar.api.privategroup.MessageType.IMAGE;

@MethodsNotNullByDefault
@ParametersNotNullByDefault
class GroupViewModel extends ThreadListViewModel<GroupMessageItem> {

	private static final Logger LOG = getLogger(GroupViewModel.class.getName());

	private final PrivateGroupManager privateGroupManager;
	private final GroupMessageFactory groupMessageFactory;
	private final TransactionManager db;
	private final Executor ioExecutor;
	private final ImageCompressor imageCompressor;

	private final MutableLiveData<PrivateGroup> privateGroup =
			new MutableLiveData<>();
	private final MutableLiveData<Boolean> isCreator = new MutableLiveData<>();
	private final MutableLiveData<Boolean> isAdmin = new MutableLiveData<>();
	private final MutableLiveData<Boolean> isDissolved =
			new MutableLiveData<>();
	private final MutableLiveData<GroupMessageItem> pinnedMessage =
			new MutableLiveData<>();

	@Inject
	GroupViewModel(Application application,
			@DatabaseExecutor Executor dbExecutor,
			LifecycleManager lifecycleManager,
			TransactionManager db,
			AndroidExecutor androidExecutor,
			EventBus eventBus,
			IdentityManager identityManager,
			AndroidNotificationManager notificationManager,
			SharingController sharingController,
			@CryptoExecutor Executor cryptoExecutor,
			@IoExecutor Executor ioExecutor,
			Clock clock,
			MessageTracker messageTracker,
			PrivateGroupManager privateGroupManager,
			GroupMessageFactory groupMessageFactory,
			ImageCompressor imageCompressor) {
		super(application, dbExecutor, lifecycleManager, db, androidExecutor,
				identityManager, notificationManager, sharingController,
				cryptoExecutor, clock, messageTracker, eventBus);
		this.privateGroupManager = privateGroupManager;
		this.groupMessageFactory = groupMessageFactory;
		this.db = db;
		this.ioExecutor = ioExecutor;
		this.imageCompressor = imageCompressor;
	}

	@Override
	public void eventOccurred(Event e) {
		if (e instanceof GroupMessageAddedEvent) {
			GroupMessageAddedEvent g = (GroupMessageAddedEvent) e;
			// only act on non-local messages in this group
			if (!g.isLocal() && g.getGroupId().equals(groupId)) {
				if (g.getHeader() instanceof JoinMessageHeader) {
					// Membership is updated by the protocol, but join notices
					// are intentionally omitted from channel conversations.
					if (((JoinMessageHeader) g.getHeader()).isInitial())
						loadSharingContacts();
					return;
				}
				LOG.info("Group message received, adding...");
				if (g.getHeader().getType() == IMAGE) {
					runOnDbThread(() -> {
						try {
							byte[] image = db.transactionWithResult(true,
									txn -> privateGroupManager.getMessageImage(
											txn, g.getHeader().getId()));
							androidExecutor.runOnUiThread(() -> addItem(
									buildItem(g.getHeader(), g.getText(), image),
									false));
						} catch (DbException ex) {
							handleException(ex);
						}
					});
				} else {
					addItem(buildItem(g.getHeader(), g.getText(), null), false);
				}
			}
		} else if (e instanceof GroupInvitationResponseReceivedEvent) {
			GroupInvitationResponseReceivedEvent g =
					(GroupInvitationResponseReceivedEvent) e;
			GroupInvitationResponse r = g.getMessageHeader();
			if (r.getShareableId().equals(groupId) && r.wasAccepted()) {
				sharingController.add(g.getContactId());
			}
		} else if (e instanceof ContactRelationshipRevealedEvent) {
			ContactRelationshipRevealedEvent c =
					(ContactRelationshipRevealedEvent) e;
			if (c.getGroupId().equals(groupId)) {
				sharingController.add(c.getContactId());
			}
		} else if (e instanceof GroupDissolvedEvent) {
			GroupDissolvedEvent g = (GroupDissolvedEvent) e;
			if (g.getGroupId().equals(groupId)) {
				isDissolved.setValue(true);
			}
		} else if (e instanceof GroupAdminAddedEvent) {
			GroupAdminAddedEvent a = (GroupAdminAddedEvent) e;
			if (a.getGroupId().equals(groupId)) loadPrivateGroup(groupId);
		} else if (e instanceof GroupAdminRemovedEvent) {
			GroupAdminRemovedEvent a = (GroupAdminRemovedEvent) e;
			if (a.getGroupId().equals(groupId)) loadPrivateGroup(groupId);
		} else if (e instanceof GroupPinnedMessageChangedEvent) {
			GroupPinnedMessageChangedEvent p =
					(GroupPinnedMessageChangedEvent) e;
			if (p.getGroupId().equals(groupId)) loadPinnedMessage();
		} else {
			super.eventOccurred(e);
		}
	}

	@Override
	protected void performInitialLoad() {
		super.performInitialLoad();
		loadPrivateGroup(groupId);
		loadPinnedMessage();
	}

	@Override
	protected void clearNotifications() {
		notificationManager.clearGroupMessageNotification(groupId);
	}

	private void loadPrivateGroup(GroupId groupId) {
		runOnDbThread(() -> {
			try {
				PrivateGroup g = privateGroupManager.getPrivateGroup(groupId);
				privateGroup.postValue(g);
				Author author = identityManager.getLocalAuthor();
				isCreator.postValue(g.getCreator().equals(author));
				isAdmin.postValue(
						privateGroupManager.isAdmin(groupId, author));
			} catch (DbException e) {
				handleException(e);
			}
		});
	}

	@Override
	public void loadItems() {
		loadFromDb(txn -> {
			// check first if group is dissolved
			isDissolved
					.postValue(privateGroupManager.isDissolved(txn, groupId));
			// now continue to load the items
			long start = now();
			List<GroupMessageHeader> headers =
					privateGroupManager.getHeaders(txn, groupId);
			logDuration(LOG, "Loading headers", start);
			start = now();
			List<GroupMessageItem> items = new ArrayList<>();
			for (GroupMessageHeader header : headers) {
				if (!(header instanceof JoinMessageHeader))
					items.add(loadItem(txn, header));
			}
			logDuration(LOG, "Loading bodies and creating items", start);
			return items;
		}, this::setItems);
	}

	private GroupMessageItem loadItem(Transaction txn,
			GroupMessageHeader header) throws DbException {
		String text;
		if (header instanceof JoinMessageHeader) {
			// will be looked up later
			text = "";
		} else {
			text = privateGroupManager.getMessageText(txn, header.getId());
		}
		byte[] image = header.getType() == IMAGE
				? privateGroupManager.getMessageImage(txn, header.getId())
				: null;
		return buildItem(header, text, image);
	}

	private GroupMessageItem buildItem(GroupMessageHeader header, String text,
			@Nullable byte[] image) {
		if (header instanceof JoinMessageHeader) {
			return new JoinMessageItem((JoinMessageHeader) header, text);
		}
		return new GroupMessageItem(header, text, image);
	}

	@Override
	public void createAndStoreMessage(String text,
			@Nullable MessageId parentId) {
		runOnDbThread(() -> {
			try {
				LocalAuthor author = identityManager.getLocalAuthor();
				MessageId previousMsgId =
						privateGroupManager.getPreviousMsgId(groupId);
				GroupCount count = privateGroupManager.getGroupCount(groupId);
				long timestamp = count.getLatestMsgTime();
				timestamp = max(clock.currentTimeMillis(), timestamp + 1);
				createMessage(text, timestamp, parentId, author, previousMsgId);
			} catch (DbException e) {
				handleException(e);
			}
		});
	}

	private void createMessage(String text, long timestamp,
			@Nullable MessageId parentId, LocalAuthor author,
			MessageId previousMsgId) {
		cryptoExecutor.execute(() -> {
			LOG.info("Creating group message...");
			GroupMessage msg = groupMessageFactory.createGroupMessage(groupId,
					timestamp, parentId, author, text, previousMsgId);
			storePost(msg, text, null);
		});
	}

	void createAndStoreAdminRequest(String text) {
		runOnDbThread(() -> {
			try {
				LocalAuthor author = identityManager.getLocalAuthor();
				MessageId previousMsgId =
						privateGroupManager.getPreviousMsgId(groupId);
				GroupCount count = privateGroupManager.getGroupCount(groupId);
				long timestamp = max(clock.currentTimeMillis(),
						count.getLatestMsgTime() + 1);
				cryptoExecutor.execute(() -> {
					GroupMessage msg = groupMessageFactory
							.createAdminRequestMessage(groupId, timestamp,
									author, text, previousMsgId);
					storePost(msg, text, null);
				});
			} catch (DbException e) {
				handleException(e);
			}
		});
	}

	void createAndStoreImage(Uri uri, @Nullable String caption,
			@Nullable MessageId parentId) {
		ioExecutor.execute(() -> {
			try {
				String contentType = getApplication().getContentResolver()
						.getType(uri);
				if (contentType == null) throw new IOException();
				InputStream input = getApplication().getContentResolver()
						.openInputStream(uri);
				if (input == null) throw new IOException();
				InputStream compressed =
						imageCompressor.compressImage(input, contentType);
				byte[] image = readImage(compressed);
				runOnDbThread(() -> prepareImageMessage(caption, parentId,
						image));
			} catch (IOException e) {
				handleException(e);
			}
		});
	}

	private byte[] readImage(InputStream input) throws IOException {
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		byte[] buffer = new byte[4096];
		int read;
		while ((read = input.read(buffer)) != -1)
			output.write(buffer, 0, read);
		input.close();
		byte[] image = output.toByteArray();
		int maxBytes = MAX_MESSAGE_BODY_LENGTH - 2048;
		if (image.length <= maxBytes) return image;
		Bitmap bitmap = BitmapFactory.decodeByteArray(image, 0, image.length);
		if (bitmap == null) throw new IOException();
		for (int quality = 80; quality >= 10; quality -= 10) {
			output.reset();
			if (!bitmap.compress(Bitmap.CompressFormat.JPEG, quality, output))
				throw new IOException();
			if (output.size() <= maxBytes) return output.toByteArray();
		}
		throw new IOException();
	}

	private void prepareImageMessage(@Nullable String caption,
			@Nullable MessageId parentId, byte[] image) {
		try {
			LocalAuthor author = identityManager.getLocalAuthor();
			MessageId previousMsgId =
					privateGroupManager.getPreviousMsgId(groupId);
			GroupCount count = privateGroupManager.getGroupCount(groupId);
			long timestamp = max(clock.currentTimeMillis(),
					count.getLatestMsgTime() + 1);
			cryptoExecutor.execute(() -> {
				GroupMessage msg = groupMessageFactory.createImageMessage(
						groupId, timestamp, parentId, author, caption,
						MIME_TYPE, image, previousMsgId);
				storePost(msg, caption == null ? "" : caption, image);
			});
		} catch (DbException e) {
			handleException(e);
		}
	}

	void pinMessage(@Nullable MessageId messageId) {
		runOnDbThread(() -> {
			try {
				LocalAuthor author = identityManager.getLocalAuthor();
				GroupCount count = privateGroupManager.getGroupCount(groupId);
				long timestamp = max(clock.currentTimeMillis(),
						count.getLatestMsgTime() + 1);
				cryptoExecutor.execute(() -> {
					PinMessage pin = groupMessageFactory.createPinMessage(
							groupId, timestamp, author, messageId);
					try {
						privateGroupManager.addLocalPin(pin);
					} catch (DbException e) {
						handleException(e);
					}
				});
			} catch (DbException e) {
				handleException(e);
			}
		});
	}

	private void storePost(GroupMessage msg, String text,
			@Nullable byte[] image) {
		runOnDbThread(false, txn -> {
			long start = now();
			GroupMessageHeader header =
					privateGroupManager.addLocalMessage(txn, msg);
			logDuration(LOG, "Storing group message", start);
			txn.attach(() ->
					addItem(buildItem(header, text, image), true)
			);
		}, this::handleException);
	}

	private void loadPinnedMessage() {
		runOnDbThread(() -> {
			try {
				GroupMessageItem item = db.transactionWithResult(true, txn -> {
					MessageId id =
							privateGroupManager.getPinnedMessageId(txn, groupId);
					if (id == null) return null;
					for (GroupMessageHeader header :
							privateGroupManager.getHeaders(txn, groupId)) {
						if (header.getId().equals(id))
							return loadItem(txn, header);
					}
					return null;
				});
				pinnedMessage.postValue(item);
			} catch (DbException e) {
				handleException(e);
			}
		});
	}

	@Override
	protected void markItemRead(GroupMessageItem item) {
		runOnDbThread(() -> {
			try {
				privateGroupManager.setReadFlag(groupId, item.getId(), true);
			} catch (DbException e) {
				handleException(e);
			}
		});
	}

	@Override
	public void loadSharingContacts() {
		runOnDbThread(true, txn -> {
			Collection<GroupMember> members =
					privateGroupManager.getMembers(txn, groupId);
			Collection<ContactId> contactIds = new ArrayList<>();
			for (GroupMember m : members) {
				if (m.getContactId() != null)
					contactIds.add(m.getContactId());
			}
			txn.attach(() -> sharingController.addAll(contactIds));
		}, this::handleException);
	}

	void deletePrivateGroup() {
		runOnDbThread(() -> {
			try {
				privateGroupManager.removePrivateGroup(groupId);
			} catch (DbException e) {
				handleException(e);
			}
		});
	}

	LiveData<PrivateGroup> getPrivateGroup() {
		return privateGroup;
	}

	LiveData<Boolean> isCreator() {
		return isCreator;
	}

	LiveData<Boolean> isAdmin() {
		return isAdmin;
	}

	LiveData<Boolean> isDissolved() {
		return isDissolved;
	}

	LiveData<GroupMessageItem> getPinnedMessage() {
		return pinnedMessage;
	}

}
