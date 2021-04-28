/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2013-2021 Threema GmbH
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

package ch.threema.app.activities;

import android.content.Intent;
import android.content.res.Configuration;
import android.media.AudioManager;
import android.os.Bundle;
import android.preference.PreferenceActivity;
import android.widget.FrameLayout;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentManager;
import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.dialogs.GenericAlertDialog;
import ch.threema.app.fragments.ComposeMessageFragment;
import ch.threema.app.fragments.MessageSectionFragment;
import ch.threema.app.listeners.MessagePlayerListener;
import ch.threema.app.managers.ListenerManager;
import ch.threema.app.messagereceiver.MessageReceiver;
import ch.threema.app.preference.SettingsActivity;
import ch.threema.app.preference.SettingsSecurityFragment;
import ch.threema.app.services.DeadlineListService;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.HiddenChatUtil;
import ch.threema.app.utils.IntentDataUtil;
import ch.threema.localcrypto.MasterKey;
import ch.threema.storage.models.AbstractMessageModel;

public class ComposeMessageActivity extends ThreemaToolbarActivity implements GenericAlertDialog.DialogClickListener {
	private static final Logger logger = LoggerFactory.getLogger(ComposeMessageActivity.class);

	private static final int ID_HIDDEN_CHECK_ON_NEW_INTENT = 9291;
	private static final int ID_HIDDEN_CHECK_ON_CREATE = 9292;
	private static final String DIALOG_TAG_HIDDEN_NOTICE = "hidden";

	private ComposeMessageFragment composeMessageFragment;
	private MessageSectionFragment messageSectionFragment;

	private Intent currentIntent;

	private final String COMPOSE_FRAGMENT_TAG = "compose_message_fragment";
	private final String MESSAGES_FRAGMENT_TAG = "message_section_fragment";

	private final MessagePlayerListener messagePlayerListener = new MessagePlayerListener() {
		@Override
		public void onAudioStreamChanged(int newStreamType) {
			setVolumeControlStream(newStreamType == AudioManager.STREAM_VOICE_CALL ? AudioManager.STREAM_VOICE_CALL : AudioManager.USE_DEFAULT_STREAM_TYPE);
		}

		@Override
		public void onAudioPlayEnded(AbstractMessageModel messageModel) { }
	};

	@Override
	public void onCreate(Bundle savedInstanceState) {
		logger.debug("onCreate");

		super.onCreate(savedInstanceState);

		this.currentIntent = getIntent();

		ListenerManager.messagePlayerListener.add(this.messagePlayerListener);

		//check master key
		MasterKey masterKey = ThreemaApplication.getMasterKey();

		if (!(masterKey != null && masterKey.isLocked())) {
			this.initActivity(savedInstanceState);
		}
	}

	@Override
	protected boolean initActivity(Bundle savedInstanceState) {
		if (!super.initActivity(savedInstanceState)) {
			return false;
		}

		logger.debug("initActivity");

		checkHiddenChatLock(getIntent(), ID_HIDDEN_CHECK_ON_CREATE);

		this.getFragments();

		if (findViewById(R.id.messages) != null) {
			// add messages fragment in tablet layout
			if (messageSectionFragment == null) {
				messageSectionFragment = new MessageSectionFragment();
				getSupportFragmentManager().beginTransaction().add(R.id.messages, messageSectionFragment, MESSAGES_FRAGMENT_TAG).commit();
			}
		}

		if (composeMessageFragment == null) {
			// fragment no longer around
			composeMessageFragment = new ComposeMessageFragment();
			getSupportFragmentManager().beginTransaction().add(R.id.compose, composeMessageFragment, COMPOSE_FRAGMENT_TAG).commit();
		}


		return true;
	}

	@Override
	public int getLayoutResource() {
		return ConfigUtils.isTabletLayout(this) ? R.layout.activity_compose_message_tablet : R.layout.activity_compose_message;
	}

	private void getFragments() {
		FragmentManager fragmentManager = getSupportFragmentManager();
		composeMessageFragment = (ComposeMessageFragment) fragmentManager.findFragmentByTag(COMPOSE_FRAGMENT_TAG);
		messageSectionFragment = (MessageSectionFragment) fragmentManager.findFragmentByTag(MESSAGES_FRAGMENT_TAG);
	}

	@Override
	public void onNewIntent(Intent intent) {
		logger.debug("onNewIntent");

		super.onNewIntent(intent);

		this.currentIntent = intent;

		this.getFragments();

		if (composeMessageFragment != null) {
			if (!checkHiddenChatLock(intent, ID_HIDDEN_CHECK_ON_NEW_INTENT)) {
				composeMessageFragment.onNewIntent(intent);
			}
		}
	}

	@Override
	public void onBackPressed() {
		logger.debug("onBackPressed");
		if (ConfigUtils.isTabletLayout()) {
			if (messageSectionFragment != null) {
				if (messageSectionFragment.onBackPressed()) {
					return;
				}
			}
		}
		if (composeMessageFragment != null) {
			if (!composeMessageFragment.onBackPressed()) {
				finish();
				overridePendingTransition(0, 0);
			}
			return;
		}
		super.onBackPressed();
	}

	@Override
	public void onDestroy() {
		logger.debug("onDestroy");

		ListenerManager.messagePlayerListener.remove(this.messagePlayerListener);

		super.onDestroy();
	}

	@Override
	public void onStop() {
		logger.debug("onStop");

		super.onStop();
	}

	@Override
	public void onResume() {
		logger.debug("onResume");
		super.onResume();
	}

	@Override
	public void onPause() {
		logger.debug("onPause");

		super.onPause();
	}

	@Override
	public void onWindowFocusChanged(boolean hasFocus) {
		logger.debug("onWindowFocusChanged " + hasFocus);
		super.onWindowFocusChanged(hasFocus);

		if (ConfigUtils.isSamsungDevice() && !ConfigUtils.isTabletLayout() && composeMessageFragment != null) {
            composeMessageFragment.onWindowFocusChanged(hasFocus);
        }
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode,
								 final Intent intent) {
		switch (requestCode) {
			case ID_HIDDEN_CHECK_ON_CREATE:
				super.onActivityResult(requestCode, resultCode, intent);

				if (resultCode == RESULT_OK) {
					serviceManager.getScreenLockService().setAuthenticated(true);
					if (composeMessageFragment != null) {
						// mark conversation as read as soon as it's unhidden
						composeMessageFragment.markAsRead();
					}
				} else {
					finish();
				}
				break;
			case ID_HIDDEN_CHECK_ON_NEW_INTENT:
				super.onActivityResult(requestCode, resultCode, intent);

				if (resultCode == RESULT_OK) {
					serviceManager.getScreenLockService().setAuthenticated(true);
					if (composeMessageFragment != null) {
						composeMessageFragment.onNewIntent(this.currentIntent);
					}
				}
				break;
			case ThreemaActivity.ACTIVITY_ID_UNLOCK_MASTER_KEY:
				if (ThreemaApplication.getMasterKey().isLocked()) {
					finish();
				} else {
					ConfigUtils.recreateActivity(this, ComposeMessageActivity.class, getIntent().getExtras());
				}
				break;
			default:
				super.onActivityResult(requestCode, resultCode, intent);

				// required for result of qr code scanner
				if (composeMessageFragment != null) {
					composeMessageFragment.onActivityResult(requestCode, resultCode, intent);
				}
		}
	}

	private boolean checkHiddenChatLock(Intent intent, int requestCode) {
		MessageReceiver messageReceiver = IntentDataUtil.getMessageReceiverFromIntent(getApplicationContext(), intent);

		if (messageReceiver != null && serviceManager != null) {
			DeadlineListService hiddenChatsListService = serviceManager.getHiddenChatsListService();
			if (hiddenChatsListService != null && hiddenChatsListService.has(messageReceiver.getUniqueIdString())) {
				if (preferenceService != null && ConfigUtils.hasProtection(preferenceService)) {
					HiddenChatUtil.launchLockCheckDialog(this, null, preferenceService, requestCode);
				} else {
					GenericAlertDialog.newInstance(R.string.hide_chat, R.string.hide_chat_enter_message_explain, R.string.set_lock, R.string.cancel).show(getSupportFragmentManager(), DIALOG_TAG_HIDDEN_NOTICE);
				}
				return true;
			}
		}
		return false;
	}

	@Override
	public void onConfigurationChanged(@NonNull Configuration newConfig) {
		super.onConfigurationChanged(newConfig);

		ConfigUtils.adjustToolbar(this, getToolbar());

		FrameLayout messagesLayout = findViewById(R.id.messages);

		if (messagesLayout != null) {
			// adjust width of messages fragment in tablet layout
			FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) messagesLayout.getLayoutParams();
			layoutParams.width = getResources().getDimensionPixelSize(R.dimen.message_fragment_width);
			messagesLayout.setLayoutParams(layoutParams);
		}
	}

	@Override
	public void onYes(String tag, Object data) {
		Intent intent = new Intent(this, SettingsActivity.class);
		intent.putExtra(PreferenceActivity.EXTRA_SHOW_FRAGMENT, SettingsSecurityFragment.class.getName());
		intent.putExtra(PreferenceActivity.EXTRA_NO_HEADERS, true);
		startActivity(intent);
		finish();
	}

	@Override
	public void onNo(String tag, Object data) {
		finish();
	}
}
