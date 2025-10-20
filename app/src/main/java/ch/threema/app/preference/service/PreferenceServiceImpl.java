/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2013-2025 Threema GmbH
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

package ch.threema.app.preference.service;

import android.content.Context;
import android.net.Uri;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;

import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import ch.threema.app.AppConstants;
import ch.threema.app.R;
import ch.threema.app.managers.ListenerManager;
import ch.threema.app.multidevice.MultiDeviceManager;
import ch.threema.app.services.ContactService;
import ch.threema.app.stores.EncryptedPreferenceStore;
import ch.threema.app.stores.PreferenceStore;
import ch.threema.app.threemasafe.ThreemaSafeMDMConfig;
import ch.threema.app.threemasafe.ThreemaSafeServerInfo;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.TestUtil;
import ch.threema.base.crypto.NonceFactory;
import ch.threema.base.utils.Base64;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.domain.protocol.api.work.WorkDirectoryCategory;
import ch.threema.domain.protocol.api.work.WorkOrganization;
import ch.threema.domain.taskmanager.TaskManager;

import static ch.threema.app.utils.AutoDeleteUtil.validateKeepMessageDays;

public class PreferenceServiceImpl implements PreferenceService {
    private static final Logger logger = LoggingUtil.getThreemaLogger("PreferenceServiceImpl");

    private static final String CONTACT_PHOTO_BLOB_ID = "id";
    private static final String CONTACT_PHOTO_ENCRYPTION_KEY = "key";
    private static final String CONTACT_PHOTO_SIZE = "size";

    @NonNull
    private final Context context;
    @NonNull
    private final PreferenceStore preferenceStore;
    @NonNull
    private final EncryptedPreferenceStore encryptedPreferenceStore;

    @NonNull
    private final ContactSyncPolicySetting contactSyncPolicySetting;
    @NonNull
    private final UnknownContactPolicySetting unknownContactPolicySetting;
    @NonNull
    private final ReadReceiptPolicySetting readReceiptPolicySetting;
    @NonNull
    private final TypingIndicatorPolicySetting typingIndicatorPolicySetting;
    @NonNull
    private final O2oCallPolicySetting o2oCallPolicySetting;
    @NonNull
    private final O2oCallConnectionPolicySetting o2oCallConnectionPolicySetting;
    @NonNull
    private final O2oCallVideoPolicySetting o2oCallVideoPolicySetting;
    @NonNull
    private final GroupCallPolicySetting groupCallPolicySetting;
    @NonNull
    private final ScreenshotPolicySetting screenshotPolicySetting;
    @NonNull
    private final KeyboardDataCollectionPolicySetting keyboardDataCollectionPolicySetting;
    @NonNull
    private final Map<String, SynchronizedBooleanSetting> booleanSettingsMap = new HashMap<>();

    public PreferenceServiceImpl(
        @NonNull Context context,
        @NonNull PreferenceStore preferenceStore,
        @NonNull EncryptedPreferenceStore encryptedPreferenceStore,
        @NonNull TaskManager taskManager,
        @NonNull MultiDeviceManager multiDeviceManager,
        @NonNull NonceFactory nonceFactory
    ) {
        this.context = context;
        this.preferenceStore = preferenceStore;
        this.encryptedPreferenceStore = encryptedPreferenceStore;

        this.contactSyncPolicySetting = new ContactSyncPolicySetting(
            this,
            multiDeviceManager,
            nonceFactory,
            taskManager,
            preferenceStore,
            context
        );
        this.unknownContactPolicySetting = new UnknownContactPolicySetting(
            this,
            multiDeviceManager,
            nonceFactory,
            taskManager,
            preferenceStore,
            context
        );
        this.readReceiptPolicySetting = new ReadReceiptPolicySetting(
            this,
            multiDeviceManager,
            nonceFactory,
            taskManager,
            preferenceStore,
            context
        );
        this.typingIndicatorPolicySetting = new TypingIndicatorPolicySetting(
            this,
            multiDeviceManager,
            nonceFactory,
            taskManager,
            preferenceStore,
            context
        );
        this.o2oCallPolicySetting = new O2oCallPolicySetting(
            this,
            multiDeviceManager,
            nonceFactory,
            taskManager,
            preferenceStore,
            context
        );
        this.o2oCallConnectionPolicySetting = new O2oCallConnectionPolicySetting(
            this,
            multiDeviceManager,
            nonceFactory,
            taskManager,
            preferenceStore,
            context
        );
        this.o2oCallVideoPolicySetting = new O2oCallVideoPolicySetting(
            this,
            multiDeviceManager,
            nonceFactory,
            taskManager,
            preferenceStore,
            context
        );
        this.groupCallPolicySetting = new GroupCallPolicySetting(
            this,
            multiDeviceManager,
            nonceFactory,
            taskManager,
            preferenceStore,
            context
        );
        this.screenshotPolicySetting = new ScreenshotPolicySetting(
            this,
            multiDeviceManager,
            nonceFactory,
            taskManager,
            preferenceStore,
            context
        );
        this.keyboardDataCollectionPolicySetting = new KeyboardDataCollectionPolicySetting(
            this,
            multiDeviceManager,
            nonceFactory,
            taskManager,
            preferenceStore,
            context
        );

        booleanSettingsMap.put(contactSyncPolicySetting.preferenceKey, contactSyncPolicySetting);
        booleanSettingsMap.put(unknownContactPolicySetting.preferenceKey, unknownContactPolicySetting);
        booleanSettingsMap.put(readReceiptPolicySetting.preferenceKey, readReceiptPolicySetting);
        booleanSettingsMap.put(typingIndicatorPolicySetting.preferenceKey, typingIndicatorPolicySetting);
        booleanSettingsMap.put(o2oCallPolicySetting.preferenceKey, o2oCallPolicySetting);
        booleanSettingsMap.put(o2oCallConnectionPolicySetting.preferenceKey, o2oCallConnectionPolicySetting);
        booleanSettingsMap.put(o2oCallVideoPolicySetting.preferenceKey, o2oCallVideoPolicySetting);
        booleanSettingsMap.put(groupCallPolicySetting.preferenceKey, groupCallPolicySetting);
        booleanSettingsMap.put(screenshotPolicySetting.preferenceKey, screenshotPolicySetting);
        booleanSettingsMap.put(keyboardDataCollectionPolicySetting.preferenceKey, keyboardDataCollectionPolicySetting);
    }

    @Override
    public boolean isSyncContacts() {
        return contactSyncPolicySetting.get();
    }

    @NonNull
    @Override
    public ContactSyncPolicySetting getContactSyncPolicySetting() {
        return contactSyncPolicySetting;
    }

    @Override
    public boolean isBlockUnknown() {
        return unknownContactPolicySetting.get();
    }

    @NonNull
    @Override
    public UnknownContactPolicySetting getUnknownContactPolicySetting() {
        return unknownContactPolicySetting;
    }

    @Override
    public boolean areReadReceiptsEnabled() {
        return readReceiptPolicySetting.get();
    }

    @NonNull
    @Override
    public ReadReceiptPolicySetting getReadReceiptPolicySetting() {
        return readReceiptPolicySetting;
    }

    @Override
    public boolean isTypingIndicatorEnabled() {
        return typingIndicatorPolicySetting.get();
    }

    @NonNull
    @Override
    public TypingIndicatorPolicySetting getTypingIndicatorPolicySetting() {
        return typingIndicatorPolicySetting;
    }

    @Override
    public boolean isVoipEnabled() {
        return o2oCallPolicySetting.get();
    }

    @NonNull
    @Override
    public O2oCallPolicySetting getO2oCallPolicySetting() {
        return o2oCallPolicySetting;
    }

    @Override
    public boolean getForceTURN() {
        return o2oCallConnectionPolicySetting.get();
    }

    @NonNull
    @Override
    public O2oCallConnectionPolicySetting getO2oCallConnectionPolicySetting() {
        return o2oCallConnectionPolicySetting;
    }

    @Override
    public boolean areVideoCallsEnabled() {
        return o2oCallVideoPolicySetting.get();
    }

    @NonNull
    @Override
    public O2oCallVideoPolicySetting getO2oCallVideoPolicySetting() {
        return o2oCallVideoPolicySetting;
    }

    @Override
    public boolean areGroupCallsEnabled() {
        return groupCallPolicySetting.get();
    }

    @NonNull
    @Override
    public GroupCallPolicySetting getGroupCallPolicySetting() {
        return groupCallPolicySetting;
    }

    @Override
    public boolean areScreenshotsDisabled() {
        return screenshotPolicySetting.get();
    }

    @NonNull
    @Override
    public ScreenshotPolicySetting getScreenshotPolicySetting() {
        return screenshotPolicySetting;
    }

    @Override
    public boolean isIncognitoKeyboardRequested() {
        return keyboardDataCollectionPolicySetting.get();
    }

    @NonNull
    @Override
    public KeyboardDataCollectionPolicySetting getKeyboardDataCollectionPolicySetting() {
        return keyboardDataCollectionPolicySetting;
    }

    @Override
    public boolean isCustomWallpaperEnabled() {
        return this.preferenceStore.getBoolean(this.getKeyName(R.string.preferences__wallpaper_switch));
    }

    @Override
    public void setCustomWallpaperEnabled(boolean enabled) {
        this.preferenceStore.save(this.getKeyName(R.string.preferences__wallpaper_switch), enabled);
    }

    @Override
    public boolean isEnterToSend() {
        return this.preferenceStore.getBoolean(this.getKeyName(R.string.preferences__enter_to_send));
    }

    @Override
    public boolean isInAppSounds() {
        return this.preferenceStore.getBoolean(this.getKeyName(R.string.preferences__inapp_sounds));
    }

    @Override
    public boolean isInAppVibrate() {
        return this.preferenceStore.getBoolean(this.getKeyName(R.string.preferences__inapp_vibrate));
    }

    @Override
    @ImageScale
    public int getImageScale() {
        String imageScale = this.preferenceStore.getString(this.getKeyName(R.string.preferences__image_size));
        if (imageScale == null || imageScale.isEmpty()) {
            return ImageScale_MEDIUM;
        }

        switch (Integer.parseInt(imageScale)) {
            case 0:
                return ImageScale_SMALL;
            case 2:
                return ImageScale_LARGE;
            case 3:
                return ImageScale_XLARGE;
            case 4:
                return ImageScale_ORIGINAL;
            default:
                return ImageScale_MEDIUM;
        }
    }

    @Override
    public int getVideoSize() {
        String videoSize = this.preferenceStore.getString(this.getKeyName(R.string.preferences__video_size));
        if (videoSize == null || videoSize.isEmpty()) {
            // return a default value
            return VideoSize_MEDIUM;
        }

        switch (Integer.parseInt(videoSize)) {
            case 0:
                return VideoSize_SMALL;
            case 2:
                return VideoSize_ORIGINAL;
            default:
                return VideoSize_MEDIUM;
        }
    }

    @Override
    public String getSerialNumber() {
        return this.preferenceStore.getString(this.getKeyName(R.string.preferences__serial_number));
    }

    @Override
    public void setSerialNumber(String serialNumber) {
        this.preferenceStore.save(this.getKeyName(R.string.preferences__serial_number), serialNumber);
    }

    @Override
    @Nullable
    public synchronized String getLicenseUsername() {
        return getEncryptedStringCompat(getKeyName(R.string.preferences__license_username));
    }

    @Override
    public void setLicenseUsername(String username) {
        encryptedPreferenceStore.save(getKeyName(R.string.preferences__license_username), username);
    }

    @Override
    public synchronized String getLicensePassword() {
        return getEncryptedStringCompat(getKeyName(R.string.preferences__license_password));
    }

    @Override
    public void setLicensePassword(String password) {
        encryptedPreferenceStore.save(getKeyName(R.string.preferences__license_password), password);
    }

    @Override
    @Nullable
    public synchronized String getOnPremServer() {
        return getEncryptedStringCompat(getKeyName(R.string.preferences__onprem_server));
    }

    /**
     * Get encrypted string preferences in a backwards compatible way.
     * If no encrypted prefs with given key exist, the current (unencrypted) value will be migrated
     *
     * @param key Key of preference
     * @return Value of preference or null if neither an encrypted nor an unencrypted value is found
     */
    private String getEncryptedStringCompat(@NonNull String key) {
        var value = encryptedPreferenceStore.getString(key);
        if (value != null && !value.isEmpty()) {
            return value;
        }
        var legacyValue = preferenceStore.getString(key);
        if (legacyValue != null) {
            encryptedPreferenceStore.save(key, legacyValue);
            preferenceStore.remove(key);
        }
        return legacyValue;
    }

    @Override
    public void setOnPremServer(String server) {
        encryptedPreferenceStore.save(getKeyName(R.string.preferences__onprem_server), server);
    }

    @Override
    @Deprecated
    public LinkedList<Integer> getRecentEmojis() {
        LinkedList<Integer> list = new LinkedList<>();
        JSONArray array = preferenceStore.getJSONArray(getKeyName(R.string.preferences__recent_emojis));
        for (int i = 0; i < array.length(); i++) {
            try {
                list.add(array.getInt(i));
            } catch (JSONException e) {
                logger.error("JSONException", e);
            }
        }
        return list;
    }

    @Override
    public LinkedList<String> getRecentEmojis2() {
        String[] theArray = this.preferenceStore.getStringArray(this.getKeyName(R.string.preferences__recent_emojis2));

        if (theArray != null) {
            return new LinkedList<>(Arrays.asList(theArray));
        } else {
            return new LinkedList<>(new LinkedList<>());
        }
    }

    @Override
    @Deprecated
    public void setRecentEmojis(LinkedList<Integer> list) {
        JSONArray array = new JSONArray(list);
        this.preferenceStore.save(this.getKeyName(R.string.preferences__recent_emojis), array);
    }

    @Override
    public void setRecentEmojis2(LinkedList<String> list) {
        preferenceStore.save(getKeyName(R.string.preferences__recent_emojis2), list.toArray(new String[0]));
    }

    @Override
    public int getEmojiSearchIndexVersion() {
        Integer version = this.preferenceStore.getInt(this.getKeyName(R.string.preferences__emoji_search_index_version));
        return version != null ? version : -1;
    }

    @Override
    public void setEmojiSearchIndexVersion(int version) {
        this.preferenceStore.save(this.getKeyName(R.string.preferences__emoji_search_index_version), version);
    }

    @Override
    public boolean useThreemaPush() {
        return this.preferenceStore.getBoolean(this.getKeyName(R.string.preferences__threema_push_switch));
    }

    @Override
    public void setUseThreemaPush(boolean value) {
        this.preferenceStore.save(this.getKeyName(R.string.preferences__threema_push_switch), value);
    }

    @Override
    public boolean isSaveMedia() {
        return this.preferenceStore.getBoolean(this.getKeyName(R.string.preferences__save_media));
    }

    @Override
    public boolean isPinSet() {
        return isPinCodeValid(encryptedPreferenceStore.getString(getKeyName(R.string.preferences__pin_lock_code)));
    }

    @Override
    public boolean setPin(String newCode) {
        if (isPinCodeValid(newCode)) {
            encryptedPreferenceStore.save(getKeyName(R.string.preferences__pin_lock_code), newCode);
            return true;
        } else {
            this.preferenceStore.remove(this.getKeyName(R.string.preferences__pin_lock_code));
        }
        return false;
    }

    @Override
    public boolean isPinCodeCorrect(String code) {
        String storedCode = encryptedPreferenceStore.getString(getKeyName(R.string.preferences__pin_lock_code));

        // use MessageDigest for a timing-safe comparison
        return
            code != null &&
                storedCode != null &&
                MessageDigest.isEqual(storedCode.getBytes(), code.getBytes());
    }

    private boolean isPinCodeValid(String code) {
        if (TestUtil.isEmptyOrNull(code))
            return false;
        else
            return (code.length() >= AppConstants.MIN_PIN_LENGTH &&
                code.length() <= AppConstants.MAX_PIN_LENGTH &&
                TextUtils.isDigitsOnly(code));
    }

    @Override
    public int getPinLockGraceTime() {
        String pos = this.preferenceStore.getString(this.getKeyName(R.string.preferences__pin_lock_grace_time));
        try {
            int time = Integer.parseInt(pos);
            if (time >= 30 || time < 0) {
                return time;
            }
        } catch (NumberFormatException x) {
            // ignored
        }
        return -1;
    }

    @Override
    public int getIDBackupCount() {
        return preferenceStore.getInt(getKeyName(R.string.preferences__id_backup_count));
    }

    @Override
    public void incrementIDBackupCount() {
        this.preferenceStore.save(
            this.getKeyName(R.string.preferences__id_backup_count),
            this.getIDBackupCount() + 1);
    }

    @Override
    public void resetIDBackupCount() {
        this.preferenceStore.save(
            this.getKeyName(R.string.preferences__id_backup_count),
            0);
    }

    @Override
    public void setLastIDBackupReminderTimestamp(@Nullable Instant lastIDBackupReminderTimestamp) {
        this.preferenceStore.save(
            this.getKeyName(R.string.preferences__last_id_backup_date),
            lastIDBackupReminderTimestamp
        );
    }

    @Override
    public String getContactListSorting() {
        String sorting = this.preferenceStore.getString(this.getKeyName(R.string.preferences__contact_sorting));

        if (sorting == null || sorting.isEmpty()) {
            //set last_name - first_name as default
            sorting = this.context.getString(R.string.contact_sorting__last_name);
            this.preferenceStore.save(this.getKeyName(R.string.preferences__contact_sorting), sorting);
        }

        return sorting;
    }

    @Override
    public boolean isContactListSortingFirstName() {
        return TestUtil.compare(this.getContactListSorting(), this.context.getString(R.string.contact_sorting__first_name));
    }

    @Override
    public String getContactFormat() {
        String format = this.preferenceStore.getString(this.getKeyName(R.string.preferences__contact_format));

        if (format == null || format.isEmpty()) {
            //set firstname lastname as default
            format = this.context.getString(R.string.contact_format__first_name_last_name);
            this.preferenceStore.save(this.getKeyName(R.string.preferences__contact_format), format);
        }

        return format;
    }

    @Override
    public boolean isContactFormatFirstNameLastName() {
        return TestUtil.compare(this.getContactFormat(), this.context.getString(R.string.contact_format__first_name_last_name));
    }

    @Override
    public boolean isDefaultContactPictureColored() {
        return this.preferenceStore.getBoolean(this.getKeyName(R.string.preferences__default_contact_picture_colored));
    }

    @Override
    public int getFontStyle() {
        String fontStyle = this.preferenceStore.getString(this.getKeyName(R.string.preferences__fontstyle));
        if (TestUtil.isEmptyOrNull(fontStyle)) {
            // return a default value
            return R.style.FontStyle_Normal;
        }

        switch (Integer.parseInt(fontStyle)) {
            case 1:
                return R.style.FontStyle_Large;
            case 2:
                return R.style.FontStyle_XLarge;
            default:
                return R.style.FontStyle_Normal;
        }
    }

    @Override
    @Nullable
    public Instant getLastIDBackupReminderTimestamp() {
        return this.preferenceStore.getInstant(this.getKeyName(R.string.preferences__last_id_backup_date));
    }

    @Override
    public long getTransmittedFeatureMask() {
        // TODO(ANDR-2703): Remove this migration code
        // Delete old feature level (int) and move it to the feature mask value (long)
        String featureLevelKey = getKeyName(R.string.preferences__transmitted_feature_level);
        if (preferenceStore.containsKey(featureLevelKey)) {
            // Store feature mask as long
            setTransmittedFeatureMask(preferenceStore.getInt(featureLevelKey));

            // Remove transmitted feature level
            preferenceStore.remove(featureLevelKey);
        }

        return this.preferenceStore.getLong(this.getKeyName(R.string.preferences__transmitted_feature_mask));
    }

    @Override
    public void setTransmittedFeatureMask(long transmittedFeatureMask) {
        this.preferenceStore.save(this.getKeyName(R.string.preferences__transmitted_feature_mask), transmittedFeatureMask);
    }

    @Override
    public long getLastFeatureMaskTransmission() {
        return this.preferenceStore.getLong(this.getKeyName(R.string.preferences__last_feature_mask_transmission));
    }

    @Override
    public void setLastFeatureMaskTransmission(long timestamp) {
        this.preferenceStore.save(this.getKeyName(R.string.preferences__last_feature_mask_transmission), timestamp);
    }

    @Override
    @NonNull
    public String[] getList(String listName) {
        return getList(listName, true);
    }

    @Override
    @NonNull
    public String[] getList(String listName, boolean encrypted) {
        String[] res;
        if (encrypted) {
            res = encryptedPreferenceStore.getStringArray(listName);
        } else {
            res = preferenceStore.getStringArray(listName);
        }
        if (res == null && encrypted) {
            // check if we have an old unencrypted identity list - migrate if necessary and return its values
            if (this.preferenceStore.containsKey(listName)) {
                res = this.preferenceStore.getStringArray(listName);
                this.preferenceStore.remove(listName);
                if (res != null) {
                    encryptedPreferenceStore.save(listName, res);
                }
            }
        }
        return res != null ? res : new String[0];
    }

    @Override
    public void setList(String listName, String[] elements) {
        encryptedPreferenceStore.save(listName, elements);
    }

    @Override
    public void setListQuietly(String listName, String[] elements) {
        setListQuietly(listName, elements, true);
    }

    @Override
    public void setListQuietly(@NonNull String listName, @NonNull String[] elements, boolean encrypted) {
        if (encrypted) {
            encryptedPreferenceStore.saveQuietly(listName, elements);
        } else {
            preferenceStore.saveQuietly(listName, elements);
        }
    }

    @Override
    public Map<String, String> getStringMap(String listName) {
        return preferenceStore.getMap(listName);
    }

    @Override
    public void setStringMap(String listName, Map<String, String> map) {
        preferenceStore.save(listName, map);
    }

    @Override
    public void clear() {
        preferenceStore.clear();
        encryptedPreferenceStore.clear();
    }

    private String getKeyName(@StringRes int resourceId) {
        return this.context.getString(resourceId);
    }

    @Override
    public boolean showInactiveContacts() {
        return this.preferenceStore.getBoolean(this.getKeyName(R.string.preferences__show_inactive_contacts));
    }

    @Override
    public boolean getLastOnlineStatus() {
        return this.preferenceStore.getBoolean(this.getKeyName(R.string.preferences__last_online_status));

    }

    @Override
    public void setLastOnlineStatus(boolean online) {
        this.preferenceStore.save(this.getKeyName(R.string.preferences__last_online_status), online);
    }

    @Override
    public boolean isLatestVersion(Context context) {
        int buildNumber = ConfigUtils.getBuildNumber(context);
        if (buildNumber != 0) {
            return this.preferenceStore.getInt(this.getKeyName(R.string.preferences__latest_version)) >= buildNumber;
        }
        return false;
    }

    @Override
    public int getLatestVersion() {
        return this.preferenceStore.getInt(this.getKeyName(R.string.preferences__latest_version));
    }

    @Override
    public void setLatestVersion(Context context) {
        int buildNumber = ConfigUtils.getBuildNumber(context);
        if (buildNumber != 0) {
            this.preferenceStore.save(this.getKeyName(R.string.preferences__latest_version), buildNumber);
        }
    }

    @Override
    public boolean checkForAppUpdate(@NonNull Context context) {
        // Get the current build number
        int buildNumber = ConfigUtils.getBuildNumber(context);
        if (buildNumber == 0) {
            logger.error("Could not check for app update because build number is 0");
            return false;
        }

        // Get the last stored build number
        Integer latestStoredBuildNumber = this.preferenceStore.getInt(this.getKeyName(R.string.preferences__build_version));
        int lastCheckedBuildNumber = 0;
        if (latestStoredBuildNumber != null) {
            lastCheckedBuildNumber = latestStoredBuildNumber;
        }

        // Update the stored build number if a newer version is installed and return true
        if (lastCheckedBuildNumber < buildNumber) {
            this.preferenceStore.save(this.getKeyName(R.string.preferences__build_version), buildNumber);
            return true;
        }

        // The app has not been updated since the last check
        return false;
    }

    @Override
    public boolean getFileSendInfoShown() {
        return this.preferenceStore.getBoolean(this.getKeyName(R.string.preferences__filesend_info_shown));
    }

    @Override
    public void setFileSendInfoShown(boolean shown) {
        this.preferenceStore.save(this.getKeyName(R.string.preferences__filesend_info_shown), shown);
    }

    @Override
    @EmojiStyle
    public int getEmojiStyle() {
        final @Nullable String emojiStyleString = this.preferenceStore.getString(
            this.getKeyName(R.string.preferences__emoji_style)
        );
        if (emojiStyleString != null && !emojiStyleString.isEmpty()) {
            try {
                final int emojiStyle = Integer.parseInt(emojiStyleString);
                if (emojiStyle == 1) {
                    return EmojiStyle_ANDROID;
                }
            } catch (NumberFormatException exception) {
                logger.error("Failed to parse emoji style setting with saved value of: {}", emojiStyleString, exception);
            }
        }
        return EmojiStyle_DEFAULT;
    }

    @Override
    public void setLockoutDeadline(@Nullable Instant deadline) {
        this.preferenceStore.save(this.getKeyName(R.string.preferences__lockout_deadline), deadline);
    }

    @Override
    @Nullable
    public Instant getLockoutDeadline() {
        return this.preferenceStore.getInstant(this.getKeyName(R.string.preferences__lockout_deadline));
    }

    @Override
    public void setLockoutAttempts(int numWrongConfirmAttempts) {
        this.preferenceStore.save(this.getKeyName(R.string.preferences__lockout_attempts), numWrongConfirmAttempts);

    }

    @Override
    public int getLockoutAttempts() {
        return this.preferenceStore.getInt(this.getKeyName(R.string.preferences__lockout_attempts));
    }

    @Override
    public boolean isAnimationAutoplay() {
        return this.preferenceStore.getBoolean(this.getKeyName(R.string.preferences__gif_autoplay));
    }

    @Override
    public boolean isUseProximitySensor() {
        return this.preferenceStore.getBoolean(this.getKeyName(R.string.preferences__proximity_sensor));
    }

    @Override
    public void setAppLogoExpiresAt(@Nullable Instant expiresAt, @NonNull @ConfigUtils.AppThemeSetting String theme) {
        this.preferenceStore.save(this.getKeyName(
            ConfigUtils.THEME_DARK.equals(theme) ?
                R.string.preferences__app_logo_dark_expires_at :
                R.string.preferences__app_logo_light_expires_at), expiresAt);
    }

    @Override
    @Nullable
    public Instant getAppLogoExpiresAt(@NonNull @ConfigUtils.AppThemeSetting String theme) {
        return this.preferenceStore.getInstant(this.getKeyName(
            ConfigUtils.THEME_DARK.equals(theme) ?
                R.string.preferences__app_logo_dark_expires_at :
                R.string.preferences__app_logo_light_expires_at));
    }

    @Override
    public boolean isPrivateChatsHidden() {
        return this.preferenceStore.getBoolean(this.getKeyName(R.string.preferences__chats_hidden));
    }

    @Override
    public void setPrivateChatsHidden(boolean hidden) {
        this.preferenceStore.save(this.getKeyName(R.string.preferences__chats_hidden), hidden);
    }

    @Override
    public String getLockMechanism() {
        String mech = this.preferenceStore.getString(this.getKeyName(R.string.preferences__lock_mechanism));
        return mech == null ? LockingMech_NONE : mech;
    }

    @Override
    public boolean isAppLockEnabled() {
        return preferenceStore.getBoolean(this.getKeyName(R.string.preferences__app_lock_enabled)) && !PreferenceService.LockingMech_NONE.equals(getLockMechanism());
    }

    @Override
    public void setAppLockEnabled(boolean enabled) {
        this.preferenceStore.save(this.getKeyName(R.string.preferences__app_lock_enabled), !PreferenceService.LockingMech_NONE.equals(getLockMechanism()) && enabled);
    }

    @Override
    public void setLockMechanism(String lockingMech) {
        this.preferenceStore.save(this.getKeyName(R.string.preferences__lock_mechanism), lockingMech);
    }

    @Override
    public boolean isShowImageAttachPreviewsEnabled() {
        return this.preferenceStore.getBoolean(this.getKeyName(R.string.preferences__image_attach_previews));
    }

    @Override
    public boolean isDirectShare() {
        return this.preferenceStore.getBoolean(this.getKeyName(R.string.preferences__direct_share));
    }

    @Override
    public void setMessageDrafts(Map<String, String> messageDrafts) {
        encryptedPreferenceStore.save(getKeyName(R.string.preferences__message_drafts), messageDrafts);
    }

    @Override
    public Map<String, String> getMessageDrafts() {
        return encryptedPreferenceStore.getMap(getKeyName(R.string.preferences__message_drafts));
    }

    @Override
    public void setQuoteDrafts(Map<String, String> quoteDrafts) {
        encryptedPreferenceStore.save(getKeyName(R.string.preferences__quote_drafts), quoteDrafts);
    }

    @Override
    public Map<String, String> getQuoteDrafts() {
        return encryptedPreferenceStore.getMap(getKeyName(R.string.preferences__quote_drafts));
    }

    private @NonNull
    String getAppLogoKey(@ConfigUtils.AppThemeSetting String theme) {
        if (ConfigUtils.THEME_DARK.equals(theme)) {
            return this.getKeyName(R.string.preferences__app_logo_dark_url);
        }
        return this.getKeyName(R.string.preferences__app_logo_light_url);
    }

    @Override
    public void setAppLogo(@NonNull String url, @ConfigUtils.AppThemeSetting String theme) {
        encryptedPreferenceStore.save(getAppLogoKey(theme), url);
    }

    @Override
    public void clearAppLogo(@ConfigUtils.AppThemeSetting String theme) {
        this.preferenceStore.remove(this.getAppLogoKey(theme));
    }

    @Override
    public void clearAppLogos() {
        this.clearAppLogo(ConfigUtils.THEME_DARK);
        this.clearAppLogo(ConfigUtils.THEME_LIGHT);
    }

    @Override
    @Nullable
    public String getAppLogo(@ConfigUtils.AppThemeSetting String theme) {
        return encryptedPreferenceStore.getString(this.getAppLogoKey(theme));
    }

    @Override
    public void setCustomSupportUrl(String supportUrl) {
        encryptedPreferenceStore.save(getKeyName(R.string.preferences__custom_support_url), supportUrl);
    }

    @Override
    public String getCustomSupportUrl() {
        return encryptedPreferenceStore.getString(getKeyName(R.string.preferences__custom_support_url));
    }

    @Override
    public Map<String, String> getDiverseEmojiPrefs() {
        return preferenceStore.getMap(getKeyName(R.string.preferences__diverse_emojis));
    }

    @Override
    public void setDiverseEmojiPrefs(Map<String, String> diverseEmojis) {
        preferenceStore.save(getKeyName(R.string.preferences__diverse_emojis), diverseEmojis);
    }

    @Override
    public boolean isWebClientEnabled() {
        return this.preferenceStore.getBoolean(this.getKeyName(R.string.preferences__web_client_enabled));
    }

    @Override
    public void setWebClientEnabled(boolean enabled) {
        this.preferenceStore.save(this.getKeyName(R.string.preferences__web_client_enabled), enabled);
    }

    @Override
    public void setPushToken(String fcmToken) {
        encryptedPreferenceStore.save(getKeyName(R.string.preferences__push_token), fcmToken);
    }

    @Override
    public String getPushToken() {
        return encryptedPreferenceStore.getString(getKeyName(R.string.preferences__push_token));
    }

    @Override
    public int getProfilePicRelease() {
        return this.preferenceStore.getInt(this.getKeyName(R.string.preferences__profile_pic_release));
    }

    @Override
    public void setProfilePicRelease(int value) {
        this.preferenceStore.save(this.getKeyName(R.string.preferences__profile_pic_release), value);
    }

    @Override
    @Nullable
    public Instant getProfilePicUploadTimestamp() {
        return this.preferenceStore.getInstant(this.getKeyName(R.string.preferences__profile_pic_upload_date));
    }

    @Override
    public void setProfilePicUploadData(@Nullable ContactService.ProfilePictureUploadData result) {
        JSONObject toStore = null;

        if (result != null) {
            toStore = new JSONObject();
            try {
                toStore.put(CONTACT_PHOTO_BLOB_ID, Base64.encodeBytes(result.blobId));
                toStore.put(CONTACT_PHOTO_ENCRYPTION_KEY, Base64.encodeBytes(result.encryptionKey));
                toStore.put(CONTACT_PHOTO_SIZE, result.size);
            } catch (Exception e) {
                logger.error("Exception", e);
            }
        }

        if (toStore != null) {
            encryptedPreferenceStore.save(getKeyName(R.string.preferences__profile_pic_upload_data), toStore);
        } else {
            // calling the listeners here might not be needed anymore, but we do it to be on the safe side
            ListenerManager.preferenceListeners.handle(
                listener -> listener.onChanged(getKeyName(R.string.preferences__profile_pic_upload_data), null)
            );
        }

        var uploadDate = result != null ? Instant.ofEpochMilli(result.uploadedAt) : null;
        this.preferenceStore.save(this.getKeyName(R.string.preferences__profile_pic_upload_date), uploadDate);
    }

    @Override
    @Nullable
    public ContactService.ProfilePictureUploadData getProfilePicUploadData() {
        JSONObject fromStore = encryptedPreferenceStore.getJSONObject(getKeyName(R.string.preferences__profile_pic_upload_data));
        if (fromStore != null) {
            try {
                ContactService.ProfilePictureUploadData data = new ContactService.ProfilePictureUploadData();
                data.blobId = Base64.decode(fromStore.getString(CONTACT_PHOTO_BLOB_ID));
                data.encryptionKey = Base64.decode(fromStore.getString(CONTACT_PHOTO_ENCRYPTION_KEY));
                data.size = fromStore.getInt(CONTACT_PHOTO_SIZE);
                return data;
            } catch (Exception e) {
                logger.error("Exception", e);
                return null;
            }
        }
        return null;
    }

    @Override
    public boolean getProfilePicReceive() {
        return this.preferenceStore.getBoolean(this.getKeyName(R.string.preferences__receive_profilepics));
    }

    @Override
    @NonNull
    public String getAECMode() {
        String mode = this.preferenceStore.getString(this.getKeyName(R.string.preferences__voip_echocancel));
        if ("sw".equals(mode)) {
            return mode;
        }
        return "hw";
    }

    @Override
    public @NonNull
    String getVideoCodec() {
        String mode = this.preferenceStore.getString(this.getKeyName(R.string.preferences__voip_video_codec));
        if (mode != null) {
            return mode;
        }
        return PreferenceService.VIDEO_CODEC_HW;
    }

    @Override
    public boolean isRejectMobileCalls() {
        return this.preferenceStore.getBoolean(this.getKeyName(R.string.preferences__voip_reject_mobile_calls));
    }

    @Override
    public void setRejectMobileCalls(boolean value) {
        this.preferenceStore.save(this.getKeyName(R.string.preferences__voip_reject_mobile_calls), value);
    }

    @Override
    public boolean isIpv6Preferred() {
        return this.preferenceStore.getBoolean(this.getKeyName(R.string.preferences__ipv6_preferred));
    }

    @Override
    public boolean allowWebrtcIpv6() {
        return this.preferenceStore.getBoolean(this.getKeyName(R.string.preferences__ipv6_webrtc_allowed));
    }

    @Override
    public Set<String> getMobileAutoDownload() {
        var stringSet = preferenceStore.getStringSet(getKeyName(R.string.preferences__auto_download_mobile));
        return stringSet != null
            ? stringSet
            : new HashSet<>(Arrays.stream(context.getResources().getStringArray(R.array.list_auto_download_mobile_default)).toList());
    }

    @Override
    public Set<String> getWifiAutoDownload() {
        var stringSet = preferenceStore.getStringSet(getKeyName(R.string.preferences__auto_download_wifi));
        return stringSet != null
            ? stringSet
            : new HashSet<>(Arrays.stream(context.getResources().getStringArray(R.array.list_auto_download_wifi_default)).toList());
    }

    @Override
    public void setRatingReference(String reference) {
        encryptedPreferenceStore.save(getKeyName(R.string.preferences__rate_ref), reference);
    }

    @Override
    @Nullable
    public String getRatingReference() {
        return encryptedPreferenceStore.getString(getKeyName(R.string.preferences__rate_ref));
    }

    @Override
    public void setRatingReviewText(String review) {
        encryptedPreferenceStore.save(getKeyName(R.string.preferences__rate_text), review);
    }

    @Override
    public String getRatingReviewText() {
        return encryptedPreferenceStore.getString(getKeyName(R.string.preferences__rate_text));
    }

    @Override
    public void setPrivacyPolicyAccepted(@Nullable Instant timestamp, int source) {
        this.preferenceStore.save(this.getKeyName(R.string.preferences__privacy_policy_accept_date), timestamp);
        this.preferenceStore.save(this.getKeyName(R.string.preferences__privacy_policy_accept_source), source);
    }

    @Override
    @Nullable
    public Instant getPrivacyPolicyAccepted() {
        if (this.preferenceStore.getInt(this.getKeyName(R.string.preferences__privacy_policy_accept_source)) != PRIVACY_POLICY_ACCEPT_NONE) {
            return this.preferenceStore.getInstant(this.getKeyName(R.string.preferences__privacy_policy_accept_date));
        }
        return null;
    }

    @Override
    public boolean getIsGroupCallsTooltipShown() {
        return this.preferenceStore.getBoolean(this.getKeyName(R.string.preferences__group_calls_tooltip_shown));
    }

    @Override
    public void setGroupCallsTooltipShown(boolean shown) {
        this.preferenceStore.save(this.getKeyName(R.string.preferences__group_calls_tooltip_shown), shown);
    }

    @Override
    public boolean getIsWorkHintTooltipShown() {
        return this.preferenceStore.getBoolean(this.getKeyName(R.string.preferences__tooltip_work_hint_shown));
    }

    @Override
    public void setIsWorkHintTooltipShown(boolean shown) {
        this.preferenceStore.save(this.getKeyName(R.string.preferences__tooltip_work_hint_shown), shown);
    }

    @Override
    public boolean getIsFaceBlurTooltipShown() {
        return this.preferenceStore.getBoolean(this.getKeyName(R.string.preferences__tooltip_face_blur_shown));
    }

    @Override
    public void setFaceBlurTooltipShown(boolean shown) {
        this.preferenceStore.save(this.getKeyName(R.string.preferences__tooltip_face_blur_shown), shown);
    }

    @Override
    public void setThreemaSafeEnabled(boolean value) {
        this.preferenceStore.save(this.getKeyName(R.string.preferences__threema_safe_enabled), value);
    }

    @Override
    public boolean getThreemaSafeEnabled() {
        return this.preferenceStore.getBoolean(this.getKeyName(R.string.preferences__threema_safe_enabled));
    }

    @Override
    public void setThreemaSafeMasterKey(byte[] masterKey) {
        encryptedPreferenceStore.save(getKeyName(R.string.preferences__threema_safe_masterkey), masterKey);
        ThreemaSafeMDMConfig.getInstance().saveConfig(this);
    }

    @Override
    public byte[] getThreemaSafeMasterKey() {
        return encryptedPreferenceStore.getBytes(this.getKeyName(R.string.preferences__threema_safe_masterkey));
    }

    @Override
    public void setThreemaSafeServerInfo(@Nullable ThreemaSafeServerInfo serverInfo) {
        encryptedPreferenceStore.save(getKeyName(R.string.preferences__threema_safe_server_name), serverInfo != null ? serverInfo.getCustomServerName() : null);
        encryptedPreferenceStore.save(getKeyName(R.string.preferences__threema_safe_server_username), serverInfo != null ? serverInfo.getServerUsername() : null);
        encryptedPreferenceStore.save(getKeyName(R.string.preferences__threema_safe_server_password), serverInfo != null ? serverInfo.getServerPassword() : null);
    }

    @Override
    @NonNull
    public ThreemaSafeServerInfo getThreemaSafeServerInfo() {
        return new ThreemaSafeServerInfo(
            encryptedPreferenceStore.getString(getKeyName(R.string.preferences__threema_safe_server_name)),
            encryptedPreferenceStore.getString(getKeyName(R.string.preferences__threema_safe_server_username)),
            encryptedPreferenceStore.getString(getKeyName(R.string.preferences__threema_safe_server_password))
        );
    }

    @Override
    public void setThreemaSafeUploadTimestamp(@Nullable Instant timestamp) {
        this.preferenceStore.save(this.getKeyName(R.string.preferences__threema_safe_backup_date), timestamp);
    }

    @Override
    @Nullable
    public Instant getThreemaSafeUploadTimestamp() {
        return this.preferenceStore.getInstant(this.getKeyName(R.string.preferences__threema_safe_backup_date));
    }

    @Override
    public boolean getShowUnreadBadge() {
        return this.preferenceStore.getBoolean(this.getKeyName(R.string.preferences__show_unread_badge));
    }

    @Override
    public void setThreemaSafeErrorCode(int code) {
        this.preferenceStore.save(this.getKeyName(R.string.preferences__threema_safe_error_code), code);
    }

    @Override
    public int getThreemaSafeErrorCode() {
        return this.preferenceStore.getInt(this.getKeyName(R.string.preferences__threema_safe_error_code));
    }

    @Override
    public void setThreemaSafeErrorTimestamp(@Nullable Instant timestamp) {
        this.preferenceStore.save(this.getKeyName(R.string.preferences__threema_safe_create_error_date), timestamp);
    }

    @Override
    @Nullable
    public Instant getThreemaSafeErrorTimestamp() {
        return this.preferenceStore.getInstant(this.getKeyName(R.string.preferences__threema_safe_create_error_date));
    }

    @Override
    public void setThreemaSafeServerMaxUploadSize(long maxBackupBytes) {
        this.preferenceStore.save(this.getKeyName(R.string.preferences__threema_safe_server_upload_size), maxBackupBytes);
    }

    @Override
    public long getThreemaSafeServerMaxUploadSize() {
        return this.preferenceStore.getLong(this.getKeyName(R.string.preferences__threema_safe_server_upload_size));
    }

    @Override
    public void setThreemaSafeServerRetention(int days) {
        this.preferenceStore.save(this.getKeyName(R.string.preferences__threema_safe_server_retention), days);
    }

    @Override
    public int getThreemaSafeServerRetention() {
        return this.preferenceStore.getInt(this.getKeyName(R.string.preferences__threema_safe_server_retention));
    }

    @Override
    public void setThreemaSafeBackupSize(int size) {
        this.preferenceStore.save(this.getKeyName(R.string.preferences__threema_safe_upload_size), size);
    }

    @Override
    public int getThreemaSafeBackupSize() {
        return this.preferenceStore.getInt(this.getKeyName(R.string.preferences__threema_safe_upload_size));
    }

    @Override
    public void setThreemaSafeHashString(String hashString) {
        this.preferenceStore.save(this.getKeyName(R.string.preferences__threema_safe_hash_string), hashString);
    }

    @Override
    public String getThreemaSafeHashString() {
        return this.preferenceStore.getString(this.getKeyName(R.string.preferences__threema_safe_hash_string));
    }

    @Override
    public void setThreemaSafeBackupTimestamp(@Nullable Instant timestamp) {
        this.preferenceStore.save(this.getKeyName(R.string.preferences__threema_safe_backup_date), timestamp);
    }

    @Override
    @Nullable
    public Instant getThreemaSafeBackupTimestamp() {
        return this.preferenceStore.getInstant(this.getKeyName(R.string.preferences__threema_safe_backup_date));
    }

    @Override
    public void setWorkSyncCheckInterval(int checkInterval) {
        this.preferenceStore.save(this.getKeyName(R.string.preferences__work_sync_check_interval), checkInterval);
    }

    @Override
    public int getWorkSyncCheckInterval() {
        return this.preferenceStore.getInt(this.getKeyName(R.string.preferences__work_sync_check_interval));
    }

    @Override
    public void setIdentityStateSyncInterval(int syncIntervalS) {
        this.preferenceStore.save(this.getKeyName(R.string.preferences__identity_states_check_interval), syncIntervalS);
    }

    @Override
    public int getIdentityStateSyncIntervalS() {
        return this.preferenceStore.getInt(this.getKeyName(R.string.preferences__identity_states_check_interval));
    }

    @Override
    public boolean getIsExportIdTooltipShown() {
        return this.preferenceStore.getBoolean(this.getKeyName(R.string.preferences__tooltip_export_id_shown));
    }

    @Override
    public void setThreemaSafeMDMConfig(String mdmConfigHash) {
        encryptedPreferenceStore.save(getKeyName(R.string.preferences__work_safe_mdm_config), mdmConfigHash);
    }

    @Override
    public String getThreemaSafeMDMConfig() {
        return encryptedPreferenceStore.getString(getKeyName(R.string.preferences__work_safe_mdm_config));
    }

    @Override
    public void setWorkDirectoryEnabled(boolean enabled) {
        this.preferenceStore.save(this.getKeyName(R.string.preferences__work_directory_enabled), enabled);
    }

    @Override
    public boolean getWorkDirectoryEnabled() {
        return this.preferenceStore.getBoolean(this.getKeyName(R.string.preferences__work_directory_enabled));
    }

    @Override
    public void setWorkDirectoryCategories(List<WorkDirectoryCategory> categories) {
        JSONArray array = new JSONArray();
        for (WorkDirectoryCategory category : categories) {
            String categoryObjectString = category.toJSON();
            if (!TestUtil.isEmptyOrNull(categoryObjectString)) {
                try {
                    array.put(new JSONObject(categoryObjectString));
                } catch (JSONException e) {
                    logger.error("Exception", e);
                }
            }
        }
        encryptedPreferenceStore.save(getKeyName(R.string.preferences__work_directory_categories), array);
    }

    @Override
    public List<WorkDirectoryCategory> getWorkDirectoryCategories() {
        JSONArray array = encryptedPreferenceStore.getJSONArray(getKeyName(R.string.preferences__work_directory_categories));
        List<WorkDirectoryCategory> categories = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            try {
                JSONObject jsonObject = array.optJSONObject(i);
                if (jsonObject != null) {
                    categories.add(new WorkDirectoryCategory(jsonObject));
                }
            } catch (Exception e) {
                logger.error("Exception", e);
            }
        }
        return categories;
    }

    @Override
    public void setWorkOrganization(WorkOrganization organization) {
        encryptedPreferenceStore.save(getKeyName(R.string.preferences__work_directory_organization), organization.toJSON());
    }

    @Override
    public WorkOrganization getWorkOrganization() {
        JSONObject object = encryptedPreferenceStore.getJSONObject(getKeyName(R.string.preferences__work_directory_organization));

        if (object != null) {
            return new WorkOrganization(object);
        }
        return null;
    }

    @Override
    public void setLicensedStatus(boolean licensed) {
        this.preferenceStore.save(this.getKeyName(R.string.preferences__license_status), licensed);
    }

    @Override
    public boolean getLicensedStatus() {
        return this.preferenceStore.getBoolean(this.getKeyName(R.string.preferences__license_status), true);
    }

    @Override
    public void setShowDeveloperMenu(boolean show) {
        this.preferenceStore.save(
            this.getKeyName(R.string.preferences__developer_menu),
            show
        );
    }

    @Override
    public boolean showDeveloperMenu() {
        return ConfigUtils.isDevBuild() && this.preferenceStore.getBoolean(
            this.getKeyName(R.string.preferences__developer_menu),
            false
        );
    }

    @Override
    public Uri getDataBackupUri() {
        String backupUri = this.preferenceStore.getString(this.getKeyName(R.string.preferences__data_backup_uri));
        if (backupUri != null && !backupUri.isEmpty()) {
            return Uri.parse(backupUri);
        }
        return null;
    }

    @Override
    public void setDataBackupUri(Uri newUri) {
        this.preferenceStore.save(
            this.getKeyName(R.string.preferences__data_backup_uri),
            newUri != null ? newUri.toString() : null
        );
    }

    @Override
    @Nullable
    public Instant getLastDataBackupTimestamp() {
        return this.preferenceStore.getInstant(this.getKeyName(R.string.preferences__last_data_backup_date));
    }

    @Override
    public void setLastDataBackupTimestamp(@Nullable Instant timestamp) {
        this.preferenceStore.save(this.getKeyName(R.string.preferences__last_data_backup_date), timestamp);
    }

    @Override
    public String getMatchToken() {
        return this.preferenceStore.getString(this.getKeyName(R.string.preferences__match_token));
    }

    @Override
    public void setMatchToken(String matchToken) {
        this.preferenceStore.save(this.getKeyName(R.string.preferences__match_token), matchToken);
    }

    @Override
    public boolean isAfterWorkDNDEnabled() {
        return this.preferenceStore.getBoolean(this.getKeyName(R.string.preferences__working_days_enable));
    }

    @Override
    public void setAfterWorkDNDEnabled(boolean enabled) {
        this.preferenceStore.save(this.getKeyName(R.string.preferences__working_days_enable), enabled);
    }

    @Override
    public void setCameraFlashMode(int flashMode) {
        this.preferenceStore.save(this.getKeyName(R.string.preferences__camera_flash_mode), flashMode);
    }

    @Override
    public int getCameraFlashMode() {
        return this.preferenceStore.getInt(this.getKeyName(R.string.preferences__camera_flash_mode));
    }

    @Override
    public void setPipPosition(int pipPosition) {
        this.preferenceStore.save(this.getKeyName(R.string.preferences__pip_position), pipPosition);
    }

    @Override
    public int getPipPosition() {
        return this.preferenceStore.getInt(this.getKeyName(R.string.preferences__pip_position));
    }

    @Override
    @Nullable
    public String getVideoCallsProfile() {
        return this.preferenceStore.getString(this.getKeyName(R.string.preferences__voip_video_profile));
    }

    @Override
    public void setBallotOverviewHidden(boolean hidden) {
        this.preferenceStore.save(this.getKeyName(R.string.preferences__ballot_overview_hidden), hidden);
    }

    @Override
    public boolean getBallotOverviewHidden() {
        return this.preferenceStore.getBoolean(this.getKeyName(R.string.preferences__ballot_overview_hidden));
    }

    @Override
    public int getVideoCallToggleTooltipCount() {
        return this.preferenceStore.getInt(this.getKeyName(R.string.preferences__tooltip_video_toggle));
    }

    @Override
    public void incrementVideoCallToggleTooltipCount() {
        this.preferenceStore.save(this.getKeyName(R.string.preferences__tooltip_video_toggle), getVideoCallToggleTooltipCount() + 1);
    }

    @Override
    public boolean getCameraPermissionRequestShown() {
        return this.preferenceStore.getBoolean(this.getKeyName(R.string.preferences__camera_permission_request_shown), false);
    }

    @Override
    public void setCameraPermissionRequestShown(boolean shown) {
        this.preferenceStore.save(this.getKeyName(R.string.preferences__camera_permission_request_shown), shown);
    }

    @Override
    public void setVoiceRecorderBluetoothDisabled(boolean disabled) {
        this.preferenceStore.save(this.getKeyName(R.string.preferences__voicerecorder_bluetooth_disabled), disabled);

    }

    @Override
    public boolean getVoiceRecorderBluetoothDisabled() {
        return this.preferenceStore.getBoolean(this.getKeyName(R.string.preferences__voicerecorder_bluetooth_disabled), true);
    }

    @Override
    public void setAudioPlaybackSpeed(float newSpeed) {
        this.preferenceStore.save(this.getKeyName(R.string.preferences__audio_playback_speed), newSpeed);
    }

    @Override
    public float getAudioPlaybackSpeed() {
        return this.preferenceStore.getFloat(this.getKeyName(R.string.preferences__audio_playback_speed), 1f);
    }

    @Override
    public int getMultipleRecipientsTooltipCount() {
        return this.preferenceStore.getInt(this.getKeyName(R.string.preferences__tooltip_multi_recipients));
    }

    @Override
    public void incrementMultipleRecipientsTooltipCount() {
        this.preferenceStore.save(this.getKeyName(R.string.preferences__tooltip_multi_recipients), getMultipleRecipientsTooltipCount() + 1);
    }

    @Override
    public boolean isGroupCallSendInitEnabled() {
        return ConfigUtils.isDevBuild() && this.preferenceStore.getBoolean(this.getKeyName(R.string.preferences__group_call_send_init), false);
    }

    @Override
    public boolean skipGroupCallCreateDelay() {
        return ConfigUtils.isDevBuild() && this.preferenceStore.getBoolean(this.getKeyName(R.string.preferences__group_call_skip_delay), false);
    }

    @Override
    public long getBackupWarningDismissedTime() {
        return this.preferenceStore.getLong(this.getKeyName(R.string.preferences__backup_warning_dismissed_time));
    }

    @Override
    public void setBackupWarningDismissedTime(long time) {
        this.preferenceStore.save(this.getKeyName(R.string.preferences__backup_warning_dismissed_time), time);
    }

    @Override
    @StarredMessagesSortOrder
    public int getStarredMessagesSortOrder() {
        return this.preferenceStore.getInt(this.getKeyName(R.string.preferences__starred_messages_sort_order));
    }

    @Override
    public void setStarredMessagesSortOrder(@StarredMessagesSortOrder int order) {
        this.preferenceStore.save(this.getKeyName(R.string.preferences__starred_messages_sort_order), order);
    }

    @Override
    public void setAutoDeleteDays(int i) {
        this.preferenceStore.save(this.getKeyName(R.string.preferences__auto_delete_days), i);
    }

    @Override
    public int getAutoDeleteDays() {
        Integer autoDeleteDays = this.preferenceStore.getInt(this.getKeyName(R.string.preferences__auto_delete_days));
        if (autoDeleteDays != null) {
            return validateKeepMessageDays(autoDeleteDays);
        }
        return 0;
    }

    @Override
    public void removeLastNotificationRationaleShown() {
        String key = this.getKeyName(R.string.preferences__last_notification_rationale_shown);
        if (this.preferenceStore.containsKey(key)) {
            this.preferenceStore.remove(key);
        }
    }

    @Override
    public void getMediaGalleryContentTypes(boolean[] contentTypes) {
        Arrays.fill(contentTypes, true);
        JSONArray array = preferenceStore.getJSONArray(getKeyName(R.string.preferences__media_gallery_content_types));
        if (array != null && array.length() > 0 && array.length() == contentTypes.length) {
            for (int i = 0; i < array.length(); i++) {
                try {
                    boolean value = array.getBoolean(i);
                    contentTypes[i] = value;
                } catch (JSONException e) {
                    logger.error("JSON error", e);
                }
            }
        }
    }

    @Override
    public void setMediaGalleryContentTypes(boolean[] contentTypes) {
        JSONArray jsonArray;
        try {
            jsonArray = new JSONArray(contentTypes);
            this.preferenceStore.save(this.getKeyName(R.string.preferences__media_gallery_content_types), jsonArray);
        } catch (JSONException e) {
            logger.error("JSON error", e);
        }
    }

    @Override
    public int getEmailSyncHashCode() {
        return this.preferenceStore.getInt(this.getKeyName(R.string.preferences__email_sync_hash));
    }

    @Override
    public int getPhoneNumberSyncHashCode() {
        return this.preferenceStore.getInt(this.getKeyName(R.string.preferences__phone_number_sync_hash));
    }

    @Override
    public void setEmailSyncHashCode(int emailsHash) {
        this.preferenceStore.save(this.getKeyName(R.string.preferences__email_sync_hash), emailsHash);
    }

    @Override
    public void setPhoneNumberSyncHashCode(int phoneNumbersHash) {
        this.preferenceStore.save(this.getKeyName(R.string.preferences__phone_number_sync_hash), phoneNumbersHash);
    }

    @Override
    public void setTimeOfLastContactSync(long timeMs) {
        this.preferenceStore.save(this.getKeyName(R.string.preferences__contact_sync_time), timeMs);
    }

    @Override
    public long getTimeOfLastContactSync() {
        return this.preferenceStore.getLong(this.getKeyName(R.string.preferences__contact_sync_time));
    }

    @Override
    public boolean showMessageDebugInfo() {
        return this.preferenceStore.getBoolean(this.getKeyName(R.string.preferences__message_debug_info), false);
    }

    @Override
    public boolean showConversationLastUpdate() {
        return this.preferenceStore.getBoolean(this.getKeyName(R.string.preferences__show_last_update_prefix), false);
    }

    @Override
    @Nullable
    public Instant getLastShortcutUpdateTimestamp() {
        return this.preferenceStore.getInstant(this.getKeyName(R.string.preferences__last_shortcut_update_date));
    }

    @Override
    public void setLastShortcutUpdateTimestamp(@Nullable Instant timestamp) {
        this.preferenceStore.save(this.getKeyName(R.string.preferences__last_shortcut_update_date), timestamp);
    }

    @Override
    public void setLastNotificationPermissionRequestTimestamp(long timestamp) {
        this.preferenceStore.save(this.getKeyName(R.string.preferences__last_notification_request_timestamp), timestamp);
    }

    @Override
    public long getLastNotificationPermissionRequestTimestamp() {
        return this.preferenceStore.getLong(this.getKeyName(R.string.preferences__last_notification_request_timestamp));
    }

    @Nullable
    @Override
    public Instant getLastMultiDeviceGroupCheckTimestamp() {
        return preferenceStore.getInstant(getKeyName(R.string.preferences__last_multi_device_group_check_timestamp));
    }

    @Override
    public void setLastMultiDeviceGroupCheckTimestamp(final @NonNull Instant timestamp) {
        preferenceStore.save(getKeyName(R.string.preferences__last_multi_device_group_check_timestamp), timestamp);
    }

    @Nullable
    @Override
    public SynchronizedBooleanSetting getSynchronizedBooleanSettingByKey(@Nullable String key) {
        if (key == null) {
            return null;
        }

        return booleanSettingsMap.get(key);
    }

    @Override
    public void reloadSynchronizedBooleanSettings() {
        for (SynchronizedBooleanSetting setting : booleanSettingsMap.values()) {
            setting.reload();
        }
    }
}
