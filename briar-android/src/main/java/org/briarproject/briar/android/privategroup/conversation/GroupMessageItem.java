package org.briarproject.briar.android.privategroup.conversation;

import org.briarproject.bramble.api.identity.Author;
import org.briarproject.briar.api.identity.AuthorInfo;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.briar.R;
import org.briarproject.briar.android.threaded.ThreadItem;
import org.briarproject.briar.api.privategroup.GroupMessageHeader;

import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;

import androidx.annotation.LayoutRes;
import androidx.annotation.UiThread;

@UiThread
@NotThreadSafe
class GroupMessageItem extends ThreadItem {

	private final GroupId groupId;
	@Nullable
	private final byte[] image;
	private final boolean adminRequest;

	private GroupMessageItem(MessageId messageId, GroupId groupId,
			@Nullable MessageId parentId, String text, long timestamp,
			Author author, AuthorInfo authorInfo, boolean isRead,
			@Nullable byte[] image, boolean adminRequest) {
		super(messageId, parentId, text, timestamp, author, authorInfo, isRead);
		this.groupId = groupId;
		this.image = image;
		this.adminRequest = adminRequest;
	}

	GroupMessageItem(GroupMessageHeader h, String text) {
		this(h, text, null);
	}

	GroupMessageItem(GroupMessageHeader h, String text,
			@Nullable byte[] image) {
		this(h.getId(), h.getGroupId(), h.getParentId(), text, h.getTimestamp(),
				h.getAuthor(), h.getAuthorInfo(), h.isRead(), image,
				h.getType() ==
						org.briarproject.briar.api.privategroup.MessageType.ADMIN_REQUEST);
	}

	public GroupId getGroupId() {
		return groupId;
	}

	@Nullable
	byte[] getImage() {
		return image;
	}

	boolean hasImage() {
		return image != null;
	}

	boolean isAdminRequest() {
		return adminRequest;
	}

	@LayoutRes
	public int getLayout() {
		return R.layout.list_item_thread;
	}

}
