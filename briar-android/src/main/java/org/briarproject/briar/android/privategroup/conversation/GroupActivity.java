package org.briarproject.briar.android.privategroup.conversation;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.briarproject.briar.R;
import org.briarproject.briar.android.activity.ActivityComponent;
import org.briarproject.briar.android.contact.add.nearby.AddNearbyContactActivity;
import org.briarproject.briar.android.contact.add.remote.AddContactActivity;
import org.briarproject.briar.android.privategroup.memberlist.GroupMemberListActivity;
import org.briarproject.briar.android.privategroup.reveal.RevealContactsActivity;
import org.briarproject.briar.android.threaded.ThreadListActivity;
import org.briarproject.briar.android.threaded.ThreadListViewModel;
import org.briarproject.briar.android.widget.LinkDialogFragment;
import org.briarproject.nullsafety.MethodsNotNullByDefault;
import org.briarproject.nullsafety.ParametersNotNullByDefault;

import javax.annotation.Nullable;
import javax.inject.Inject;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts.GetContent;
import androidx.appcompat.widget.Toolbar;
import androidx.lifecycle.ViewModelProvider;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;
import static android.content.DialogInterface.BUTTON_POSITIVE;
import static org.briarproject.briar.android.util.UiUtils.observeOnce;
import static org.briarproject.briar.api.privategroup.PrivateGroupConstants.MAX_GROUP_POST_TEXT_LENGTH;
import static org.briarproject.nullsafety.NullSafety.requireNonNull;

@MethodsNotNullByDefault
@ParametersNotNullByDefault
public class GroupActivity extends
		ThreadListActivity<GroupMessageItem, GroupMessageAdapter>
		implements GroupMessageItemViewHolder.PinListener {

	@Inject
	ViewModelProvider.Factory viewModelFactory;

	private GroupViewModel viewModel;
	private boolean admin = false;
	private boolean dissolved = true;
	private View messageAdminsButton;
	private View pinnedMessageContainer;
	private TextView pinnedMessage;

	private final ActivityResultLauncher<String> imageLauncher =
			registerForActivityResult(new GetContent(), this::onImageChosen);

	@Override
	public void injectActivity(ActivityComponent component) {
		component.inject(this);
		viewModel = new ViewModelProvider(this, viewModelFactory)
				.get(GroupViewModel.class);
	}

	@Override
	protected ThreadListViewModel<GroupMessageItem> getViewModel() {
		return viewModel;
	}

	@Override
	protected GroupMessageAdapter createAdapter() {
		return new GroupMessageAdapter(this);
	}

	@Override
	public void onCreate(@Nullable Bundle state) {
		super.onCreate(state);

		Toolbar toolbar = setUpCustomToolbar(false);
		// Open member list on Toolbar click
		toolbar.setOnClickListener(v -> {
			Intent i = new Intent(GroupActivity.this,
					GroupMemberListActivity.class);
			i.putExtra(GROUP_ID, groupId.getBytes());
			startActivity(i);
		});

		String groupName = getIntent().getStringExtra(GROUP_NAME);
		if (groupName != null) setTitle(groupName);
		observeOnce(viewModel.getPrivateGroup(), this, privateGroup ->
				setTitle(privateGroup.getName())
		);
		observeOnce(viewModel.isCreator(), this, adapter::setIsCreator);
		viewModel.isAdmin().observe(this, isAdmin -> {
			admin = isAdmin;
			adapter.setIsAdmin(isAdmin);
			updatePostingState();
			invalidateOptionsMenu();
		});
		messageAdminsButton = findViewById(R.id.messageAdminsButton);
		messageAdminsButton.setOnClickListener(v -> showAdminRequestDialog());
		pinnedMessageContainer =
				findViewById(R.id.pinnedMessageContainer);
		pinnedMessage = findViewById(R.id.pinnedMessage);
		pinnedMessageContainer.setOnClickListener(v -> {
			GroupMessageItem item = viewModel.getPinnedMessage().getValue();
			if (item != null) scrollToItemAtTop(item.getId());
		});
		pinnedMessageContainer.setOnLongClickListener(v -> {
			if (admin) confirmUnpinMessage();
			return admin;
		});
		viewModel.getPinnedMessage().observe(this,
				this::displayPinnedMessage);

		// start with group disabled and enable when not dissolved
		setGroupEnabled(false);
		viewModel.isDissolved().observe(this, dissolved -> {
			this.dissolved = dissolved;
			updatePostingState();
			// only show dialog when no prior state
			if (dissolved && state == null) onGroupDissolved();
			invalidateOptionsMenu();
		});
	}

	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		MenuItem image = menu.findItem(R.id.action_channel_share_image);
		if (image != null) image.setVisible(admin && !dissolved);
		return super.onPrepareOptionsMenu(menu);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu items for use in the action bar
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.group_actions, menu);

		// show items based on role (which will not change, so observe once)
		observeOnce(viewModel.isCreator(), this, isCreator -> {
			menu.findItem(R.id.action_group_reveal).setVisible(!isCreator);
			menu.findItem(R.id.action_group_leave).setVisible(!isCreator);
			menu.findItem(R.id.action_group_dissolve).setVisible(isCreator);
		});
		super.onCreateOptionsMenu(menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		int itemId = item.getItemId();
		if (itemId == R.id.action_group_member_list) {
			Intent i = new Intent(this, GroupMemberListActivity.class);
			i.putExtra(GROUP_ID, groupId.getBytes());
			startActivity(i);
			return true;
		} else if (itemId == R.id.action_channel_share_image) {
			imageLauncher.launch("image/*");
			return true;
		} else if (itemId == R.id.action_group_reveal) {
			if (requireNonNull(viewModel.isCreator().getValue()))
				throw new IllegalStateException();
			Intent i = new Intent(this, RevealContactsActivity.class);
			i.putExtra(GROUP_ID, groupId.getBytes());
			startActivity(i);
			return true;
		} else if (itemId == R.id.action_channel_add_nearby) {
			Intent i = new Intent(this, AddNearbyContactActivity.class);
			i.putExtra(GROUP_ID, groupId.getBytes());
			startActivity(i);
			return true;
		} else if (itemId == R.id.action_channel_add_remotely) {
			startActivity(new Intent(this, AddContactActivity.class));
			return true;
		} else if (itemId == R.id.action_group_leave) {
			if (requireNonNull(viewModel.isCreator().getValue()))
				throw new IllegalStateException();
			showLeaveGroupDialog();
			return true;
		} else if (itemId == R.id.action_group_dissolve) {
			if (!requireNonNull(viewModel.isCreator().getValue()))
				throw new IllegalStateException();
			showDissolveGroupDialog();
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

	private void onImageChosen(@Nullable Uri uri) {
		if (uri == null) return;
		EditText caption = new EditText(this);
		caption.setHint(R.string.image_caption_hint);
		int padding = getResources().getDimensionPixelSize(
				R.dimen.margin_medium);
		caption.setPadding(padding, padding, padding, padding);
		new MaterialAlertDialogBuilder(this, R.style.BriarDialogTheme)
				.setTitle(R.string.channels_share_image)
				.setView(caption)
				.setPositiveButton(R.string.send, (dialog, which) -> {
					String text = caption.getText().toString().trim();
					viewModel.createAndStoreImage(uri,
							text.isEmpty() ? null : text,
							null);
				})
				.setNegativeButton(R.string.cancel, null)
				.show();
	}

	@Override
	protected int getMaxTextLength() {
		return MAX_GROUP_POST_TEXT_LENGTH;
	}

	@Override
	public void onReplyClick(GroupMessageItem item) {
		Boolean isDissolved = viewModel.isDissolved().getValue();
		if (admin && isDissolved != null && !isDissolved)
			super.onReplyClick(item);
	}

	@Override
	public void onLinkClick(String url){
		LinkDialogFragment f = LinkDialogFragment.newInstance(url);
		f.show(getSupportFragmentManager(), f.getUniqueTag());
	}

	private void setGroupEnabled(boolean enabled) {
		sendController.setReady(enabled);
		list.getRecyclerView().setAlpha(dissolved ? 0.5f : 1f);
		if (!enabled) {
			textInput.setVisibility(GONE);
			if (textInput.isKeyboardOpen()) textInput.hideSoftKeyboard();
		} else {
			textInput.setVisibility(VISIBLE);
		}
		if (messageAdminsButton != null) {
			messageAdminsButton.setVisibility(!admin && !dissolved
					? VISIBLE : GONE);
		}
	}

	private void updatePostingState() {
		setGroupEnabled(admin && !dissolved);
	}

	private void showAdminRequestDialog() {
		EditText input = new EditText(this);
		input.setHint(R.string.channels_admin_request_hint);
		int padding = getResources().getDimensionPixelSize(
				R.dimen.margin_medium);
		input.setPadding(padding, padding, padding, padding);
		MaterialAlertDialogBuilder dialog = new MaterialAlertDialogBuilder(
				this, R.style.BriarDialogTheme)
				.setTitle(R.string.channels_message_admins)
				.setView(input)
				.setPositiveButton(R.string.send, null)
				.setNegativeButton(R.string.cancel, null);
		androidx.appcompat.app.AlertDialog alert = dialog.create();
		alert.setOnShowListener(ignored ->
				alert.getButton(BUTTON_POSITIVE)
						.setOnClickListener(v -> {
							String text = input.getText().toString().trim();
							if (!text.isEmpty()) {
								viewModel.createAndStoreAdminRequest(text);
								alert.dismiss();
							}
						}));
		alert.show();
	}

	@Override
	public void onPinClick(GroupMessageItem item) {
		new MaterialAlertDialogBuilder(this, R.style.BriarDialogTheme)
				.setTitle(R.string.channels_pin_message)
				.setMessage(R.string.channels_pin_message_confirm)
				.setPositiveButton(R.string.channels_pin_message,
						(dialog, which) -> viewModel.pinMessage(item.getId()))
				.setNegativeButton(R.string.cancel, null)
				.show();
	}

	private void confirmUnpinMessage() {
		new MaterialAlertDialogBuilder(this, R.style.BriarDialogTheme)
				.setTitle(R.string.channels_unpin_message)
				.setMessage(R.string.channels_unpin_message_confirm)
				.setPositiveButton(R.string.channels_unpin_message,
						(dialog, which) -> viewModel.pinMessage(null))
				.setNegativeButton(R.string.cancel, null)
				.show();
	}

	private void displayPinnedMessage(@Nullable GroupMessageItem item) {
		if (item == null) {
			pinnedMessageContainer.setVisibility(GONE);
			return;
		}
		String text = item.hasImage()
				? getString(R.string.channels_pinned_image)
				: item.getText();
		pinnedMessage.setText(getString(R.string.channels_pinned_format,
				item.getAuthorName(), text));
		pinnedMessageContainer.setVisibility(VISIBLE);
	}

	private void showLeaveGroupDialog() {
		MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(
				this, R.style.BriarDialogTheme);
		builder.setTitle(getString(R.string.groups_leave_dialog_title));
		builder.setMessage(getString(R.string.groups_leave_dialog_message));
		builder.setNegativeButton(R.string.dialog_button_leave,
				(d, w) -> deleteGroup());
		builder.setPositiveButton(R.string.cancel, null);
		builder.show();
	}

	private void showDissolveGroupDialog() {
		MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(
				this, R.style.BriarDialogTheme);
		builder.setTitle(getString(R.string.groups_dissolve_dialog_title));
		builder.setMessage(getString(R.string.groups_dissolve_dialog_message));
		builder.setNegativeButton(R.string.groups_dissolve_button,
				(d, w) -> deleteGroup());
		builder.setPositiveButton(R.string.cancel, null);
		builder.show();
	}

	private void deleteGroup() {
		// The activity is going to be destroyed by the
		// GroupRemovedEvent being fired
		viewModel.deletePrivateGroup();
	}

	private void onGroupDissolved() {
		MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(
				this, R.style.BriarDialogTheme);
		builder.setTitle(getString(R.string.groups_dissolved_dialog_title));
		builder.setMessage(getString(R.string.groups_dissolved_dialog_message));
		builder.setNeutralButton(R.string.ok, null);
		builder.show();
	}

}
