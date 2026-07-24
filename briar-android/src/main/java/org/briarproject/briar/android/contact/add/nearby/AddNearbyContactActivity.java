package org.briarproject.briar.android.contact.add.nearby;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.widget.Toast;

import org.briarproject.bramble.api.contact.Contact;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.identity.Author;
import org.briarproject.bramble.api.qrcode.QrCodeClassifier.QrCodeType;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.briar.BuildConfig;
import org.briarproject.briar.R;
import org.briarproject.briar.android.activity.ActivityComponent;
import org.briarproject.briar.android.activity.BriarActivity;
import org.briarproject.briar.android.contact.add.nearby.AddContactState.ContactExchangeFinished;
import org.briarproject.briar.android.contact.add.nearby.AddContactState.ContactExchangeResult;
import org.briarproject.briar.android.contact.add.nearby.AddContactState.Failed;
import org.briarproject.briar.android.contact.add.nearby.AddContactState.Failed.WrongQrCodeType;
import org.briarproject.briar.android.contact.add.nearby.AddContactState.Failed.WrongQrCodeVersion;
import org.briarproject.briar.android.contact.add.nearby.AddNearbyContactViewModel.BluetoothDecision;
import org.briarproject.briar.android.controller.handler.UiResultExceptionHandler;
import org.briarproject.briar.android.fragment.BaseFragment;
import org.briarproject.briar.android.fragment.BaseFragment.BaseFragmentListener;
import org.briarproject.briar.android.privategroup.creation.CreateGroupController;
import org.briarproject.briar.android.util.ActivityLaunchers.RequestBluetoothDiscoverable;
import org.briarproject.nullsafety.MethodsNotNullByDefault;
import org.briarproject.nullsafety.ParametersNotNullByDefault;

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
import static java.util.Collections.singleton;
import static java.util.Objects.requireNonNull;
import static java.util.logging.Logger.getLogger;
import static org.briarproject.bramble.api.qrcode.QrCodeClassifier.QrCodeType.MAILBOX;
import static org.briarproject.briar.android.contact.add.nearby.AddNearbyContactViewModel.BluetoothDecision.ACCEPTED;
import static org.briarproject.briar.android.contact.add.nearby.AddNearbyContactViewModel.BluetoothDecision.REFUSED;

@MethodsNotNullByDefault
@ParametersNotNullByDefault
public class AddNearbyContactActivity extends BriarActivity
		implements BaseFragmentListener {

	public static final String EXTRA_JOIN_GROUP_QR =
			"sushh.EXTRA_JOIN_GROUP_QR";

	private static final Logger LOG =
			getLogger(AddNearbyContactActivity.class.getName());

	@Inject
	ViewModelProvider.Factory viewModelFactory;
	@Inject
	CreateGroupController createGroupController;

	private AddNearbyContactViewModel viewModel;
	@Nullable
	private GroupId invitationGroupId;
	private boolean joinGroupQr;
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
		byte[] groupId = getIntent().getByteArrayExtra(GROUP_ID);
		if (groupId != null) {
			if (!BuildConfig.SUSHH_HUB) {
				throw new IllegalStateException(
						"Only the Hub edition can issue group invitations");
			}
			invitationGroupId = new GroupId(groupId);
		}
		joinGroupQr = getIntent().getBooleanExtra(EXTRA_JOIN_GROUP_QR, false);
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
		if (invitationGroupId != null) {
			requireNonNull(getSupportActionBar())
					.setTitle(R.string.groups_invite_by_qr);
		} else if (joinGroupQr) {
			requireNonNull(getSupportActionBar())
					.setTitle(R.string.groups_join_by_qr);
		} else {
			requireNonNull(getSupportActionBar())
					.setTitle(R.string.add_contact_title);
		}
		viewModel.getState()
				.observe(this, this::onAddContactStateChanged);
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
		if (result instanceof ContactExchangeResult.Success) {
			ContactExchangeResult.Success success =
					(ContactExchangeResult.Success) result;
			if (invitationGroupId != null) {
				sendGroupInvitation(success.contact);
			} else {
				String contactName = success.contact.getAuthor().getName();
				int message = joinGroupQr
						? R.string.groups_qr_waiting_for_invitation
						: success.alreadyExisted
								? R.string.contact_already_exists
								: R.string.contact_added_toast;
				String text = getString(message, contactName);
				Toast.makeText(this, text, LENGTH_LONG).show();
				supportFinishAfterTransition();
			}
		} else if (result instanceof ContactExchangeResult.Error) {
			Author duplicateAuthor =
					((ContactExchangeResult.Error) result).duplicateAuthor;
			if (duplicateAuthor == null) {
				showErrorFragment();
			} else {
				String contactName = duplicateAuthor.getName();
				String text =
						getString(R.string.contact_already_exists, contactName);
				Toast.makeText(this, text, LENGTH_LONG).show();
				supportFinishAfterTransition();
			}
		} else throw new AssertionError();
	}

	private void sendGroupInvitation(Contact contact) {
		GroupId groupId = requireNonNull(invitationGroupId);
		createGroupController.sendInvitation(groupId,
				singleton(contact.getId()), null,
				new UiResultExceptionHandler<Void, DbException>(this) {
					@Override
					public void onResultUi(Void result) {
						String text = getString(
								R.string.groups_qr_invitation_sent,
								contact.getAuthor().getName());
						Toast.makeText(AddNearbyContactActivity.this, text,
								LENGTH_LONG).show();
						setResult(RESULT_OK);
						supportFinishAfterTransition();
					}

					@Override
					public void onExceptionUi(DbException exception) {
						setResult(RESULT_CANCELED);
						handleException(exception);
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
