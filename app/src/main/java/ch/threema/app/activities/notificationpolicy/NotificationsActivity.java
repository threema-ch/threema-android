/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2015-2025 Threema GmbH
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

package ch.threema.app.activities.notificationpolicy;

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
import android.widget.RadioButton;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.core.app.NotificationChannelCompat;
import androidx.core.app.NotificationManagerCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.materialswitch.MaterialSwitch;

import org.slf4j.Logger;

import androidx.work.OneTimeWorkRequest;
import androidx.work.OutOfQuotaPolicy;
import androidx.work.WorkManager;
import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.activities.ThreemaActivity;
import ch.threema.app.dialogs.RingtoneSelectorDialog;
import ch.threema.app.dialogs.ShowOnceDialog;
import ch.threema.app.managers.ListenerManager;
import ch.threema.app.managers.ServiceManager;
import ch.threema.app.notifications.NotificationChannels;
import ch.threema.app.preference.SettingsActivity;
import ch.threema.app.services.ContactService;
import ch.threema.app.services.GroupService;
import ch.threema.app.services.PreferenceService;
import ch.threema.app.services.RingtoneService;
import ch.threema.app.services.ServicesConstants;
import ch.threema.app.utils.AnimationUtil;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.LogUtil;
import ch.threema.app.utils.RingtoneUtil;
import ch.threema.app.utils.TestUtil;
import ch.threema.app.workers.ShareTargetUpdateWorker;
import ch.threema.base.utils.LoggingUtil;

import static ch.threema.app.services.DeadlineListService.DEADLINE_INDEFINITE;
import static ch.threema.app.services.DeadlineListService.DEADLINE_INDEFINITE_EXCEPT_MENTIONS;

public abstract class NotificationsActivity extends ThreemaActivity implements View.OnClickListener, ShowOnceDialog.ShowOnceDialogClickListener, RingtoneSelectorDialog.RingtoneSelectorDialogClickListener {

    private static final Logger logger = LoggingUtil.getThreemaLogger("NotificationsActivity");

    private static final String BUNDLE_ANIMATION_CENTER = "animC";
    private static final String DIALOG_TAG_RINGTONE_SELECTOR = "drs";
    private static final String DIALOG_TAG_INDIVIDUAL_CONFIRM = "individual_confirm";
    protected static final int MUTE_INDEX_INDEFINITE = -1;
    protected TextView textSoundCustom, textSoundDefault;
    protected RadioButton
        radioSoundDefault,
        radioSilentOff,
        radioSilentUnlimited,
        radioSilentLimited,
        radioSilentExceptMentions,
        radioSoundCustom,
        radioSoundNone;
    private MaterialButton plusButton, minusButton;
    private ScrollView parentLayout;
    protected RingtoneService ringtoneService;
    protected ContactService contactService;
    protected GroupService groupService;
    protected PreferenceService preferenceService;
    protected MaterialSwitch notificationSettingsSwitch;
    protected TextView individualSettingsText;
    protected Uri defaultRingtone, selectedRingtone, backupSoundCustom;
    protected int mutedTimerIndex;
    protected static final int[] muteTimerValuesInHours = {1, 2, 4, 8, 24, 144};
    private int[] animCenterLocation = {0, 0};
    protected String uid, chatName;

    private final ActivityResultLauncher<Intent> ringtonePickerLauncher = registerForActivityResult(
        new ActivityResultContracts.StartActivityForResult(),
        result -> {
            if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                Uri uri = result.getData().getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI);
                onRingtoneSelected(DIALOG_TAG_RINGTONE_SELECTOR, uri);
            }
        }
    );

    private final ActivityResultLauncher<Intent> ringtoneSettingsLauncher = registerForActivityResult(
        new ActivityResultContracts.StartActivityForResult(),
        result -> refreshSettings()
    );

    private final ActivityResultLauncher<Intent> channelSettingsLauncher = registerForActivityResult(
        new ActivityResultContracts.StartActivityForResult(),
        result -> {
            refreshSettings();
            WorkManager.getInstance(NotificationsActivity.this).enqueue(
                new OneTimeWorkRequest.Builder(ShareTargetUpdateWorker.class)
                    .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                    .build()
            );
        }
    );

    @Override
    public void onCreate(Bundle savedInstanceState) {
        if (!this.requiredInstances()) {
            return;
        }

        super.onCreate(savedInstanceState);

        chatName = getIntent().getStringExtra(ThreemaApplication.INTENT_DATA_TEXT);

        supportRequestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_notifications);

        parentLayout = findViewById(R.id.parent_layout);
        loopViewGroup(parentLayout);
        ViewGroup topLayout = (ViewGroup) parentLayout.getParent();
        plusButton = findViewById(R.id.duration_plus);
        minusButton = findViewById(R.id.duration_minus);
        Button doneButton = findViewById(R.id.done_button);

        if (ConfigUtils.isWorkBuild()) {
            findViewById(R.id.work_life_warning).setVisibility(preferenceService.isAfterWorkDNDEnabled() ? View.VISIBLE : View.GONE);
        }

        if (ConfigUtils.supportsNotificationChannels()) {
            findViewById(R.id.individual_settings_container).setVisibility(View.VISIBLE);
            findViewById(R.id.notification_sound_container).setVisibility(View.GONE);
            notificationSettingsSwitch = findViewById(R.id.individual_settings_switch);
            individualSettingsText = findViewById(R.id.individual_settings_text);
            individualSettingsText.setOnClickListener(v -> {
                if (notificationSettingsSwitch.isChecked()) {
                    showIndividualSettings(chatName);
                }
            });

        } else {
            findViewById(R.id.individual_settings_container).setVisibility(View.GONE);
            findViewById(R.id.notification_sound_container).setVisibility(View.VISIBLE);
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

    private void showIndividualSettings(String chatName) {
        if (!ConfigUtils.supportsNotificationChannels()) {
            return;
        }
        boolean isGroupChat = this instanceof GroupNotificationsActivity;
        @NonNull Intent intent = NotificationChannels.INSTANCE.getPerConversationChannelSettingsIntent(this, chatName, uid, isGroupChat);
        try {
            channelSettingsLauncher.launch(intent);
        } catch (ActivityNotFoundException e) {
            logger.debug("No settings activity found");
        }
    }

    private void deleteIndividualSettings() {
        NotificationChannels.INSTANCE.deletePerConversationChannel(this, uid);
    }

    @Override
    public void onDestroy() {
        ListenerManager.contactSettingsListeners.handle(listener -> listener.onNotificationSettingChanged(uid));
        super.onDestroy();
    }

    private void loopViewGroup(@NonNull ViewGroup group) {
        for (int i = 0; i < group.getChildCount(); i++) {
            View v = group.getChildAt(i);
            if (v instanceof ViewGroup) {
                loopViewGroup((ViewGroup) v);
            } else {
                if (v instanceof RadioButton ||
                    v instanceof ImageView ||
                    v instanceof TextView
                ) {
                    v.setOnClickListener(this);
                }
            }
        }
    }

    private int findNextHigherMuteIndex(double hours) {
        for (int i = muteTimerValuesInHours.length - 1; i >= 0; i--) {
            if (muteTimerValuesInHours[i] < hours) {
                return i + 1;
            }
        }
        return 0;
    }

    protected void refreshSettings() {
        if (isMutedRightNow()) {
            final @Nullable Long muteOverrideUntilValue = getNotificationTriggerPolicyOverrideValue();
            if (muteOverrideUntilValue != null && muteOverrideUntilValue != DEADLINE_INDEFINITE && muteOverrideUntilValue != DEADLINE_INDEFINITE_EXCEPT_MENTIONS) {
                double hours = ((double) muteOverrideUntilValue - System.currentTimeMillis()) / DateUtils.HOUR_IN_MILLIS;
                mutedTimerIndex = findNextHigherMuteIndex(hours);
            } else {
                mutedTimerIndex = MUTE_INDEX_INDEFINITE;
            }
        }
        updateUI();
    }

    protected void enablePlusMinus(boolean enable) {
        plusButton.setEnabled(enable);
        minusButton.setEnabled(enable);
    }

    @UiThread
    protected void updateUI() {
        if (backupSoundCustom != null && !TestUtil.isEmptyOrNull(RingtoneUtil.getRingtoneNameFromUri(this, backupSoundCustom))) {
            textSoundCustom.setText(RingtoneUtil.getRingtoneNameFromUri(this, backupSoundCustom));
        } else {
            textSoundCustom.setText("");
        }
        textSoundDefault.setText(RingtoneUtil.getRingtoneNameFromUri(this, defaultRingtone));

        enablePlusMinus(false);

        // DND
        if (isMutedRightNow()) {
            if (!isMutedExceptMentions()) {
                final @Nullable Long muteOverrideUntilValue = getNotificationTriggerPolicyOverrideValue();

                String deadlineString = "";
                if (muteOverrideUntilValue != null && muteOverrideUntilValue != DEADLINE_INDEFINITE && muteOverrideUntilValue != DEADLINE_INDEFINITE_EXCEPT_MENTIONS) {
                    deadlineString = "\n" + String.format(
                        getString(R.string.notifications_until),
                        DateUtils.formatDateTime(this, muteOverrideUntilValue,
                            mutedTimerIndex < 4 ?
                                DateUtils.FORMAT_SHOW_TIME :
                                DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_SHOW_TIME
                        )
                    );
                }

                if (mutedTimerIndex >= 0) {
                    enablePlusMinus(true);
                    radioSilentLimited.setChecked(true);
                    if (mutedTimerIndex >= 5) {
                        radioSilentLimited.setText(getString(R.string.one_week) + deadlineString);
                    } else {
                        radioSilentLimited.setText(ConfigUtils.getSafeQuantityString(this, R.plurals.notifications_for_x_hours, muteTimerValuesInHours[mutedTimerIndex], muteTimerValuesInHours[mutedTimerIndex], muteTimerValuesInHours[mutedTimerIndex]) + deadlineString);
                    }
                } else {
                    radioSilentUnlimited.setChecked(true);
                    radioSilentLimited.setText((ConfigUtils.getSafeQuantityString(this, R.plurals.notifications_for_x_hours, muteTimerValuesInHours[0], muteTimerValuesInHours[0]) + deadlineString));
                }
            } else if (isMutedExceptMentions()) {
                // mentions only
                radioSilentExceptMentions.setChecked(true);
            }
        } else {
            radioSilentOff.setChecked(true);
        }

        // SOUND
        if (ringtoneService.hasCustomRingtone(uid)) {
            if (selectedRingtone == null || selectedRingtone.toString().equals(ServicesConstants.PREFERENCES_NULL)) {
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

        if (ConfigUtils.supportsNotificationChannels()) {
            NotificationManagerCompat notificationManagerCompat = NotificationManagerCompat.from(this);
            NotificationChannelCompat notificationChannelCompat = notificationManagerCompat.getNotificationChannelCompat(uid);

            notificationSettingsSwitch.setOnCheckedChangeListener(null);
            notificationSettingsSwitch.setChecked(notificationChannelCompat != null);
            individualSettingsText.setEnabled(notificationSettingsSwitch.isChecked());
            notificationSettingsSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) {
                    individualSettingsText.setEnabled(true);
                    if (ShowOnceDialog.shouldNotShowAnymore(DIALOG_TAG_INDIVIDUAL_CONFIRM)) {
                        onYes(DIALOG_TAG_INDIVIDUAL_CONFIRM);
                    } else {
                        ShowOnceDialog showOnceDialog = ShowOnceDialog.newInstance(R.string.individual_notification_settings, R.string.individual_notification_settings_warn);
                        showOnceDialog.show(getSupportFragmentManager(), DIALOG_TAG_INDIVIDUAL_CONFIRM);
                    }
                } else {
                    individualSettingsText.setEnabled(false);
                    deleteIndividualSettings();
                }
            });
        }
    }

    @Override
    public void onClick(@NonNull View view) {
        @IdRes final int clickedViewId = view.getId();
        if (clickedViewId == R.id.radio_sound_default) {
            ringtoneService.removeCustomRingtone(this.uid);
        } else if (clickedViewId == R.id.radio_sound_custom || clickedViewId == R.id.text_sound) {
            pickRingtone(this.uid);
        } else if (clickedViewId == R.id.radio_sound_none) {
            ringtoneService.setRingtone(this.uid, null);
        } else if (clickedViewId == R.id.radio_silent_off) {
            onSettingChanged(null);
        } else if (clickedViewId == R.id.radio_silent_unlimited) {
            onSettingChanged(DEADLINE_INDEFINITE);
        } else if (clickedViewId == R.id.radio_silent_limited) {
            if (mutedTimerIndex < 0) {
                mutedTimerIndex = 0;
            }
            final @NonNull Long muteUntilUtcMillis = muteTimerValuesInHours[mutedTimerIndex] * DateUtils.HOUR_IN_MILLIS + System.currentTimeMillis();
            onSettingChanged(muteUntilUtcMillis);
        } else if (clickedViewId == R.id.radio_silent_except_mentions) {
            onSettingChanged(DEADLINE_INDEFINITE_EXCEPT_MENTIONS);
        } else if (clickedViewId == R.id.duration_plus) {
            mutedTimerIndex = Math.min(mutedTimerIndex + 1, muteTimerValuesInHours.length - 1);
            final @NonNull Long muteUntilUtcMillis = muteTimerValuesInHours[mutedTimerIndex] * DateUtils.HOUR_IN_MILLIS + System.currentTimeMillis();
            onSettingChanged(muteUntilUtcMillis);
        } else if (clickedViewId == R.id.duration_minus) {
            mutedTimerIndex = Math.max(mutedTimerIndex - 1, 0);
            final @NonNull Long muteUntilUtcMillis = muteTimerValuesInHours[mutedTimerIndex] * DateUtils.HOUR_IN_MILLIS + System.currentTimeMillis();
            onSettingChanged(muteUntilUtcMillis);
        } else if (clickedViewId == R.id.prefs_button) {
            Intent intent = new Intent(this, SettingsActivity.class);
            intent.putExtra(SettingsActivity.EXTRA_SHOW_NOTIFICATION_FRAGMENT, true);
            ringtoneSettingsLauncher.launch(intent);
        }
        refreshSettings();
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
        radioSilentLimited.setText(ConfigUtils.getSafeQuantityString(this, R.plurals.notifications_for_x_hours, muteTimerValuesInHours[0], muteTimerValuesInHours[0]));
        radioSilentExceptMentions = this.findViewById(R.id.radio_silent_except_mentions);
    }

    protected void pickRingtone(String uniqueId) {
        Uri existingUri = this.ringtoneService.getRingtoneFromUniqueId(uniqueId);
        if (existingUri != null && existingUri.getPath() != null && existingUri.getPath().equals(ServicesConstants.PREFERENCES_NULL)) {
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
        AnimationUtil.circularObscure(parentLayout, animCenterLocation[0], animCenterLocation[1], false, this::finish);
    }

    @Override
    protected boolean checkInstances() {
        return TestUtil.required(
            this.contactService,
            this.groupService,
            this.ringtoneService,
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
                this.ringtoneService = serviceManager.getRingtoneService();
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
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putIntArray(BUNDLE_ANIMATION_CENTER, this.animCenterLocation);
    }

    @Override
    public void onRingtoneSelected(String tag, @NonNull Uri ringtone) {
        ringtoneService.setRingtone(uid, ringtone);
        backupSoundCustom = ringtone;
        refreshSettings();
    }

    @Override
    public void finish() {
        // used to avoid flickering of status and navigation bar when activity is closed
        super.finish();
        overridePendingTransition(R.anim.fast_fade_in, R.anim.fast_fade_out);
    }

    @Override
    public void onYes(String tag) {
        if (DIALOG_TAG_INDIVIDUAL_CONFIRM.equals(tag)) {
            individualSettingsText.setEnabled(true);
            showIndividualSettings(chatName);
        }
    }

    @Override
    public void onCancel(String tag) {
        if (DIALOG_TAG_INDIVIDUAL_CONFIRM.equals(tag)) {
            notificationSettingsSwitch.setChecked(false);
            individualSettingsText.setEnabled(false);
        }
    }

    protected abstract void onSettingChanged(@Nullable final Long mutedOverrideUntil);

    // Also respects current system time
    protected abstract boolean isMutedRightNow();

    protected abstract boolean isMutedExceptMentions();

    @Nullable
    protected abstract Long getNotificationTriggerPolicyOverrideValue();
}
