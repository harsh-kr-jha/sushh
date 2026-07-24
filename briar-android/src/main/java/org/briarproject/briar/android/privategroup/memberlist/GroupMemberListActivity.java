package org.briarproject.briar.android.privategroup.memberlist;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.event.Event;
import org.briarproject.bramble.api.event.EventBus;
import org.briarproject.bramble.api.event.EventListener;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.event.GroupRemovedEvent;
import org.briarproject.briar.R;
import org.briarproject.briar.android.activity.ActivityComponent;
import org.briarproject.briar.android.activity.BriarActivity;
import org.briarproject.briar.android.controller.handler.UiResultExceptionHandler;
import org.briarproject.briar.android.conversation.ConversationActivity;
import org.briarproject.briar.android.view.BriarRecyclerView;
import org.briarproject.briar.api.privategroup.JoinMessageHeader;
import org.briarproject.briar.api.privategroup.event.GroupAdminAddedEvent;
import org.briarproject.briar.api.privategroup.event.GroupMessageAddedEvent;
import org.briarproject.nullsafety.MethodsNotNullByDefault;
import org.briarproject.nullsafety.ParametersNotNullByDefault;

import java.util.Collection;

import javax.annotation.Nullable;
import javax.inject.Inject;

import androidx.recyclerview.widget.LinearLayoutManager;

import static org.briarproject.briar.android.conversation.ConversationActivity.CONTACT_ID;
import static org.briarproject.briar.api.identity.AuthorInfo.Status.OURSELVES;

@MethodsNotNullByDefault
@ParametersNotNullByDefault
public class GroupMemberListActivity extends BriarActivity
		implements EventListener {

	@Inject
	GroupMemberListController controller;
	@Inject
	EventBus eventBus;

	private MemberListAdapter adapter;
	private BriarRecyclerView list;
	private GroupId groupId;
	private boolean localCreator;

	@Override
	public void injectActivity(ActivityComponent component) {
		component.inject(this);
	}

	@Override
	public void onCreate(@Nullable Bundle state) {
		super.onCreate(state);

		setContentView(R.layout.activity_sharing_status);

		Intent i = getIntent();
		byte[] b = i.getByteArrayExtra(GROUP_ID);
		if (b == null) throw new IllegalStateException("No GroupId in intent.");
		groupId = new GroupId(b);

		list = findViewById(R.id.list);
		LinearLayoutManager linearLayoutManager = new LinearLayoutManager(this);
		list.setLayoutManager(linearLayoutManager);
		adapter = new MemberListAdapter(this, this::onMemberClick);
		list.setAdapter(adapter);

		TextView info = findViewById(R.id.info);
		info.setText(R.string.sharing_status_groups);
	}

	@Override
	public void onStart() {
		super.onStart();
		loadMembers();
		eventBus.addListener(this);
		list.startPeriodicUpdate();
	}

	@Override
	public void onStop() {
		super.onStop();
		eventBus.removeListener(this);
		list.stopPeriodicUpdate();
	}

	@Override
	public void eventOccurred(Event e) {
		if (e instanceof GroupAdminAddedEvent) {
			GroupAdminAddedEvent a = (GroupAdminAddedEvent) e;
			if (a.getGroupId().equals(groupId)) loadMembers();
		} else if (e instanceof GroupMessageAddedEvent) {
			// we can't use GroupInvitationResponseReceivedEvent, because
			// a peer only becomes a member after joining the group by message
			GroupMessageAddedEvent ge = (GroupMessageAddedEvent) e;
			if (ge.getGroupId().equals(groupId) &&
					ge.getHeader() instanceof JoinMessageHeader) {
				loadMembers();
			}
		} else if (e instanceof GroupRemovedEvent) {
			GroupRemovedEvent g = (GroupRemovedEvent) e;
			if (g.getGroup().getId().equals(groupId)) {
				supportFinishAfterTransition();
			}
		}
		// TODO ContactConnectedEvent and ContactDisconnectedEvent
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		if (item.getItemId() == android.R.id.home) {
			onBackPressed();
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

	private void loadMembers() {
		controller.loadMembers(groupId,
				new UiResultExceptionHandler<Collection<MemberListItem>, DbException>(
						this) {
					@Override
					public void onResultUi(Collection<MemberListItem> members) {
						localCreator = false;
						for (MemberListItem member : members) {
							if (member.isCreator() &&
									member.getStatus() == OURSELVES) {
								localCreator = true;
							}
						}
						adapter.addAll(members);
					}

					@Override
					public void onExceptionUi(DbException exception) {
						handleException(exception);
					}
				});
	}

	private void onMemberClick(MemberListItem item) {
		if (item.isAdmin() && item.getContactId() != null) {
			Intent intent = new Intent(this, ConversationActivity.class);
			intent.putExtra(CONTACT_ID, item.getContactId().getInt());
			startActivity(intent);
		} else if (localCreator && !item.isAdmin()) {
			String name = item.getMember().getName();
			new MaterialAlertDialogBuilder(this, R.style.BriarDialogTheme)
					.setTitle(R.string.channels_make_admin)
					.setMessage(getString(R.string.channels_make_admin_message,
							name))
					.setPositiveButton(R.string.channels_make_admin,
							(dialog, which) -> appointAdmin(item))
					.setNegativeButton(R.string.cancel, null)
					.show();
		} else if (item.isAdmin()) {
			Toast.makeText(this, R.string.channels_add_admin_contact,
					Toast.LENGTH_LONG).show();
		}
	}

	private void appointAdmin(MemberListItem item) {
		controller.appointAdmin(groupId, item.getMember(),
				new UiResultExceptionHandler<Void, DbException>(this) {
					@Override
					public void onResultUi(Void result) {
						Toast.makeText(GroupMemberListActivity.this,
								R.string.channels_admin_added,
								Toast.LENGTH_SHORT).show();
						loadMembers();
					}

					@Override
					public void onExceptionUi(DbException exception) {
						handleException(exception);
					}
				});
	}

}
