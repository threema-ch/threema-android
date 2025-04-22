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

package ch.threema.app.services;

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
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import ch.threema.app.BuildConfig;
import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.multidevice.MultiDeviceManager;
import ch.threema.app.stores.PreferenceStoreInterface;
import ch.threema.app.tasks.ReflectSettingsSyncTask;
import ch.threema.app.threemasafe.ThreemaSafeMDMConfig;
import ch.threema.app.threemasafe.ThreemaSafeServerInfo;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.TestUtil;
import ch.threema.base.crypto.NonceFactory;
import ch.threema.base.utils.Base64;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.domain.protocol.api.work.WorkDirectoryCategory;
import ch.threema.domain.protocol.api.work.WorkOrganization;
import ch.threema.domain.taskmanager.ActiveTask;
import ch.threema.domain.taskmanager.TaskManager;
import ch.threema.domain.taskmanager.TriggerSource;

import static ch.threema.app.utils.AutoDeleteUtil.validateKeepMessageDays;

public class PreferenceServiceImpl implements PreferenceService {
    private static final Logger logger = LoggingUtil.getThreemaLogger("PreferenceServiceImpl");

    private static final String CONTACT_PHOTO_BLOB_ID = "id";
    private static final String CONTACT_PHOTO_ENCRYPTION_KEY = "key";
    private static final String CONTACT_PHOTO_SIZE = "size";

    @NonNull
    private final Context context;
    @NonNull
    private final PreferenceStoreInterface preferenceStore;
    @NonNull
    private final TaskManager taskManager;
    @NonNull
    private final MultiDeviceManager multiDeviceManager;
    @NonNull
    private final NonceFactory nonceFactory;

    public PreferenceServiceImpl(
        @NonNull Context context,
        @NonNull PreferenceStoreInterface preferenceStore,
        @NonNull TaskManager taskManager,
        @NonNull MultiDeviceManager multiDeviceManager,
        @NonNull NonceFactory nonceFactory
    ) {
        this.context = context;
        this.preferenceStore = preferenceStore;
        this.taskManager = taskManager;
        this.multiDeviceManager = multiDeviceManager;
        this.nonceFactory = nonceFactory;
    }

    @Override
    public boolean isReadReceipts() {
        return this.preferenceStore.getBoolean(this.getKeyName(R.string.preferences__read_receipts));
    }

    @Override
    public void setReadReceipts(boolean value, @NonNull TriggerSource triggerSource) {
        String keyName = getKeyName(R.string.preferences__read_receipts);
        if (!changeRequired(keyName, value)) {
            return;
        }
        this.preferenceStore.save(keyName, value);
        scheduleSettingsSyncUpdateTask(new ReflectSettingsSyncTask.ReflectReadReceiptPolicySyncUpdate(
            multiDeviceManager,
            nonceFactory,
            this
        ), triggerSource);
    }

    @Override
    public boolean isSyncContacts() {
        return this.preferenceStore.getBoolean(this.getKeyName(R.string.preferences__sync_contacts));
    }

    @Override
    public void setSyncContacts(boolean setting) {
        this.preferenceStore.save(this.getKeyName(R.string.preferences__sync_contacts), setting);
    }

    @Override
    public boolean isBlockUnknown() {
        return this.preferenceStore.getBoolean(this.getKeyName(R.string.preferences__block_unknown));
    }

    @Override
    public void setBlockUnknown(boolean value, @NonNull TriggerSource triggerSource) {
        String keyName = getKeyName(R.string.preferences__block_unknown);
        if (!changeRequired(keyName, value)) {
            return;
        }
        this.preferenceStore.save(keyName, value);
        scheduleSettingsSyncUpdateTask(new ReflectSettingsSyncTask.ReflectUnknownContactPolicySyncUpdate(
            multiDeviceManager,
            nonceFactory,
            this
        ), triggerSource);
    }

    @Override
    public boolean isTypingIndicator() {
        return this.preferenceStore.getBoolean(this.getKeyName(R.string.preferences__typing_indicator));
    }

    @Override
    public void setTypingIndicator(boolean value, @NonNull TriggerSource triggerSource) {
        String keyName = getKeyName(R.string.preferences__typing_indicator);
        if (!changeRequired(keyName, value)) {
            return;
        }
        this.preferenceStore.save(keyName, value);
        scheduleSettingsSyncUpdateTask(new ReflectSettingsSyncTask.ReflectTypingIndicatorPolicySyncUpdate(
            multiDeviceManager,
            nonceFactory,
            this
        ), triggerSource);
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
        if (imageScale == null || imageScale.length() == 0) {
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
        if (videoSize == null || videoSize.length() == 0) {
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
        return this.preferenceStore.getStringCompat(this.getKeyName(R.string.preferences__license_username));
    }

    @Override
    public void setLicenseUsername(String username) {
        this.preferenceStore.save(this.getKeyName(R.string.preferences__license_username), username, true);
    }

    @Override
    public synchronized String getLicensePassword() {
        return this.preferenceStore.getStringCompat(this.getKeyName(R.string.preferences__license_password));
    }

    @Override
    public void setLicensePassword(String password) {
        this.preferenceStore.save(this.getKeyName(R.string.preferences__license_password), password, true);
    }

    @Override
    @Nullable
    public synchronized String getOnPremServer() {
        return this.preferenceStore.getStringCompat(this.getKeyName(R.string.preferences__onprem_server));
    }

    @Override
    public void setOnPremServer(String server) {
        this.preferenceStore.save(this.getKeyName(R.string.preferences__onprem_server), server, true);
    }

    @Override
    @Deprecated
    public LinkedList<Integer> getRecentEmojis() {
        LinkedList<Integer> list = new LinkedList<>();
        JSONArray array = this.preferenceStore.getJSONArray(this.getKeyName(R.string.preferences__recent_emojis), false);
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
        this.preferenceStore.save(this.getKeyName(R.string.preferences__recent_emojis2), list.toArray(new String[0]), false);
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

    /*
        @Override
        public boolean isPinLockEnabled() {
            return isPinSet() && this.preferenceStore.getBoolean(this.getKeyName(R.string.preferences__pin_lock_enabled));
        }
    */
    @Override
    public boolean isPinSet() {
        return isPinCodeValid(this.preferenceStore.getString(this.getKeyName(R.string.preferences__pin_lock_code), true));
    }

    @Override
    public boolean setPin(String newCode) {
        if (isPinCodeValid(newCode)) {
            this.preferenceStore.save(this.getKeyName(R.string.preferences__pin_lock_code), newCode, true);
            return true;
        } else {
            this.preferenceStore.remove(this.getKeyName(R.string.preferences__pin_lock_code));
        }
        return false;
    }

    @Override
    public boolean isPinCodeCorrect(String code) {
        String storedCode = this.preferenceStore.getString(this.getKeyName(R.string.preferences__pin_lock_code), true);

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
            return (code.length() >= ThreemaApplication.MIN_PIN_LENGTH &&
                code.length() <= ThreemaApplication.MAX_PIN_LENGTH &&
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
        return this.preferenceStore.getInt(this.getKeyName(R.string.preferences__id_backup_count));
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
    public void setLastIDBackupReminderDate(Date lastIDBackupReminderDate) {
        this.preferenceStore.save(
            this.getKeyName(R.string.preferences__last_id_backup_date),
            lastIDBackupReminderDate
        );
    }

    @Override
    public String getContactListSorting() {
        String sorting = this.preferenceStore.getString(this.getKeyName(R.string.preferences__contact_sorting));

        if (sorting == null || sorting.length() == 0) {
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

        if (format == null || format.length() == 0) {
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
    public boolean isDisableScreenshots() {
        return this.preferenceStore.getBoolean(this.getKeyName(R.string.preferences__hide_screenshots));
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
    public Date getLastIDBackupReminderDate() {
        return this.preferenceStore.getDate(this.getKeyName(R.string.preferences__last_id_backup_date));
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
        String[] res = this.preferenceStore.getStringArray(listName, encrypted);
        if (res == null && encrypted) {
            // check if we have an old unencrypted identity list - migrate if necessary and return its values
            if (this.preferenceStore.containsKey(listName)) {
                res = this.preferenceStore.getStringArray(listName, false);
                this.preferenceStore.remove(listName, false);
                if (res != null) {
                    this.preferenceStore.save(listName, res, true);
                }
            }
        }
        return res != null ? res : new String[0];
    }

    @Override
    public void setList(String listName, String[] elements) {
        this.preferenceStore.save(
            listName,
            elements,
            true
        );
    }

    @Override
    public void setListQuietly(String listName, String[] elements) {
        setListQuietly(listName, elements, true);
    }

    @Override
    public void setListQuietly(@NonNull String listName, @NonNull String[] elements, boolean encrypted) {
        this.preferenceStore.saveQuietly(
            listName,
            elements,
            encrypted
        );
    }

    @Override
    public HashMap<Integer, String> getHashMap(String listName, boolean encrypted) {
        return this.preferenceStore.getHashMap(listName, encrypted);
    }

    @Override
    public void setHashMap(String listName, HashMap<Integer, String> hashMap) {
        this.preferenceStore.save(
            listName,
            hashMap
        );
    }

    @Override
    public HashMap<String, String> getStringHashMap(String listName, boolean encrypted) {
        return this.preferenceStore.getStringHashMap(listName, encrypted);
    }

    @Override
    public void setStringHashMap(String listName, HashMap<String, String> hashMap) {
        this.preferenceStore.saveStringHashMap(
            listName,
            hashMap,
            false
        );
    }

    @Override
    public void clear() {
        this.preferenceStore.clear();
    }

    @Override
    public List<String[]> write() {
        List<String[]> res = new ArrayList<>();
        Map<String, ?> values = this.preferenceStore.getAllNonCrypted();
        Iterator<String> i = values.keySet().iterator();
        while (i.hasNext()) {
            String key = i.next();
            Object v = values.get(key);

            String value = null;
            if (v instanceof Boolean) {
                value = String.valueOf(v);
            } else if (v instanceof Float) {
                value = String.valueOf(v);
            } else if (v instanceof Integer) {
                value = String.valueOf(v);
            } else if (v instanceof Long) {
                value = String.valueOf(v);
            } else if (v instanceof String) {
                value = ((String) v);
            }
            res.add(new String[]{
                key,
                value,
                v.getClass().toString()
            });
        }
        return res;
    }

    @Override
    public boolean read(List<String[]> values) {

        for (String[] v : values) {
            if (v.length != 3) {
                //invalid row
                return false;
            }

            String key = v[0];
            String value = v[1];
            String valueClass = v[2];

            if (valueClass.equals(Boolean.class.toString())) {
                this.preferenceStore.save(key, Boolean.parseBoolean(value));
            } else if (valueClass.equals(Integer.class.toString())) {
                this.preferenceStore.save(key, Integer.parseInt(value));
            } else if (valueClass.equals(Long.class.toString())) {
                this.preferenceStore.save(key, Long.valueOf(value));
            } else if (valueClass.equals(String.class.toString())) {
                this.preferenceStore.save(key, value);
            }
        }

        return true;
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
    public int getAppThemeValue() {
        String theme = this.preferenceStore.getString(this.getKeyName(R.string.preferences__theme));
        if (theme != null && theme.length() > 0) {
            return Integer.parseInt(theme);
        }
        return Integer.parseInt(BuildConfig.DEFAULT_APP_THEME);
    }

    @Override
    public int getEmojiStyle() {
        String theme = this.preferenceStore.getString(this.getKeyName(R.string.preferences__emoji_style));
        if (theme != null && theme.length() > 0) {
            if (Integer.parseInt(theme) == 1) {
                return EmojiStyle_ANDROID;
            }
        }
        return EmojiStyle_DEFAULT;
    }

    @Override
    public void setLockoutDeadline(long deadline) {
        this.preferenceStore.save(this.getKeyName(R.string.preferences__lockout_deadline), deadline);
    }

    @Override
    public void setLockoutTimeout(long timeout) {
        this.preferenceStore.save(this.getKeyName(R.string.preferences__lockout_timeout), timeout);
    }

    @Override
    public long getLockoutDeadline() {
        return this.preferenceStore.getLong(this.getKeyName(R.string.preferences__lockout_deadline));
    }

    @Override
    public long getLockoutTimeout() {
        return this.preferenceStore.getLong(this.getKeyName(R.string.preferences__lockout_timeout));
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
    public void setAppLogoExpiresAt(Date expiresAt, @ConfigUtils.AppThemeSetting String theme) {
        this.preferenceStore.save(this.getKeyName(
            ConfigUtils.THEME_DARK.equals(theme) ?
                R.string.preferences__app_logo_dark_expires_at :
                R.string.preferences__app_logo_light_expires_at), expiresAt);
    }

    @Override
    public Date getAppLogoExpiresAt(@ConfigUtils.AppThemeSetting String theme) {
        return this.preferenceStore.getDate(this.getKeyName(
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
    public void setSaveToGallery(Boolean booleanPreset) {
        this.preferenceStore.save(this.getKeyName(R.string.preferences__save_media), booleanPreset);
    }

    @Override
    public void setDisableScreenshots(Boolean booleanPreset) {
        this.preferenceStore.save(this.getKeyName(R.string.preferences__hide_screenshots), booleanPreset);
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
    public void setImageAttachPreviewsEnabled(boolean enable) {
        this.preferenceStore.save(this.getKeyName(R.string.preferences__image_attach_previews), enable);
    }

    @Override
    public boolean isDirectShare() {
        return this.preferenceStore.getBoolean(this.getKeyName(R.string.preferences__direct_share));
    }

    @Override
    public void setMessageDrafts(HashMap<String, String> messageDrafts) {
        this.preferenceStore.saveStringHashMap(this.getKeyName(R.string.preferences__message_drafts), messageDrafts, true);
    }

    @Override
    public HashMap<String, String> getMessageDrafts() {
        return this.preferenceStore.getStringHashMap(this.getKeyName(R.string.preferences__message_drafts), true);
    }

    @Override
    public void setQuoteDrafts(HashMap<String, String> quoteDrafts) {
        this.preferenceStore.saveStringHashMap(this.getKeyName(R.string.preferences__quote_drafts), quoteDrafts, true);
    }

    @Override
    public HashMap<String, String> getQuoteDrafts() {
        return this.preferenceStore.getStringHashMap(this.getKeyName(R.string.preferences__quote_drafts), true);
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
        this.preferenceStore.save(this.getAppLogoKey(theme), url, true);
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
        return this.preferenceStore.getString(this.getAppLogoKey(theme), true);
    }

    @Override
    public void setCustomSupportUrl(String supportUrl) {
        this.preferenceStore.save(this.getKeyName(R.string.preferences__custom_support_url), supportUrl, true);
    }

    @Override
    public String getCustomSupportUrl() {
        return this.preferenceStore.getString(this.getKeyName(R.string.preferences__custom_support_url), true);
    }

    @Override
    public HashMap<String, String> getDiverseEmojiPrefs() {
        return this.preferenceStore.getStringHashMap(this.getKeyName(R.string.preferences__diverse_emojis), false);
    }

    @Override
    public void setDiverseEmojiPrefs(HashMap<String, String> diverseEmojis) {
        this.preferenceStore.saveStringHashMap(this.getKeyName(R.string.preferences__diverse_emojis), diverseEmojis, false);
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
        this.preferenceStore.save(
            this.getKeyName(R.string.preferences__push_token),
            fcmToken,
            true);
    }

    @Override
    public String getPushToken() {
        return this.preferenceStore.getString(
            this.getKeyName(R.string.preferences__push_token),
            true
        );
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
    public long getProfilePicUploadDate() {
        return this.preferenceStore.getDateAsLong(this.getKeyName(R.string.preferences__profile_pic_upload_date));
    }

    @Override
    public void setProfilePicUploadDate(Date date) {
        this.preferenceStore.save(this.getKeyName(R.string.preferences__profile_pic_upload_date), date);
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

        this.preferenceStore.save(this.getKeyName(R.string.preferences__profile_pic_upload_data), toStore, true);
    }

    @Override
    @Nullable
    public ContactService.ProfilePictureUploadData getProfilePicUploadData() {
        JSONObject fromStore = this.preferenceStore.getJSONObject(this.getKeyName(R.string.preferences__profile_pic_upload_data), true);
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
    public boolean getForceTURN() {
        return this.preferenceStore.getBoolean(this.getKeyName(R.string.preferences__voip_force_turn));
    }

    @Override
    public void setForceTURN(boolean value) {
        this.preferenceStore.save(this.getKeyName(R.string.preferences__voip_force_turn), value);
    }

    @Override
    public boolean isVoipEnabled() {
        return this.preferenceStore.getBoolean(this.getKeyName(R.string.preferences__voip_enable));
    }

    @Override
    public void setVoipEnabled(boolean value) {
        this.preferenceStore.save(this.getKeyName(R.string.preferences__voip_enable), value);
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
        return this.preferenceStore.getStringSet(this.getKeyName(R.string.preferences__auto_download_mobile), R.array.list_auto_download_mobile_default);
    }

    @Override
    public Set<String> getWifiAutoDownload() {
        return this.preferenceStore.getStringSet(this.getKeyName(R.string.preferences__auto_download_wifi), R.array.list_auto_download_wifi_default);
    }

    @Override
    public void setRandomRatingRef(String ref) {
        this.preferenceStore.save(this.getKeyName(R.string.preferences__rate_ref), ref, true);
    }

    @Override
    public String getRandomRatingRef() {
        return this.preferenceStore.getString(this.getKeyName(R.string.preferences__rate_ref), true);
    }

    @Override
    public void setRatingReviewText(String review) {
        this.preferenceStore.save(this.getKeyName(R.string.preferences__rate_text), review, true);
    }

    @Override
    public String getRatingReviewText() {
        return this.preferenceStore.getString(this.getKeyName(R.string.preferences__rate_text), true);
    }

    @Override
    public void setPrivacyPolicyAccepted(Date date, int source) {
        this.preferenceStore.save(this.getKeyName(R.string.preferences__privacy_policy_accept_date), date);
        this.preferenceStore.save(this.getKeyName(R.string.preferences__privacy_policy_accept_source), source);
    }

    @Override
    public Date getPrivacyPolicyAccepted() {
        if (this.preferenceStore.getInt(this.getKeyName(R.string.preferences__privacy_policy_accept_source)) != PRIVACY_POLICY_ACCEPT_NONE) {
            return this.preferenceStore.getDate(this.getKeyName(R.string.preferences__privacy_policy_accept_date));
        }
        return null;
    }

    @Override
    public void clearPrivacyPolicyAccepted() {
        this.preferenceStore.remove(this.getKeyName(R.string.preferences__privacy_policy_accept_date));
        this.preferenceStore.remove(this.getKeyName(R.string.preferences__privacy_policy_accept_source));
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
        this.preferenceStore.save(this.getKeyName(R.string.preferences__threema_safe_masterkey), masterKey, true);
        ThreemaSafeMDMConfig.getInstance().saveConfig(this);
    }

    @Override
    public byte[] getThreemaSafeMasterKey() {
        return this.preferenceStore.getBytes(this.getKeyName(R.string.preferences__threema_safe_masterkey), true);
    }

    @Override
    public void setThreemaSafeServerInfo(@Nullable ThreemaSafeServerInfo serverInfo) {
        this.preferenceStore.save(this.getKeyName(R.string.preferences__threema_safe_server_name), serverInfo != null ? serverInfo.getCustomServerName() : null, true);
        this.preferenceStore.save(this.getKeyName(R.string.preferences__threema_safe_server_username), serverInfo != null ? serverInfo.getServerUsername() : null, true);
        this.preferenceStore.save(this.getKeyName(R.string.preferences__threema_safe_server_password), serverInfo != null ? serverInfo.getServerPassword() : null, true);
    }

    @Override
    @NonNull
    public ThreemaSafeServerInfo getThreemaSafeServerInfo() {
        return new ThreemaSafeServerInfo(
            this.preferenceStore.getString(this.getKeyName(R.string.preferences__threema_safe_server_name), true),
            this.preferenceStore.getString(this.getKeyName(R.string.preferences__threema_safe_server_username), true),
            this.preferenceStore.getString(this.getKeyName(R.string.preferences__threema_safe_server_password), true)
        );
    }

    @Override
    public void setThreemaSafeUploadDate(Date date) {
        this.preferenceStore.save(this.getKeyName(R.string.preferences__threema_safe_backup_date), date);
    }

    @Override
    @Nullable
    public Date getThreemaSafeUploadDate() {
        return this.preferenceStore.getDate(this.getKeyName(R.string.preferences__threema_safe_backup_date));
    }

    @Override
    public void setIncognitoKeyboard(boolean enabled) {
        this.preferenceStore.save(this.getKeyName(R.string.preferences__incognito_keyboard), enabled);
    }

    @Override
    public boolean getIncognitoKeyboard() {
        return this.preferenceStore.getBoolean(this.getKeyName(R.string.preferences__incognito_keyboard));
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
    public void setThreemaSafeErrorDate(@Nullable Date date) {
        this.preferenceStore.save(this.getKeyName(R.string.preferences__threema_safe_create_error_date), date);
    }

    @Override
    @Nullable
    public Date getThreemaSafeErrorDate() {
        return this.preferenceStore.getDate(this.getKeyName(R.string.preferences__threema_safe_create_error_date));
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
    public void setThreemaSafeBackupDate(Date date) {
        this.preferenceStore.save(this.getKeyName(R.string.preferences__threema_safe_backup_date), date);
    }

    @Override
    public Date getThreemaSafeBackupDate() {
        return this.preferenceStore.getDate(this.getKeyName(R.string.preferences__threema_safe_backup_date));
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
        this.preferenceStore.save(this.getKeyName(R.string.preferences__work_safe_mdm_config), mdmConfigHash, true);
    }

    @Override
    public String getThreemaSafeMDMConfig() {
        return this.preferenceStore.getString(this.getKeyName(R.string.preferences__work_safe_mdm_config), true);
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
        this.preferenceStore.save(this.getKeyName(R.string.preferences__work_directory_categories), array, true);
    }

    @Override
    public List<WorkDirectoryCategory> getWorkDirectoryCategories() {
        JSONArray array = this.preferenceStore.getJSONArray(this.getKeyName(R.string.preferences__work_directory_categories), true);
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
        this.preferenceStore.save(this.getKeyName(R.string.preferences__work_directory_organization), organization.toJSON(), true);
    }

    @Override
    public WorkOrganization getWorkOrganization() {
        JSONObject object = this.preferenceStore.getJSONObject(this.getKeyName(R.string.preferences__work_directory_organization), true);

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
        if (backupUri != null && backupUri.length() > 0) {
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
    public Date getLastDataBackupDate() {
        return this.preferenceStore.getDate(this.getKeyName(R.string.preferences__last_data_backup_date));
    }

    @Override
    public void setLastDataBackupDate(Date date) {
        this.preferenceStore.save(this.getKeyName(R.string.preferences__last_data_backup_date), date);
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
    public boolean isVideoCallsEnabled() {
        return this.preferenceStore.getBoolean(this.getKeyName(R.string.preferences__voip_video_enable));
    }

    @Override
    public boolean isGroupCallsEnabled() {
        return this.preferenceStore.getBoolean(this.getKeyName(R.string.preferences__group_calls_enable));
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
    public void setGroupRequestsOverviewHidden(boolean hidden) {
        this.preferenceStore.save(this.getKeyName(R.string.preferences__group_request_overview_hidden), hidden);
    }

    @Override
    public boolean getGroupRequestsOverviewHidden() {
        return this.preferenceStore.getBoolean(this.getKeyName(R.string.preferences__group_request_overview_hidden));
    }

    @Override
    public int getVideoCallToggleTooltipCount() {
        return this.preferenceStore.getInt(this.getKeyName(R.string.preferences__tooltip_video_toggle));
    }

    @Override
    public void incremenetVideoCallToggleTooltipCount() {
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
        JSONArray array = this.preferenceStore.getJSONArray(this.getKeyName(R.string.preferences__media_gallery_content_types), false);
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
    public boolean showConversationLastUpdate() {
        return this.preferenceStore.getBoolean(this.getKeyName(R.string.preferences__show_last_update_prefix), false);
    }

    @Override
    public Date getLastShortcutUpdateDate() {
        return this.preferenceStore.getDate(this.getKeyName(R.string.preferences__last_shortcut_update_date));
    }

    @Override
    public void setLastShortcutUpdateDate(Date date) {
        this.preferenceStore.save(this.getKeyName(R.string.preferences__last_shortcut_update_date), date);
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
        final long utcMillisOrZero = this.preferenceStore.getLong(
            this.getKeyName(R.string.preferences__last_multi_device_group_check_timestamp)
        );
        if (utcMillisOrZero > 0L) {
            return Instant.ofEpochMilli(utcMillisOrZero);
        } else {
            return null;
        }
    }

    @Override
    public void setLastMultiDeviceGroupCheckTimestamp(final @NonNull Instant timestamp) {
        final long utcMillis = timestamp.toEpochMilli();
        this.preferenceStore.save(
            this.getKeyName(R.string.preferences__last_multi_device_group_check_timestamp),
            utcMillis
        );
    }

    private boolean changeRequired(@NonNull String keyName, boolean value) {
        return !preferenceStore.containsKey(keyName) || preferenceStore.getBoolean(keyName) != value;
    }

    private void scheduleSettingsSyncUpdateTask(@NonNull ActiveTask<?> task, TriggerSource triggerSource) {
        if (multiDeviceManager.isMultiDeviceActive() && triggerSource == TriggerSource.LOCAL) {
            taskManager.schedule(task);
        }
    }
}
