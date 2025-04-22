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

package ch.threema.app.services;

import android.content.Context;
import android.media.RingtoneManager;
import android.net.Uri;

import org.slf4j.Logger;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

import java.util.HashMap;

import ch.threema.app.R;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.RingtoneUtil;
import ch.threema.app.utils.TestUtil;
import ch.threema.base.utils.LoggingUtil;

public class RingtoneServiceImpl implements RingtoneService {
    private final static Logger logger = LoggingUtil.getThreemaLogger("RingtoneServiceImpl");
    @NonNull
    private final NotificationPreferenceService notificationPreferenceService;
    private HashMap<String, String> ringtones;
    private final boolean supportsNotificationChannels = ConfigUtils.supportsNotificationChannels();

    public RingtoneServiceImpl(
        @NonNull NotificationPreferenceService notificationPreferenceService
    ) {
        this.notificationPreferenceService = notificationPreferenceService;

        init();
    }

    @Override
    public void init() {
        if (supportsNotificationChannels) {
            // Set empty hash map as notification channels are supported and therefore ringtones
            // won't be managed by us.
            HashMap<String, String> emptyRingtones = new HashMap<>();
            notificationPreferenceService.setLegacyRingtones(emptyRingtones);
            ringtones = emptyRingtones;
        } else {
            ringtones = new HashMap<>(notificationPreferenceService.getLegacyRingtones());
        }
    }

    @Override
    public void setRingtone(String uniqueId, Uri ringtoneUri) {
        if (supportsNotificationChannels) {
            logger.error("Cannot set ringtone if notification channels are supported");
            return;
        }

        String ringtone = null;

        if (ringtoneUri != null) {
            ringtone = ringtoneUri.toString();
        }

        if (ringtoneUri != null && RingtoneManager.isDefault(ringtoneUri)) {
            ringtones.remove(uniqueId);
        } else {
            ringtones.put(uniqueId, ringtone);
        }

        notificationPreferenceService.setLegacyRingtones(ringtones);
    }

    @Override
    public Uri getRingtoneFromUniqueId(String uniqueId) {
        String ringtone = ringtones.get(uniqueId);
        // check for "null" string (HTC bug)
        if (ringtone != null && !ringtone.equals(ServicesConstants.PREFERENCES_NULL)) {
            return Uri.parse(ringtone);
        } else {
            // silent
            return null;
        }
    }

    @Override
    public boolean hasCustomRingtone(String uniqueId) {
        return ringtones != null && ringtones.containsKey(uniqueId);
    }

    @Override
    public void removeCustomRingtone(String uniqueId) {
        if (supportsNotificationChannels) {
            logger.warn("No need to remove custom ringtone if notification channels are supported");
        }

        if (ringtones != null && ringtones.containsKey(uniqueId)) {
            ringtones.remove(uniqueId);

            notificationPreferenceService.setLegacyRingtones(ringtones);
        }
    }

    @Override
    public void resetRingtones(Context context) {
        if (ringtones != null) {
            ringtones.clear();
            notificationPreferenceService.setLegacyRingtones(ringtones);
        }
        notificationPreferenceService.setLegacyGroupNotificationSound(Uri.parse(context.getString(R.string.default_notification_sound)));
        notificationPreferenceService.setLegacyNotificationSound(Uri.parse(context.getString(R.string.default_notification_sound)));
        notificationPreferenceService.setLegacyVoipCallRingtone(RingtoneUtil.THREEMA_CALL_RINGTONE_URI);
        notificationPreferenceService.setLegacyNotificationPriority(NotificationCompat.PRIORITY_HIGH);
    }

    @Override
    public Uri getContactRingtone(String uniqueId) {
        if (ringtones.containsKey(uniqueId)) {
            return getRingtoneFromUniqueId(uniqueId);
        } else {
            return notificationPreferenceService.getLegacyNotificationSound();
        }
    }

    @Override
    public Uri getGroupRingtone(String uniqueId) {
        if (ringtones.containsKey(uniqueId)) {
            return getRingtoneFromUniqueId(uniqueId);
        } else {
            return notificationPreferenceService.getLegacyGroupNotificationSound();
        }
    }

    @Override
    public Uri getDefaultContactRingtone() {
        if (supportsNotificationChannels) {
            return null;
        }

        return notificationPreferenceService.getLegacyNotificationSound();
    }

    @Override
    public Uri getDefaultGroupRingtone() {
        if (supportsNotificationChannels) {
            return null;
        }

        return notificationPreferenceService.getLegacyGroupNotificationSound();
    }

    @Override
    public boolean isSilent(String uniqueId, boolean isGroup) {
        if (supportsNotificationChannels) {
            // Note that we do not manage the sound of notifications if notification channels are
            // supported. Therefore we always return false as we do not display this particularly.
            return false;
        }

        if (!TestUtil.isEmptyOrNull(uniqueId)) {
            Uri defaultRingtone, selectedRingtone;

            if (isGroup) {
                defaultRingtone = getDefaultGroupRingtone();
                selectedRingtone = getGroupRingtone(uniqueId);
            } else {
                defaultRingtone = getDefaultContactRingtone();
                selectedRingtone = getContactRingtone(uniqueId);
            }
            return !(defaultRingtone != null && defaultRingtone.equals(selectedRingtone)) && hasNoRingtone(uniqueId);
        }
        return false;
    }

    private boolean hasNoRingtone(String uniqueId) {
        Uri ringtone = getRingtoneFromUniqueId(uniqueId);
        return (ringtone == null || ringtone.toString().equals(ServicesConstants.PREFERENCES_NULL));
    }
}
