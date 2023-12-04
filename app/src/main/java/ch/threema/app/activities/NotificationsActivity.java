/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2015-2023 Threema GmbH
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

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.text.format.DateUtils;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.UiThread;
import androidx.appcompat.widget.AppCompatRadioButton;

import com.google.android.material.button.MaterialButton;

import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.dialogs.RingtoneSelectorDialog;
import ch.threema.app.listeners.ContactSettingsListener;
import ch.threema.app.managers.ListenerManager;
import ch.threema.app.managers.ServiceManager;
import ch.threema.app.preference.SettingsActivity;
import ch.threema.app.services.ContactService;
import ch.threema.app.services.ConversationService;
import ch.threema.app.services.DeadlineListService;
import ch.threema.app.services.GroupService;
import ch.threema.app.services.PreferenceService;
import ch.threema.app.services.RingtoneService;
import ch.threema.app.utils.AnimationUtil;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.LogUtil;
import ch.threema.app.utils.RingtoneUtil;
import ch.threema.app.utils.TestUtil;

public abstract class NotificationsActivity extends ThreemaActivity implements View.OnClickListener, RingtoneSelectorDialog.RingtoneSelectorDialogClickListener {

	private static final String BUNDLE_ANIMATION_CENTER = "animC";
	private static final String DIALOG_TAG_RINGTONE_SELECTOR = "drs";
	protected final int MUTE_INDEX_INDEFINITE = -1;
	protected TextView textSoundCustom, textSoundDefault;
	protected AppCompatRadioButton
		radioSoundDefault,
			radioSilentOff,
			radioSilentUnlimited,
			radioSilentLimited,
			radioSilentExceptMentions,
			radioSoundCustom,
			radioSoundNone;
	private MaterialButton settingsButton, plusButton, minusButton;
	private ScrollView parentLayout;
	protected RingtoneService ringtoneService;
	protected ContactService contactService;
	protected GroupService groupService;
	protected ConversationService conversationService;
	protected DeadlineListService mutedChatsListService, mentionOnlyChatListService;
	protected PreferenceService preferenceService;
	protected Uri defaultRingtone, selectedRingtone, backupSoundCustom;
	protected boolean isMuted;
	protected int mutedIndex;
	protected int[] muteValues = {1, 2, 4, 8, 24, 144};
	private int[] animCenterLocation = {0, 0};
	protected String uid;

	private final ActivityResultLauncher<Intent> ringtonePickerLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
		result -> {
			if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
				Uri uri = result.getData().getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI);
				onRingtoneSelected(DIALOG_TAG_RINGTONE_SELECTOR, uri);
			}
		});

	private final ActivityResultLauncher<Intent> ringtoneSettingsLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
		result -> {
			refreshSettings();
			updateUI();
		});

	@Override
	public void onCreate(Bundle savedInstanceState) {
		if (!this.requiredInstances()) {
			return;
		}

		super.onCreate(savedInstanceState);

		supportRequestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.activity_notifications);

		parentLayout = findViewById(R.id.parent_layout);
		loopViewGroup(parentLayout);
		ViewGroup topLayout = (ViewGroup) parentLayout.getParent();
		plusButton = findViewById(R.id.duration_plus);
		minusButton = findViewById(R.id.duration_minus);
		settingsButton = findViewById(R.id.prefs_button);
		Button doneButton = findViewById(R.id.done_button);

		if (ConfigUtils.isWorkBuild()) {
			findViewById(R.id.work_life_warning).setVisibility(preferenceService.isAfterWorkDNDEnabled() ? View.VISIBLE : View.GONE);
		}

		if (savedInstanceState == null) {
			Intent intent = getIntent();
			animCenterLocation = intent.getIntArrayExtra(ThreemaApplication.INTENT_DATA_ANIM_CENTER);

			if (animCenterLocation != null) {
				// see http://stackoverflow.com/questions/26819429/cannot-start-this-animator-on-a-detached-view-reveal-effect
				parentLayout.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
					@Override
					public void onLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) {
						v.removeOnLayoutChangeListener(this);
						parentLayout.setVisibility(View.INVISIBLE);
						AnimationUtil.circularReveal(parentLayout, animCenterLocation[0], animCenterLocation[1], false);
					}
				});
			} else {
				parentLayout.setVisibility(View.VISIBLE);
			}
		} else {
			animCenterLocation = savedInstanceState.getIntArray(BUNDLE_ANIMATION_CENTER);
		}

		parentLayout.setOnClickListener(v -> {
			// ignore clicks
		});

		topLayout.setOnClickListener(v -> onDone());

		doneButton.setOnClickListener(v -> onDone());

		setupButtons();
	}

	@Override
	public void onDestroy() {
		ListenerManager.contactSettingsListeners.handle(new ListenerManager.HandleListener<ContactSettingsListener>() {
			@Override
			public void handle(ContactSettingsListener listener) {
				listener.onNotificationSettingChanged(uid);
			}
		});
		super.onDestroy();
	}

	private void loopViewGroup(ViewGroup group) {
		for(int i = 0; i < group.getChildCount(); i++) {
			View v = group.getChildAt(i);
			if (v instanceof ViewGroup) {
				loopViewGroup((ViewGroup) v);
			} else {
				if (v instanceof AppCompatRadioButton ||
						v instanceof ImageView ||
						v instanceof TextView) {
					v.setOnClickListener(this);
				}
			}
		}
	}

	private int findNextHigherMuteIndex(double hours) {
		for(int i = muteValues.length - 1; i >= 0; i--) {
			if (muteValues[i] < hours) {
				return i + 1;
			}
		}
		return 0;
	}

	protected void refreshSettings() {
		isMuted = mutedChatsListService.has(this.uid);
		if (isMuted) {
			long deadline = mutedChatsListService.getDeadline(this.uid);

			if (deadline != DeadlineListService.DEADLINE_INDEFINITE) {
				double hours = ((double) deadline - System.currentTimeMillis()) / DateUtils.HOUR_IN_MILLIS;
				mutedIndex = findNextHigherMuteIndex(hours);

			} else {
				mutedIndex = MUTE_INDEX_INDEFINITE;
			}
		}
		updateUI();
	}

	abstract void notifySettingsChanged();

	protected void enablePlusMinus(boolean enable) {
		plusButton.setEnabled(enable);
		minusButton.setEnabled(enable);
	}

	@UiThread
	protected void updateUI() {
		boolean isMentionsOnly = mentionOnlyChatListService.has(this.uid);

		if (backupSoundCustom != null && !TestUtil.empty(RingtoneUtil.getRingtoneNameFromUri(this, backupSoundCustom))) {
			textSoundCustom.setText(RingtoneUtil.getRingtoneNameFromUri(this, backupSoundCustom));
		} else {
			textSoundCustom.setText("");
		}
		textSoundDefault.setText(RingtoneUtil.getRingtoneNameFromUri(this, defaultRingtone));

		enablePlusMinus(false);

		// DND
		if (isMuted || isMentionsOnly) {
			if (isMuted) {
				String deadlineString = "";
				long until = mutedChatsListService.getDeadline(this.uid);

				if (until != MUTE_INDEX_INDEFINITE) {
					deadlineString = "\n" + String.format(getString(R.string.notifications_until), DateUtils.formatDateTime(this, until,
						mutedIndex < 4 ?
							DateUtils.FORMAT_SHOW_TIME :
							DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_SHOW_TIME));
				}

				if (mutedIndex >= 0) {
					enablePlusMinus(true);
					radioSilentLimited.setChecked(true);
					if (mutedIndex >= 5) {
						radioSilentLimited.setText(getString(R.string.one_week) + deadlineString);
					} else {
						radioSilentLimited.setText(ConfigUtils.getSafeQuantityString(this, R.plurals.notifications_for_x_hours, muteValues[mutedIndex], muteValues[mutedIndex], muteValues[mutedIndex]) + deadlineString);
					}
				} else {
					radioSilentUnlimited.setChecked(true);
					radioSilentLimited.setText((ConfigUtils.getSafeQuantityString(this, R.plurals.notifications_for_x_hours, muteValues[0], muteValues[0]) + deadlineString));
				}
			} else {
				// mentions only
				radioSilentExceptMentions.setChecked(true);
			}
		} else {
			radioSilentOff.setChecked(true);
		}

		// SOUND
		if (ringtoneService.hasCustomRingtone(uid)) {
			if (selectedRingtone == null || selectedRingtone.toString() == null || selectedRingtone.toString().equals("null")) {
				// silent ringtone selected
				radioSoundNone.setChecked(true);
				textSoundCustom.setEnabled(true);
			} else {
				if (selectedRingtone.equals(defaultRingtone)) {
					radioSoundDefault.setChecked(true);
				} else {
					radioSoundCustom.setChecked(true);
					textSoundCustom.setEnabled(true);
					textSoundCustom.setText(RingtoneUtil.getRingtoneNameFromUri(this, selectedRingtone));
				}
			}
		} else {
			// default settings
			radioSoundDefault.setChecked(true);
		}
	}

	@Override
	public void onClick(View v) {
		final int id = v.getId();
		if (id == R.id.radio_sound_default) {
			ringtoneService.removeCustomRingtone(this.uid);
		} else if (id == R.id.radio_sound_custom || id == R.id.text_sound) {
			pickRingtone(this.uid);
		} else if (id == R.id.radio_sound_none) {
			ringtoneService.setRingtone(this.uid, null);
		} else if (id == R.id.radio_silent_off) {
			mutedChatsListService.remove(this.uid);
			mentionOnlyChatListService.remove(this.uid);
		} else if (id == R.id.radio_silent_unlimited) {
			mutedChatsListService.add(this.uid, DeadlineListService.DEADLINE_INDEFINITE);
			mentionOnlyChatListService.remove(this.uid);
		} else if (id == R.id.radio_silent_limited) {
			if (mutedIndex < 0) {
				mutedIndex = 0;
			}
			mutedChatsListService.add(this.uid, muteValues[mutedIndex] * DateUtils.HOUR_IN_MILLIS + System.currentTimeMillis());
			mentionOnlyChatListService.remove(this.uid);
		} else if (id == R.id.radio_silent_except_mentions) {
			mentionOnlyChatListService.add(uid, DeadlineListService.DEADLINE_INDEFINITE);
			mutedChatsListService.remove(uid);
		} else if (id == R.id.duration_plus) {
			mutedIndex = Math.min(mutedIndex + 1, muteValues.length - 1);
			mutedChatsListService.add(this.uid, muteValues[mutedIndex] * DateUtils.HOUR_IN_MILLIS + System.currentTimeMillis());
		} else if (id == R.id.duration_minus) {
			mutedIndex = Math.max(mutedIndex - 1, 0);
			mutedChatsListService.add(this.uid, muteValues[mutedIndex] * DateUtils.HOUR_IN_MILLIS + System.currentTimeMillis());
		} else if (id == R.id.prefs_button) {
			Intent intent = new Intent(this, SettingsActivity.class);
			intent.putExtra(SettingsActivity.EXTRA_SHOW_NOTIFICATION_FRAGMENT, true);
			ringtoneSettingsLauncher.launch(intent);
		}
		refreshSettings();
		notifySettingsChanged();
	}

	protected void setupButtons() {
		radioSoundDefault = this.findViewById(R.id.radio_sound_default);
		textSoundDefault = this.findViewById(R.id.text_sound_default);
		radioSoundCustom = this.findViewById(R.id.radio_sound_custom);
		radioSoundNone = this.findViewById(R.id.radio_sound_none);
		textSoundCustom = this.findViewById(R.id.text_sound);
		radioSilentOff = this.findViewById(R.id.radio_silent_off);
		radioSilentUnlimited = this.findViewById(R.id.radio_silent_unlimited);
		radioSilentLimited = this.findViewById(R.id.radio_silent_limited);
		radioSilentLimited = this.findViewById(R.id.radio_silent_limited);
		radioSilentLimited.setText(ConfigUtils.getSafeQuantityString(this, R.plurals.notifications_for_x_hours, muteValues[0], muteValues[0]));
		radioSilentExceptMentions = this.findViewById(R.id.radio_silent_except_mentions);
	}

	protected void pickRingtone(String uniqueId) {
		Uri existingUri = this.ringtoneService.getRingtoneFromUniqueId(uniqueId);
		if (existingUri != null && existingUri.getPath().equals("null")) {
			existingUri = null;
		}
		if (existingUri == null && backupSoundCustom != null) {
			existingUri = backupSoundCustom;
		}

		Uri defaultUri = this.ringtoneService.getDefaultContactRingtone();

		try {
			Intent intent = RingtoneUtil.getRingtonePickerIntent(RingtoneManager.TYPE_NOTIFICATION, existingUri == null ? defaultUri : existingUri, defaultUri);
			ringtonePickerLauncher.launch(intent);
		} catch (ActivityNotFoundException e) {
			RingtoneSelectorDialog.newInstance(getString(R.string.prefs_notification_sound),
				RingtoneManager.TYPE_NOTIFICATION,
				existingUri,
				defaultUri,
				true,
				true).show(getSupportFragmentManager(), DIALOG_TAG_RINGTONE_SELECTOR);
		}
	}

	protected void onDone() {
		AnimationUtil.circularObscure(parentLayout, animCenterLocation[0], animCenterLocation[1], false, new Runnable() {
			@Override
			public void run() {
				finish();
			}
		});
	}

	@Override
	protected boolean checkInstances() {
		return TestUtil.required(
				this.contactService,
				this.groupService,
				this.conversationService,
				this.ringtoneService,
				this.mutedChatsListService,
				this.mentionOnlyChatListService,
				this.preferenceService
		) && super.checkInstances();
	}

	@Override
	protected void instantiate() {
		super.instantiate();

		ServiceManager serviceManager = ThreemaApplication.getServiceManager();
		if (serviceManager != null) {
			try {
				this.contactService = serviceManager.getContactService();
				this.groupService = serviceManager.getGroupService();
				this.conversationService = serviceManager.getConversationService();
				this.ringtoneService = serviceManager.getRingtoneService();
				this.mutedChatsListService = serviceManager.getMutedChatsListService();
				this.mentionOnlyChatListService = serviceManager.getMentionOnlyChatsListService();
				this.preferenceService = serviceManager.getPreferenceService();
			} catch (Exception e) {
				LogUtil.exception(e, this);
			}
		}
	}

	@Override
	protected boolean enableOnBackPressedCallback() {
		return true;
	}

	@Override
	protected void handleOnBackPressed() {
		onDone();
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);

		outState.putIntArray(BUNDLE_ANIMATION_CENTER, this.animCenterLocation);
	}

	@Override
	public void onRingtoneSelected(String tag, Uri ringtone) {
		ringtoneService.setRingtone(uid, ringtone);
		backupSoundCustom = ringtone;
		refreshSettings();
	}

	@Override
	public void onCancel(String tag) {

	}

	@Override
	public void finish() {
		// used to avoid flickering of status and navigation bar when activity is closed
		super.finish();
		overridePendingTransition(R.anim.fast_fade_in, R.anim.fast_fade_out);
	}
}
