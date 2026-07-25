package org.briarproject.briar.android.contact.add.nearby;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.widget.Toast;

import org.briarproject.bramble.api.contact.Contact;
import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.event.Event;
import org.briarproject.bramble.api.event.EventBus;
import org.briarproject.bramble.api.event.EventListener;
import org.briarproject.bramble.api.identity.Author;
import org.briarproject.bramble.api.qrcode.QrCodeClassifier.QrCodeType;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.versioning.ClientVersion;
import org.briarproject.bramble.api.versioning.event.ClientVersionUpdatedEvent;
import org.briarproject.briar.R;
import org.briarproject.briar.android.activity.ActivityComponent;
import org.briarproject.briar.android.activity.BriarActivity;
import org.briarproject.briar.android.contact.add.nearby.AddContactState.ContactExchangeFinished;
import org.briarproject.briar.android.contact.add.nearby.AddContactState.ContactExchangeResult;
import org.briarproject.briar.android.contact.add.nearby.AddContactState.Failed;
import org.briarproject.briar.android.contact.add.nearby.AddContactState.Failed.WrongQrCodeType;
import org.briarproject.briar.android.contact.add.nearby.AddContactState.Failed.WrongQrCodeVersion;
import org.briarproject.briar.android.contact.add.nearby.AddNearbyContactViewModel.BluetoothDecision;
import org.briarproject.briar.android.fragment.BaseFragment;
import org.briarproject.briar.android.fragment.BaseFragment.BaseFragmentListener;
import org.briarproject.briar.android.controller.handler.UiResultExceptionHandler;
import org.briarproject.briar.android.privategroup.creation.CreateGroupController;
import org.briarproject.briar.android.util.ActivityLaunchers.RequestBluetoothDiscoverable;
import org.briarproject.briar.api.privategroup.event.GroupInvitationRequestReceivedEvent;
import org.briarproject.briar.api.privategroup.invitation.GroupInvitationManager;
import org.briarproject.nullsafety.MethodsNotNullByDefault;
import org.briarproject.nullsafety.ParametersNotNullByDefault;

import java.util.Collections;
import java.util.logging.Logger;

import javax.annotation.Nullable;
import javax.inject.Inject;

import androidx.activity.result.ActivityResultLauncher;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;

import static android.bluetooth.BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE;
import static android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP;
import static android.widget.Toast.LENGTH_LONG;
import static java.util.Objects.requireNonNull;
import static java.util.logging.Logger.getLogger;
import static org.briarproject.bramble.api.qrcode.QrCodeClassifier.QrCodeType.MAILBOX;
import static org.briarproject.briar.android.contact.add.nearby.AddNearbyContactViewModel.BluetoothDecision.ACCEPTED;
import static org.briarproject.briar.android.contact.add.nearby.AddNearbyContactViewModel.BluetoothDecision.REFUSED;

@MethodsNotNullByDefault
@ParametersNotNullByDefault
public class AddNearbyContactActivity extends BriarActivity
		implements BaseFragmentListener, EventListener {

	private static final Logger LOG =
			getLogger(AddNearbyContactActivity.class.getName());

	public static final String EXTRA_JOIN_CHANNEL =
			AddNearbyContactActivity.class.getName() + ".JOIN_CHANNEL";

	@Inject
	ViewModelProvider.Factory viewModelFactory;
	@Inject
	CreateGroupController createGroupController;
	@Inject
	NearbyChannelJoinController channelJoinController;
	@Inject
	EventBus eventBus;

	private AddNearbyContactViewModel viewModel;
	private boolean joiningChannel = false;
	private boolean invitationActionStarted = false;
	@Nullable
	private GroupId inviteToChannel = null;
	@Nullable
	private ContactId channelContact = null;
	@Nullable
	private GroupInvitationRequestReceivedEvent pendingInvitation = null;
	private final ActivityResultLauncher<Integer> bluetoothLauncher =
			registerForActivityResult(new RequestBluetoothDiscoverable(),
					this::onBluetoothDiscoverableResult);

	@Override
	public void injectActivity(ActivityComponent component) {
		component.inject(this);
		viewModel = new ViewModelProvider(this, viewModelFactory)
				.get(AddNearbyContactViewModel.class);
	}

	@Override
	public void onCreate(@Nullable Bundle state) {
		super.onCreate(state);
		Intent intent = getIntent();
		joiningChannel = intent.getBooleanExtra(EXTRA_JOIN_CHANNEL, false);
		byte[] groupId = intent.getByteArrayExtra(GROUP_ID);
		if (groupId != null) inviteToChannel = new GroupId(groupId);
		if (joiningChannel && inviteToChannel != null)
			throw new IllegalStateException("Conflicting channel modes");
		setContentView(R.layout.activity_fragment_container_toolbar);
		Toolbar toolbar = findViewById(R.id.toolbar);
		setSupportActionBar(toolbar);
		requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);
		if (state == null) {
			showInitialFragment(AddNearbyContactIntroFragment.newInstance());
		}
		viewModel.getRequestBluetoothDiscoverable().observeEvent(this, r ->
				requestBluetoothDiscoverable()); // never false
		viewModel.getShowQrCodeFragment().observeEvent(this, show -> {
			if (show) showQrCodeFragment();
		});
		int title = joiningChannel
				? R.string.channels_join_channel
				: inviteToChannel == null
						? R.string.add_contact_title
						: R.string.channels_invite_with_qr;
		requireNonNull(getSupportActionBar()).setTitle(title);
		viewModel.getState()
				.observe(this, this::onAddContactStateChanged);
	}

	@Override
	public void onStart() {
		super.onStart();
		if (isChannelFlow()) eventBus.addListener(this);
	}

	@Override
	protected void onStop() {
		if (isChannelFlow()) eventBus.removeListener(this);
		super.onStop();
	}

	boolean isChannelFlow() {
		return joiningChannel || inviteToChannel != null;
	}

	private void onBluetoothDiscoverableResult(boolean discoverable) {
		if (discoverable) {
			LOG.info("Bluetooth discoverability was accepted");
			viewModel.setBluetoothDecision(ACCEPTED);
		} else {
			LOG.info("Bluetooth discoverability was refused");
			viewModel.setBluetoothDecision(REFUSED);
		}
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		if (item.getItemId() == android.R.id.home) {
			onBackPressed();
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

	@Override
	public void onBackPressed() {
		if (viewModel.getState().getValue() instanceof Failed) {
			// Re-create this activity when going back in failed state.
			// This will also re-create the ViewModel, so we start fresh.
			Intent i = new Intent(getIntent());
			i.setClass(this, AddNearbyContactActivity.class);
			i.setFlags(FLAG_ACTIVITY_CLEAR_TOP);
			startActivity(i);
		} else {
			super.onBackPressed();
		}
	}

	private void requestBluetoothDiscoverable() {
		Intent i = new Intent(ACTION_REQUEST_DISCOVERABLE);
		if (i.resolveActivity(getPackageManager()) != null) {
			LOG.info("Asking for Bluetooth discoverability");
			viewModel.setBluetoothDecision(BluetoothDecision.WAITING);
			bluetoothLauncher.launch(120); // 2min discoverable
		} else {
			viewModel.setBluetoothDecision(BluetoothDecision.NO_ADAPTER);
		}
	}

	private void showQrCodeFragment() {
		// FIXME #824
		FragmentManager fm = getSupportFragmentManager();
		if (fm.findFragmentByTag(AddNearbyContactFragment.TAG) == null) {
			BaseFragment f = AddNearbyContactFragment.newInstance();
			fm.beginTransaction()
					.replace(R.id.fragmentContainer, f, f.getUniqueTag())
					.addToBackStack(f.getUniqueTag())
					.commit();
		}
	}

	private void onAddContactStateChanged(@Nullable AddContactState state) {
		if (state instanceof ContactExchangeFinished) {
			ContactExchangeResult result =
					((ContactExchangeFinished) state).result;
			onContactExchangeResult(result);
		} else if (state instanceof WrongQrCodeType) {
			QrCodeType qrCodeType = ((WrongQrCodeType) state).qrCodeType;
			if (qrCodeType == MAILBOX) onMailboxQrCodeScanned();
			else onWrongQrCodeType();
		} else if (state instanceof WrongQrCodeVersion) {
			boolean qrCodeTooOld = ((WrongQrCodeVersion) state).qrCodeTooOld;
			onWrongQrCodeVersion(qrCodeTooOld);
		} else if (state instanceof Failed) {
			showErrorFragment();
		}
	}

	private void onContactExchangeResult(ContactExchangeResult result) {
		Author remoteAuthor;
		@Nullable ContactId contactId = null;
		if (result instanceof ContactExchangeResult.Success) {
			Contact contact =
					((ContactExchangeResult.Success) result).contact;
			remoteAuthor = contact.getAuthor();
			contactId = contact.getId();
			String contactName = remoteAuthor.getName();
			String text = getString(R.string.contact_added_toast, contactName);
			Toast.makeText(this, text, LENGTH_LONG).show();
		} else if (result instanceof ContactExchangeResult.Error) {
			Author duplicateAuthor =
					((ContactExchangeResult.Error) result).duplicateAuthor;
			if (duplicateAuthor == null) {
				showErrorFragment();
				return;
			} else {
				remoteAuthor = duplicateAuthor;
				String contactName = duplicateAuthor.getName();
				String text =
						getString(R.string.contact_already_exists, contactName);
				Toast.makeText(this, text, LENGTH_LONG).show();
			}
		} else throw new AssertionError();
		if (isChannelFlow()) {
			viewModel.waitForChannelInvitation();
			if (contactId == null) resolveChannelContact(remoteAuthor);
			else onChannelContactResolved(contactId);
		} else {
			supportFinishAfterTransition();
		}
	}

	private void resolveChannelContact(Author remoteAuthor) {
		channelJoinController.getContactId(remoteAuthor,
				new UiResultExceptionHandler<ContactId, DbException>(this) {
					@Override
					public void onResultUi(ContactId contactId) {
						onChannelContactResolved(contactId);
					}

					@Override
					public void onExceptionUi(DbException exception) {
						handleException(exception);
					}
				});
	}

	private void onChannelContactResolved(ContactId contactId) {
		channelContact = contactId;
		if (joiningChannel) {
			tryAcceptPendingInvitation();
		} else {
			prepareChannelInvitation(contactId);
		}
	}

	private void prepareChannelInvitation(ContactId contactId) {
		channelJoinController.supportsChannelJoin(contactId,
				new UiResultExceptionHandler<Boolean, DbException>(this) {
					@Override
					public void onResultUi(Boolean supported) {
						if (supported) sendChannelInvitation(contactId);
					}

					@Override
					public void onExceptionUi(DbException exception) {
						handleException(exception);
					}
				});
	}

	private void sendChannelInvitation(ContactId contactId) {
		if (invitationActionStarted) return;
		GroupId groupId = inviteToChannel;
		if (groupId == null) throw new IllegalStateException();
		invitationActionStarted = true;
		createGroupController.sendInvitation(groupId,
				Collections.singleton(contactId), null,
				new UiResultExceptionHandler<Void, DbException>(this) {
					@Override
					public void onResultUi(Void result) {
						Toast.makeText(AddNearbyContactActivity.this,
								R.string.groups_invitation_sent,
								Toast.LENGTH_LONG).show();
						setResult(RESULT_OK);
						supportFinishAfterTransition();
					}

					@Override
					public void onExceptionUi(DbException exception) {
						invitationActionStarted = false;
						handleException(exception);
					}
				});
	}

	private void tryAcceptPendingInvitation() {
		if (invitationActionStarted || channelContact == null ||
				pendingInvitation == null) return;
		if (!channelContact.equals(pendingInvitation.getContactId())) return;
		GroupInvitationRequestReceivedEvent event = pendingInvitation;
		invitationActionStarted = true;
		channelJoinController.acceptInvitation(channelContact,
				event.getMessageHeader().getNameable(),
				new UiResultExceptionHandler<Void, DbException>(this) {
					@Override
					public void onResultUi(Void result) {
						Toast.makeText(AddNearbyContactActivity.this,
								R.string.groups_invitations_joined,
								Toast.LENGTH_LONG).show();
						setResult(RESULT_OK);
						supportFinishAfterTransition();
					}

					@Override
					public void onExceptionUi(DbException exception) {
						invitationActionStarted = false;
						handleException(exception);
					}
				});
	}

	@Override
	public void eventOccurred(Event event) {
		runOnUiThreadUnlessDestroyed(() -> {
			if (joiningChannel &&
					event instanceof GroupInvitationRequestReceivedEvent) {
				pendingInvitation =
						(GroupInvitationRequestReceivedEvent) event;
				tryAcceptPendingInvitation();
			} else if (inviteToChannel != null &&
					event instanceof ClientVersionUpdatedEvent) {
				ClientVersionUpdatedEvent update =
						(ClientVersionUpdatedEvent) event;
				ClientVersion version = update.getClientVersion();
				if (update.getContactId().equals(channelContact) &&
						version.getClientId().equals(
								GroupInvitationManager.CLIENT_ID) &&
						version.getMajorVersion() ==
								GroupInvitationManager.MAJOR_VERSION &&
						version.getMinorVersion() >=
								GroupInvitationManager.MINOR_VERSION) {
					sendChannelInvitation(update.getContactId());
				}
			}
		});
	}

	private void onMailboxQrCodeScanned() {
		String title = getString(R.string.qr_code_invalid);
		String msg = getString(R.string.mailbox_qr_code_for_contact);
		showNextFragment(
				AddNearbyContactErrorFragment.newInstance(title, msg, false));
	}

	private void onWrongQrCodeType() {
		String title = getString(R.string.qr_code_invalid);
		String msg = getString(R.string.qr_code_format_unknown);
		showNextFragment(
				AddNearbyContactErrorFragment.newInstance(title, msg, false));
	}

	private void onWrongQrCodeVersion(boolean qrCodeTooOld) {
		String title = getString(R.string.qr_code_invalid);
		String msg;
		if (qrCodeTooOld) msg = getString(R.string.qr_code_too_old_1);
		else msg = getString(R.string.qr_code_too_new_1);
		showNextFragment(
				AddNearbyContactErrorFragment.newInstance(title, msg, false));
	}

	private void showErrorFragment() {
		showNextFragment(new AddNearbyContactErrorFragment());
	}

}
