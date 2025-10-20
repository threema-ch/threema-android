/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2020-2025 Threema GmbH
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

package ch.threema.app.utils;

import android.content.Context;
import android.content.SharedPreferences;

import org.slf4j.Logger;

import java.util.Calendar;
import java.util.Set;

import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;
import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.data.datatypes.NotificationTriggerPolicyOverride;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.domain.stores.IdentityStore;

public class DNDUtil {
    private static final Logger logger = LoggingUtil.getThreemaLogger("DNDUtil");
    private final IdentityStore identityStore;
    private final Context context;

    // Singleton stuff
    private static DNDUtil sInstance = null;

    public static synchronized DNDUtil getInstance() {
        if (sInstance == null) {
            sInstance = new DNDUtil();
        }
        return sInstance;
    }

    private DNDUtil() {
        this.context = ThreemaApplication.getAppContext();
        this.identityStore = ThreemaApplication.getServiceManager().getIdentityStore();
    }

    /**
     * Returns true if the chat for the provided MessageReceiver is permanently or temporarily muted AT THIS TIME and
     * no intrusive notification should be shown for an incoming message
     * If a message text is provided it is checked for possible mentions - group messages only
     *
     * @param rawMessageText Text of the incoming message (optional, group messages only)
     */
    public boolean isMessageMuted(
        @Nullable NotificationTriggerPolicyOverride notificationTriggerPolicyOverride,
        @Nullable CharSequence rawMessageText
    ) {
        if (notificationTriggerPolicyOverride == null) {
            return false;
        }
        boolean isMutedByOverrideSetting = rawMessageText != null
            ? notificationTriggerPolicyOverride.muteAppliesRightNowToMessage(rawMessageText.toString(), identityStore.getIdentity())
            : notificationTriggerPolicyOverride.getMuteAppliesRightNow();
        return isMutedByOverrideSetting || isMutedWork();
    }

    /**
     * Check if Work DND schedule is currently active
     *
     * @return true if we're currently outside of the working hours set by the user and Work DND is currently enabled, false otherwise
     */
    public boolean isMutedWork() {
        if (ConfigUtils.isWorkBuild()) {
            SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
            if (sharedPreferences.getBoolean(context.getString(R.string.preferences__working_days_enable), false)) {
                // user has working hours DND enabled
                int dayOfWeek = Calendar.getInstance().get(Calendar.DAY_OF_WEEK) - 1; // day of week starts with 1 in Java

                Set<String> selectedWorkingDays = sharedPreferences.getStringSet(context.getString(R.string.preferences__working_days), null);
                if (selectedWorkingDays != null) {
                    if (!selectedWorkingDays.contains(String.valueOf(dayOfWeek))) {
                        // it's not a working day today
                        return true;
                    } else {
                        // check if hours match as well
                        int currentTimeStamp = Calendar.getInstance().get(Calendar.HOUR_OF_DAY) * 60 + Calendar.getInstance().get(Calendar.MINUTE);
                        try {
                            String[] startTime = sharedPreferences.getString(context.getString(R.string.preferences__work_time_start), "00:00").split(":");
                            String[] endTime = sharedPreferences.getString(context.getString(R.string.preferences__work_time_end), "23:59").split(":");

                            int startTimeStamp = Integer.parseInt(startTime[0]) * 60 + Integer.parseInt(startTime[1]);
                            int endTimeStamp = Integer.parseInt(endTime[0]) * 60 + Integer.parseInt(endTime[1]);

                            if (currentTimeStamp < startTimeStamp || currentTimeStamp > endTimeStamp) {
                                logger.info("Off-hours active");
                                return true;
                            }
                        } catch (Exception ignored) {
                            //
                        }
                    }
                }
            }
        }
        return false;
    }
}
