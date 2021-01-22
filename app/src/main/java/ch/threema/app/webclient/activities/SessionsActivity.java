/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2016-2021 Threema GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License, version 3,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package ch.threema.app.webclient.activities;

import android.Manifest;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Vibrator;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;

import org.saltyrtc.client.crypto.CryptoException;
import org.saltyrtc.client.exceptions.InvalidKeyException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;
import androidx.appcompat.app.ActionBar;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import ch.threema.app.utils.QRScannerUtil;
import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.activities.DisableBatteryOptimizationsActivity;
import ch.threema.app.activities.ThreemaToolbarActivity;
import ch.threema.app.dialogs.GenericAlertDialog;
import ch.threema.app.dialogs.SelectorDialog;
import ch.threema.app.dialogs.SimpleStringAlertDialog;
import ch.threema.app.dialogs.TextEntryDialog;
import ch.threema.app.managers.ListenerManager;
import ch.threema.app.ui.EmptyRecyclerView;
import ch.threema.app.ui.SilentSwitchCompat;
import ch.threema.app.utils.AppRestrictionUtil;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.DialogUtil;
import ch.threema.app.utils.IntentDataUtil;
import ch.threema.app.utils.LogUtil;
import ch.threema.app.utils.RuntimeUtil;
import ch.threema.app.utils.TestUtil;
import ch.threema.app.webclient.Protocol;
import ch.threema.app.webclient.adapters.SessionListAdapter;
import ch.threema.app.webclient.exceptions.HandshakeException;
import ch.threema.app.webclient.listeners.WebClientServiceListener;
import ch.threema.app.webclient.listeners.WebClientSessionListener;
import ch.threema.app.webclient.manager.WebClientListenerManager;
import ch.threema.app.webclient.manager.WebClientServiceManager;
import ch.threema.app.webclient.services.QRCodeParser;
import ch.threema.app.webclient.services.QRCodeParserImpl;
import ch.threema.app.webclient.services.SessionService;
import ch.threema.app.webclient.services.instance.DisconnectContext;
import ch.threema.app.webclient.services.instance.SessionInstanceService;
import ch.threema.app.webclient.state.WebClientSessionState;
import ch.threema.base.ThreemaException;
import ch.threema.client.Base64;
import ch.threema.storage.DatabaseServiceNew;
import ch.threema.storage.models.WebClientSessionModel;

@UiThread
public class SessionsActivity extends ThreemaToolbarActivity
		implements SelectorDialog.SelectorDialogClickListener,
		GenericAlertDialog.DialogClickListener, TextEntryDialog.TextEntryDialogClickListener {
	@NonNull private static final Logger logger = LoggerFactory.getLogger(SessionsActivity.class);

	@NonNull private static final String LOG_TAG = "WebClient.SessionFragment";
	@NonNull private static final String DIALOG_TAG_ITEM_MENU = "itemMenu";
	@NonNull private static final String DIALOG_TAG_REALLY_DELETE_SESSION = "deleteSession";
	@NonNull private static final String DIALOG_TAG_REALLY_DELETE_ALL_SESSIONS = "deleteAllSession";
	@NonNull private static final String DIALOG_TAG_REALLY_START_SESSION_BY_PAYLOAD  = "startByPayload";
	@NonNull private static final String DIALOG_TAG_EDIT_LABEL = "editLabel";

	private static final int REQUEST_ID_INTRO_WIZARD = 338;
	private static final int REQUEST_ID_DISABLE_BATTERY_OPTIMIZATIONS = 339;

	private static final int MENU_POS_RENAME = 0;
	private static final int MENU_POS_START_STOP = 1;
	private static final int MENU_POS_REMOVE = 2;

	private static final int PERMISSION_REQUEST_CAMERA = 1;

	@NonNull private static final String DIALOG_TAG_MDM_CONSTRAINTS = "webConstrainedByAdmin";

	// Threema services
	private WebClientServiceManager webClientServiceManager;
	private SessionService sessionService;
	private DatabaseServiceNew databaseService;

	private EmptyRecyclerView listView;
	private SessionListAdapter listAdapter;
	private boolean initialized = false;
	private SilentSwitchCompat enableSwitch;
	private ExtendedFloatingActionButton floatingActionButton;

	/**
	 * Called for all WebClientService related events.
	 */
	@NonNull private final WebClientServiceListener webClientServiceListener = new WebClientServiceListener() {
		@Override
		@AnyThread
		public void onEnabled() {
			this.updateView(false);
		}

		@Override
		@AnyThread
		public void onDisabled() {
			this.updateView(false);
		}

		@Override
		@AnyThread
		public void onStarted(
			@NonNull final WebClientSessionModel model,
			@NonNull final byte[] permanentKey,
			@NonNull final String browser
		) {
			this.updateView(false);
		}

		@Override
		@AnyThread
		public void onStateChanged(
			@NonNull final WebClientSessionModel model,
			@NonNull final WebClientSessionState oldState,
			@NonNull final WebClientSessionState newState
		) {
			this.updateView(true);
		}

		@Override
		@AnyThread
		public void onStopped(@NonNull final WebClientSessionModel model, @NonNull final DisconnectContext reason) {
			this.updateView(true);
		}

		@Override
		@AnyThread
		public void onPushTokenChanged(@NonNull final WebClientSessionModel model, @Nullable final String newPushToken) {
			this.updateView(true);
		}

		private void updateView(final boolean notifyDataSetChanged) {
			RuntimeUtil.runOnUiThread(new Runnable() {
				@Override
				@UiThread
				public void run() {
					if (notifyDataSetChanged && SessionsActivity.this.listAdapter != null) {
						SessionsActivity.this.listAdapter.notifyDataSetChanged();
					}
					SessionsActivity.this.updateView();
				}
			});
		}
	};

	/**
	 * Called when a session is changed.
	 */
	@NonNull private final WebClientSessionListener webClientSessionListener = new WebClientSessionListener() {
		@Override
		@AnyThread
		public void onModified(@NonNull final WebClientSessionModel model) {
			RuntimeUtil.runOnUiThread(new Runnable() {
				@Override
				@UiThread
				public void run() {
					final SessionListAdapter listAdapter = SessionsActivity.this.listAdapter;
					if (listAdapter != null) {
						for (int pos = 0; pos < listAdapter.getItemCount(); pos++) {
							if (listAdapter.getEntity(pos).getId() == model.getId()) {
								// Update model in list adapter
								listAdapter.setEntity(pos, model);

								// Notify adapter about changes
								listAdapter.notifyItemChanged(pos);

								// Move session to top
								if (pos != 0) {
									SessionsActivity.this.closeAllDialogs();
									listAdapter.moveEntity(pos, 0);
								}
								return;
							}
						}
					}
				}
			});
		}

		@Override
		@AnyThread
		public void onRemoved(@NonNull final WebClientSessionModel model) {
			RuntimeUtil.runOnUiThread(new Runnable() {
				@Override
				@UiThread
				public void run() {
					if (listAdapter != null) {
						final SessionListAdapter listAdapter = SessionsActivity.this.listAdapter;
						for (int pos = 0; pos < listAdapter.getItemCount(); pos++) {
							if (listAdapter.getEntity(pos).getId() == model.getId()) {
								// Remove session from list
								SessionsActivity.this.closeAllDialogs();
								listAdapter.deleteEntity(pos);
							}
						}
					}
				}
			});
		}

		@Override
		@AnyThread
		public void onCreated(@NonNull final WebClientSessionModel model) {
			RuntimeUtil.runOnUiThread(new Runnable() {
				@Override
				public void run() {
					final SessionListAdapter listAdapter = SessionsActivity.this.listAdapter;
					if (listAdapter != null) {
						// Move session to top
						SessionsActivity.this.closeAllDialogs();
						listAdapter.addEntity(0, model);
					}
				}
			});
		}
	};

	private boolean activityInitialized = false;

	/**
	 * Make sure that all open dialogs are closed.
	 */
	private void closeAllDialogs() {
		DialogUtil.dismissDialog(getSupportFragmentManager(), DIALOG_TAG_ITEM_MENU, true);
		DialogUtil.dismissDialog(getSupportFragmentManager(), DIALOG_TAG_REALLY_DELETE_SESSION, true);
		DialogUtil.dismissDialog(getSupportFragmentManager(), DIALOG_TAG_REALLY_DELETE_ALL_SESSIONS, true);
		DialogUtil.dismissDialog(getSupportFragmentManager(), DIALOG_TAG_EDIT_LABEL, true);
	}

	private boolean requireInstances() {
		if (TestUtil.required(
				this.webClientServiceManager,
				this.sessionService,
				this.databaseService
		)) {
			return true;
		}

		try {
			if (this.serviceManager == null) {
				logger.error("Service manager is null");
				return false;
			}
			this.webClientServiceManager = this.serviceManager.getWebClientServiceManager();
			this.sessionService = this.webClientServiceManager.getSessionService();
			this.databaseService = this.serviceManager.getDatabaseServiceNew();
		} catch (ThreemaException e) {
			logger.error("Exception", e);
			return false;
		}

		return TestUtil.required(
				this.webClientServiceManager,
				this.sessionService,
				this.databaseService
		);
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		// Make sure that all necessary services are initialized
		if (!this.requireInstances()) {
			this.finish();
			return;
		}

		if (ConfigUtils.isWorkRestricted() && AppRestrictionUtil.isWebDisabled(this)) {
			final String msg = getString(R.string.webclient_cannot_restore) + ": "
				+ getString(R.string.webclient_disabled);
			Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
			this.finish();
			return;
		}

		// Remove old sessions
		this.cleanupWebclientSessions();

		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);

		ActionBar actionBar = getSupportActionBar();
		if (actionBar != null) {
			actionBar.setDisplayHomeAsUpEnabled(true);
			actionBar.setTitle(R.string.webclient);
		}

		this.enableSwitch = findViewById(R.id.switch_button);
		TextView enableSwitchText = findViewById(R.id.switch_text);
		this.enableSwitch.setOnOffLabel(enableSwitchText);
		this.enableSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
			@Override
			@UiThread
			public void onCheckedChanged(CompoundButton compoundButton, boolean isChecked) {
				if (compoundButton.isShown()) {
					if (isChecked) {
						if (SessionsActivity.this.sessionService.getAllSessionModels().size() == 0) {
							//if there are no active sessions, start the qr code scanner
							//when the enable switch is enabled.
							SessionsActivity.this.initiateSession();
						} else {
							SessionsActivity.this.sessionService.setEnabled(true);
						}
					} else {
						SessionsActivity.this.sessionService.setEnabled(false);
					}
				}
			}
		});
		this.enableSwitch.setCheckedSilent(this.sessionService.isEnabled());

		final LinearLayoutManager linearLayoutManager = new LinearLayoutManager(this);
		this.listView = this.findViewById(R.id.recycler);
		this.listView.setHasFixedSize(true);
		this.listView.setLayoutManager(linearLayoutManager);
		this.listView.setItemAnimator(new DefaultItemAnimator());
		this.listView.addOnScrollListener(new RecyclerView.OnScrollListener() {
			@Override
			@UiThread
			public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy){
				super.onScrolled(recyclerView, dx, dy);

				if (linearLayoutManager.findFirstVisibleItemPosition() == 0) {
					floatingActionButton.extend();
				} else {
					floatingActionButton.shrink();
				}
			}
		});

		floatingActionButton = this.findViewById(R.id.floating);
		floatingActionButton.setVisibility(View.VISIBLE);
		floatingActionButton.setOnClickListener(new View.OnClickListener() {
			@Override
			@UiThread
			public void onClick(View v) {
				SessionsActivity.this.initiateSession();
			}
		});

		View emptyView = this.findViewById(R.id.empty_frame);
		this.listView.setEmptyView(emptyView);
		this.reloadSessionList();

		if (savedInstanceState == null) {
			final boolean welcomeScreenShown = sharedPreferences.getBoolean(getString(R.string.preferences__web_client_welcome_shown), false);
			final boolean sessionsAvailable = this.sessionService.getAllSessionModels().size() > 0;
			if (!welcomeScreenShown && !sessionsAvailable) {
				// Show wizard
				this.startActivityForResult(new Intent(this, SessionsIntroActivity.class), REQUEST_ID_INTRO_WIZARD);
			} else {
				this.startBatteryOptimizationFlow();
			}
		}
	}

	/**
	 * Make sure that battery optimizations are disabled for Threema.
	 */
	private void startBatteryOptimizationFlow() {
		// start battery optimization flow. activity will return RESULT_OK is app is already whitelisted
		Intent intent = new Intent(this, DisableBatteryOptimizationsActivity.class);
		intent.putExtra(DisableBatteryOptimizationsActivity.EXTRA_NAME, getString(R.string.webclient));
		intent.putExtra(DisableBatteryOptimizationsActivity.EXTRA_CONFIRM, true);
		this.startActivityForResult(intent, REQUEST_ID_DISABLE_BATTERY_OPTIMIZATIONS);
	}

	/**
	 * Called after confirm battery confirmation.
	 */
	private void activityInitialized() {
		if (!this.activityInitialized) {
			this.activityInitialized = true;
			//check for a payload
			byte[] intentPayload = IntentDataUtil.getPayload(this.getIntent());
			if (intentPayload != null) {
				// Ask first
				if (RuntimeUtil.isInTest()) {
					// Add directly
					this.processPayload(intentPayload);
				} else {
					logger.info("Requesting to start Threema Web session from external scan");
					GenericAlertDialog dialogFragment = GenericAlertDialog.newInstance(
						R.string.webclient_session_start,
						R.string.webclient_really_start_webclient_by_payload_body,
						R.string.yes,
						R.string.no);
					dialogFragment.setData(intentPayload);
					dialogFragment.show(getSupportFragmentManager(), DIALOG_TAG_REALLY_START_SESSION_BY_PAYLOAD);
				}
			}
		}
	}

	private void processPayload(byte[] payload) {
		try {
			final QRCodeParserImpl qrCodeParser = new QRCodeParserImpl();
			final QRCodeParser.Result qrResult = qrCodeParser.parse(payload);
			this.startByQrResult(qrResult);
		} catch (QRCodeParser.InvalidQrCodeException invalidQRCode) {
			// ignore and log
			logger.error("Invalid QR code", invalidQRCode);
		}
	}

	public int getLayoutResource() {
		return R.layout.activity_sessions;
	}

	private void updateView() {
		if (this.enableSwitch != null) {
			if (this.enableSwitch.isChecked() != this.sessionService.isEnabled()) {
				this.enableSwitch.setCheckedSilent(this.sessionService.isEnabled());
			}
		}
	}

	/**
	 * Create or refresh list adapter with sessions.
	 */
	private void reloadSessionList() {
		if (this.listAdapter == null) {
			this.listAdapter = new SessionListAdapter(this, this.sessionService, this.preferenceService);
			this.listAdapter.setOnClickItemListener(new SessionListAdapter.OnClickItemListener() {
				@Override
				@UiThread
				public void onClick(WebClientSessionModel model, int position) {
					SessionsActivity.this.onSessionItemClicked(model);
				}
			});
			this.listView.setAdapter(this.listAdapter);
		}

		this.listAdapter.setData(this.sessionService.getAllSessionModels());
	}

	/**
	 * Session list item was clicked.
	 */
	private void onSessionItemClicked(WebClientSessionModel model) {
		if (model != null) {
			ArrayList<String> items = new ArrayList<>();
			ArrayList<Integer> values = new ArrayList<>();

			items.add(this.getString(R.string.webclient_session_rename));
			values.add(MENU_POS_RENAME);

			if (model.getState() != WebClientSessionModel.State.INITIALIZING) {
				items.add(this.getString(!this.sessionService.isRunning(model)
						? R.string.webclient_session_start
						: R.string.webclient_session_stop));
				values.add(MENU_POS_START_STOP);
			}

			items.add(this.getString(R.string.webclient_session_remove));
			values.add(MENU_POS_REMOVE);

			SelectorDialog selectorDialog = SelectorDialog.newInstance(null, items, values, null);
			selectorDialog.setData(model);
			selectorDialog.show(getSupportFragmentManager(), DIALOG_TAG_ITEM_MENU);
		}
	}

	/**
	 * Session list item context menu entry was clicked.
	 */
	@Override
	public void onClick(String tag, int which, Object data) {
		if (DIALOG_TAG_ITEM_MENU.equals(tag)) {
			if (data instanceof WebClientSessionModel) {
				WebClientSessionModel model = (WebClientSessionModel) data;
				switch (which) {
					case MENU_POS_START_STOP:
						this.startStopSession(model);
						break;
					case MENU_POS_RENAME:
						this.renameSession(model);
						break;
					case MENU_POS_REMOVE:
						this.removeSession(model);
						break;
				}

			}
		}
	}

	private void removeSession(@NonNull final WebClientSessionModel sessionModel) {
		GenericAlertDialog dialog = GenericAlertDialog.newInstance(R.string.webclient_session_remove,
				getString(R.string.webclient_sessions_really_delete),
				R.string.ok,
				R.string.cancel);
		dialog.setData(sessionModel);
		dialog.show(getSupportFragmentManager(), DIALOG_TAG_REALLY_DELETE_SESSION);
	}

	private void startStopSession(@NonNull final WebClientSessionModel sessionModel) {
		if (!this.sessionService.isRunning(sessionModel)) {
			final SessionInstanceService service = this.sessionService.getInstanceService(sessionModel, true);
			if (service == null) {
				logger.error("cannot start service, cannot instantiate session instance service");
				return;
			}

			// enable webclient session if disabled
			if (!this.sessionService.isEnabled()) {
				this.sessionService.setEnabled(true);
			}

			this.webClientServiceManager.getHandler().post(new Runnable() {
				@Override
				@WorkerThread
				public void run() {
					try {
						service.resume(null);
					} catch (CryptoException error) {
						logger.error("Could not resume session", error);
					}
				}
			});
		} else {
			final SessionInstanceService service = this.sessionService.getInstanceService(sessionModel, false);

			if (service == null) {
				logger.error("cannot stop service, no running service");
				return;
			}

			this.webClientServiceManager.getHandler().post(new Runnable() {
				@Override
				@WorkerThread
				public void run() {
					service.stop(DisconnectContext.byUs(DisconnectContext.REASON_SESSION_STOPPED));
				}
			});
		}
	}

	private void renameSession(@NonNull final WebClientSessionModel sessionModel) {
		TextEntryDialog.newInstance(R.string.webclient_session_rename,
				R.string.webclient_session_label,
				R.string.ok,
				0,
				R.string.cancel,
				sessionModel.getLabel(),
				0, TextEntryDialog.INPUT_FILTER_TYPE_NONE, 64).show(getSupportFragmentManager(), DIALOG_TAG_EDIT_LABEL + sessionModel.getId());
	}

	@Override
	public void onCancel(String tag) { }

	@Override
	public void onNo(String tag) { }

	@Override
	public void onYes(String tag, Object data) {
		switch (tag) {
			case DIALOG_TAG_REALLY_DELETE_SESSION:
				if (data instanceof WebClientSessionModel) {
					this.sessionService.stop(
						(WebClientSessionModel) data, DisconnectContext.byUs(DisconnectContext.REASON_SESSION_DELETED));
				}
				break;
			case DIALOG_TAG_REALLY_DELETE_ALL_SESSIONS:
				this.sessionService.stopAll(DisconnectContext.byUs(DisconnectContext.REASON_SESSION_DELETED));
				break;
			case DIALOG_TAG_REALLY_START_SESSION_BY_PAYLOAD:
				if (data != null) {
					this.processPayload((byte[]) data);
				}
				break;
		}
	}

	@Override
	public void onNo(String tag, Object data) {	}

	/**
	 * Handle renaming of session labels.
	 */
	@Override
	public void onYes(String tag, String text) {
		if (tag.startsWith(DIALOG_TAG_EDIT_LABEL)) {
			// The model id is appended to the tag. To get it, strip the prefix.
			int modelId = Integer.parseInt(tag.substring(DIALOG_TAG_EDIT_LABEL.length()));

			//simply search list for this id
			for (int pos = 0; pos < listAdapter.getItemCount(); pos++) {
				final WebClientSessionModel model = this.listAdapter.getEntity(pos);
				if (model.getId() == modelId) {
					model.setLabel(text);
					// UGH!
					if (this.databaseService.getWebClientSessionModelFactory().createOrUpdate(model)) {
						WebClientListenerManager.sessionListener.handle(new ListenerManager.HandleListener<WebClientSessionListener>() {
							@Override
							@UiThread
							public void handle(WebClientSessionListener listener) {
								listener.onModified(model);
							}
						});
					}
					return;
				}
			}
		}
	}

	@Override
	public void onNeutral(String tag) {	}

	/**
	 * Initiate a session by starting the QR code scanner.
	 */
	private void initiateSession() {
		//start the qr scanner
		if (ConfigUtils.requestCameraPermissions(this, null, PERMISSION_REQUEST_CAMERA)) {
			this.scanQR();
		} else if (sessionService.getAllSessionModels().size() == 0){
			enableSwitch.setCheckedSilent(false);
		}
	}

	private void scanQR() {
		logger.info("Initiate QR scan");
		QRScannerUtil.getInstance().initiateScan(this, false, getString(R.string.webclient_qr_scan_message));
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent intent) {
		super.onActivityResult(requestCode, resultCode, intent);

		ConfigUtils.setLocaleOverride(this, this.serviceManager.getPreferenceService());

		switch (requestCode) {
			case REQUEST_ID_INTRO_WIZARD:
				if (resultCode == RESULT_OK) {
					this.startBatteryOptimizationFlow();
				} else {
					this.finish();
				}
				break;
			case REQUEST_ID_DISABLE_BATTERY_OPTIMIZATIONS:
				if (resultCode != RESULT_OK) {
					this.finish();
				}
				else {
					this.activityInitialized();
				}
				break;
			default:
				if (resultCode == RESULT_OK) {
					// return from QR scan
					String payload = QRScannerUtil.getInstance().parseActivityResult(this, requestCode, resultCode, intent);

					if (!TestUtil.empty(payload)) {
						final QRCodeParser qrCodeParser = new QRCodeParserImpl();

						try {
							final byte[] pl = Base64.decode(payload);
							final QRCodeParser.Result qrResult = qrCodeParser.parse(pl);
							this.startByQrResult(qrResult);
						} catch (QRCodeParser.InvalidQrCodeException | IOException e) {
							logger.error("Exception", e);
							//show a generic error message
							GenericAlertDialog.newInstance(R.string.webclient_init_session, R.string.webclient_invalid_qr_code, R.string.ok, 0).show(getSupportFragmentManager(), "foo");
						}
					}
				}

				this.updateView();
		}
	}

	private void startByQrResult(@NonNull final QRCodeParser.Result qrCodeResult) {
		// Validate protocol version
		if (qrCodeResult.versionNumber != Protocol.PROTOCOL_VERSION) {
			// Wrong protocol version!
			logger.error("Scanned QR code with protocol version {}, but we only support {}",
				qrCodeResult.versionNumber, Protocol.PROTOCOL_VERSION);

			// Determine appropriate error message to show
			int errorMessage;
			if (qrCodeResult.versionNumber > Protocol.PROTOCOL_VERSION) {
				errorMessage = R.string.webclient_protocol_version_to_old;
			} else {
				if (qrCodeResult.isSelfHosted) {
					errorMessage = R.string.webclient_protocol_version_too_new_selfhosted;
				} else {
					errorMessage = R.string.webclient_protocol_version_too_new_threema;
				}
			}

			// Show error message
			GenericAlertDialog.newInstance(
				R.string.webclient_protocol_error,
				errorMessage,
				R.string.close,
				0
			).show(getSupportFragmentManager(), "error");
			return;
		}

		// Check internet connection
		if(!ThreemaApplication.getServiceManager().getDeviceService().isOnline()) {
			logger.error("No internet connection");
			GenericAlertDialog.newInstance(
				R.string.internet_connection_required,
				R.string.connection_error,
				R.string.close,
				0
			).show(getSupportFragmentManager(), "error");
			return;
		}

		// Ensure that session does not already exist
		final WebClientSessionModel sessionModel =
			this.databaseService.getWebClientSessionModelFactory().getByKey(qrCodeResult.key);
		if (sessionModel != null) {
			// We scanned the QR code of a session that already exists! Something's wrong.
			logger.error("Session already exists");
			GenericAlertDialog.newInstance(
				R.string.webclient_protocol_error,
				R.string.webclient_session_already_exists,
				R.string.close,
				0
			).show(getSupportFragmentManager(), "error");
			return;
		}

		// Success! Start new session.
		logger.debug(LOG_TAG, "Start with QR result: " + qrCodeResult);
		try {
			this.vibrate();
			this.start(qrCodeResult);
		} catch (IllegalArgumentException e) {
			LogUtil.exception(e, this);
		}

		this.updateView();
	}

	/**
	 * Vibrate quickly to indicate that the session has been started successfully.
	 */
	private void vibrate() {
		final Vibrator vibrator = (Vibrator) this.getSystemService(Context.VIBRATOR_SERVICE);
		final AudioManager audioManager = (AudioManager) this.getSystemService(Context.AUDIO_SERVICE);
		if (vibrator != null && audioManager != null) {
			switch (audioManager.getRingerMode()) {
				case AudioManager.RINGER_MODE_VIBRATE:
				case AudioManager.RINGER_MODE_NORMAL:
					vibrator.vibrate(100);
					break;
				default:
					break;
			}
		}
	}

	/**
	 * Start the webclient service.
	 * Connect asynchronously.
	 */
	private void start(@NonNull final QRCodeParser.Result qrCodeResult) {
		logger.info("Starting Threema Web session");

		// MDM constraints
		if (ConfigUtils.isWorkRestricted()) {
			// Threema Web may be disabled
			if (AppRestrictionUtil.isWebDisabled(this)) {
				final String msg = getString(R.string.webclient_cannot_restore) + ": "
					+ getString(R.string.webclient_disabled);
				Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
				this.finish();
				return;
			}

			// Signaling hosts may be constrained
			if (!AppRestrictionUtil.isWebHostAllowed(this, qrCodeResult.saltyRtcHost)) {
				final SimpleStringAlertDialog dialog = SimpleStringAlertDialog.newInstance(
					R.string.webclient_cannot_start,
					R.string.webclient_constrained_by_mdm
				);
				dialog.show(getSupportFragmentManager(), DIALOG_TAG_MDM_CONSTRAINTS);
				return;
			}
		}

		try {
			// Make sure that all listeners are initialized
			this.init();

			// Create new session
			this.sessionService.create(
				qrCodeResult.key,
				qrCodeResult.authToken,
				qrCodeResult.saltyRtcHost,
				qrCodeResult.saltyRtcPort,
				qrCodeResult.serverKey,
				qrCodeResult.isPermanent,
				qrCodeResult.isSelfHosted,
				null
			);
		}
		catch (HandshakeException | InvalidKeyException | ThreemaException x) {
			LogUtil.exception(x, this);
		}
	}

	/**
	 * Make sure that all listeners are initialized.
	 */
	private void init() {
		if (!this.initialized) {
			WebClientListenerManager.sessionListener.add(this.webClientSessionListener);
			WebClientListenerManager.serviceListener.add(this.webClientServiceListener);
			this.initialized = true;
		}
	}

	/**
	 * Make sure that all listeners are removed.
	 */
	private void deinit() {
		if (this.initialized) {
			WebClientListenerManager.sessionListener.remove(this.webClientSessionListener);
			WebClientListenerManager.serviceListener.remove(this.webClientServiceListener);
			this.initialized = false;
		}
	}

	@Override
	public void onStart() {
		super.onStart();
		this.init();
	}

	@Override
	public void onStop() {
		this.deinit();
		super.onStop();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		super.onCreateOptionsMenu(menu);
		this.getMenuInflater().inflate(R.menu.activity_webclient_sessions, menu);

		ConfigUtils.addIconsToOverflowMenu(this, menu);

		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case android.R.id.home:
				this.finish();
				break;
			case R.id.menu_help:
				this.startActivity(new Intent(this, SessionsIntroActivity.class));
				break;
			case R.id.menu_clear_all:
				GenericAlertDialog dialog = GenericAlertDialog.newInstance(R.string.webclient_clear_all_sessions,
					getString(R.string.webclient_clear_all_sessions_confirm),
					R.string.ok,
					R.string.cancel);
				dialog.show(getSupportFragmentManager(), DIALOG_TAG_REALLY_DELETE_ALL_SESSIONS);
				break;
			default:
				break;
		}
		return super.onOptionsItemSelected(item);
	}

	/**
	 * Delete all non-persistent webclient sessions that have been inactive for 24h.
	 */
	private void cleanupWebclientSessions() {
		final List<WebClientSessionModel> models =
				this.databaseService.getWebClientSessionModelFactory().getAll();
		final long now = System.currentTimeMillis() / 1000;
		for (WebClientSessionModel model : models) {
			// Ignore persistent sessions
			if (model.isPersistent()) {
				continue;
			}

			boolean remove = true;
			final long secondsAgoThreshold = 3600 * 24; // 24h

			// Ignore sessions that have been created in the last 24h.
			if (model.getCreated() != null) {
				final long secondsAgo = now - (model.getCreated().getTime() / 1000);
				if (secondsAgo < secondsAgoThreshold) {
					remove = false;
				}
			}

			// Ignore sessions that have been active in the last 24h.
			if (model.getLastConnection() != null) {
				final long secondsAgo = now - (model.getLastConnection().getTime() / 1000);
				if (secondsAgo < secondsAgoThreshold) {
					remove = false;
				}
			}

			if (remove) {
				this.databaseService.getWebClientSessionModelFactory().delete(model);
			}
		}
	}



	@TargetApi(Build.VERSION_CODES.M)
	@Override
	public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
		if (requestCode == PERMISSION_REQUEST_CAMERA) {
			if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
				this.scanQR();
			} else if (!this.shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
				ConfigUtils.showPermissionRationale(this, findViewById(R.id.parent_layout), R.string.permission_camera_qr_required);
				if (this.sessionService.getAllSessionModels().size() == 0) {
					this.enableSwitch.setCheckedSilent(false);
				}
			}
		}
	}
}
