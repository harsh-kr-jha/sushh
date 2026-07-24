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
import org.briarproject.briar.api.privategroup.event.GroupAdminRemovedEvent;
import org.briarproject.briar.api.privategroup.event.GroupMemberRemovedEvent;
import org.briarproject.briar.api.privategroup.event.GroupMessageAddedEvent;
import org.briarproject.nullsafety.MethodsNotNullByDefault;
import org.briarproject.nullsafety.ParametersNotNullByDefault;

import java.util.Collection;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;
import javax.inject.Inject;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.appcompat.widget.SearchView;

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
	private boolean localAdmin;

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
		SearchView search = findViewById(R.id.memberSearch);
		search.setVisibility(android.view.View.VISIBLE);
		search.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
			@Override
			public boolean onQueryTextSubmit(String query) {
				adapter.setQuery(query);
				return true;
			}

			@Override
			public boolean onQueryTextChange(String newText) {
				adapter.setQuery(newText);
				return true;
			}
		});
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
		} else if (e instanceof GroupAdminRemovedEvent) {
			GroupAdminRemovedEvent a = (GroupAdminRemovedEvent) e;
			if (a.getGroupId().equals(groupId)) loadMembers();
		} else if (e instanceof GroupMemberRemovedEvent) {
			GroupMemberRemovedEvent m = (GroupMemberRemovedEvent) e;
			if (m.getGroupId().equals(groupId)) loadMembers();
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
						localAdmin = false;
						for (MemberListItem member : members) {
							if (member.getStatus() == OURSELVES) {
								localCreator = member.isCreator();
								localAdmin = member.isAdmin();
							}
						}
						adapter.setMembers(members);
					}

					@Override
					public void onExceptionUi(DbException exception) {
						handleException(exception);
					}
				});
	}

	private void onMemberClick(MemberListItem item) {
		boolean ourselves = item.getStatus() == OURSELVES;
		boolean canDemote = localCreator && item.isAdmin() &&
				!item.isCreator();
		boolean canPromote = localCreator && !item.isAdmin();
		boolean canKick = !ourselves && !item.isCreator() &&
				(localCreator || (localAdmin && !item.isAdmin()));
		boolean canMessage = item.getContactId() != null;
		if (canDemote || canPromote || canKick) {
			List<String> labels = new ArrayList<>();
			List<Runnable> actions = new ArrayList<>();
			if (canPromote) {
				labels.add(getString(R.string.channels_make_admin));
				actions.add(() -> confirmAppointAdmin(item));
			}
			if (canDemote) {
				labels.add(getString(R.string.channels_remove_admin));
				actions.add(() -> confirmRemoveAdmin(item));
			}
			if (canKick) {
				labels.add(getString(R.string.channels_kick_member));
				actions.add(() -> confirmRemoveMember(item));
			}
			if (canMessage) {
				labels.add(getString(R.string.channels_message_member));
				actions.add(() -> openConversation(item));
			}
			new MaterialAlertDialogBuilder(this, R.style.BriarDialogTheme)
					.setTitle(item.getMember().getName())
					.setItems(labels.toArray(new String[0]),
							(dialog, which) -> actions.get(which).run())
					.setNegativeButton(R.string.cancel, null)
					.show();
		} else if (item.isAdmin() && canMessage) {
			openConversation(item);
		} else if (item.isAdmin()) {
			Toast.makeText(this, R.string.channels_add_admin_contact,
					Toast.LENGTH_LONG).show();
		}
	}

	private void openConversation(MemberListItem item) {
		if (item.getContactId() != null) {
			Intent intent = new Intent(this, ConversationActivity.class);
			intent.putExtra(CONTACT_ID, item.getContactId().getInt());
			startActivity(intent);
		}
	}

	private void confirmAppointAdmin(MemberListItem item) {
		String name = item.getMember().getName();
		new MaterialAlertDialogBuilder(this, R.style.BriarDialogTheme)
				.setTitle(R.string.channels_make_admin)
				.setMessage(getString(R.string.channels_make_admin_message,
						name))
				.setPositiveButton(R.string.channels_make_admin,
						(dialog, which) -> appointAdmin(item))
				.setNegativeButton(R.string.cancel, null)
				.show();
	}

	private void confirmRemoveAdmin(MemberListItem item) {
		new MaterialAlertDialogBuilder(this, R.style.BriarDialogTheme)
				.setTitle(R.string.channels_remove_admin)
				.setMessage(getString(R.string.channels_remove_admin_message,
						item.getMember().getName()))
				.setPositiveButton(R.string.channels_remove_admin,
						(dialog, which) -> removeAdmin(item))
				.setNegativeButton(R.string.cancel, null)
				.show();
	}

	private void confirmRemoveMember(MemberListItem item) {
		new MaterialAlertDialogBuilder(this, R.style.BriarDialogTheme)
				.setTitle(R.string.channels_kick_member)
				.setMessage(getString(R.string.channels_kick_member_message,
						item.getMember().getName()))
				.setPositiveButton(R.string.channels_kick_member,
						(dialog, which) -> removeMember(item))
				.setNegativeButton(R.string.cancel, null)
				.show();
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

	private void removeAdmin(MemberListItem item) {
		controller.removeAdmin(groupId, item.getMember(),
				new UiResultExceptionHandler<Void, DbException>(this) {
					@Override
					public void onResultUi(Void result) {
						Toast.makeText(GroupMemberListActivity.this,
								R.string.channels_admin_removed,
								Toast.LENGTH_SHORT).show();
						loadMembers();
					}

					@Override
					public void onExceptionUi(DbException exception) {
						handleException(exception);
					}
				});
	}

	private void removeMember(MemberListItem item) {
		controller.removeMember(groupId, item.getMember(),
				new UiResultExceptionHandler<Void, DbException>(this) {
					@Override
					public void onResultUi(Void result) {
						Toast.makeText(GroupMemberListActivity.this,
								R.string.channels_member_kicked,
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
