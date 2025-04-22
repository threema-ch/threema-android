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
import android.net.Uri;

import androidx.annotation.Nullable;

/**
 * The ringtone service provides either default or custom ringtones for contacts and groups. Note
 * that the ringtone manager only manages notification sounds until api 25. From api 26 on, the
 * ringtone manager won't return any custom notification sounds.
 */
public interface RingtoneService {

    void init();

    void setRingtone(String uniqueId, Uri ringtoneUri);

    /**
     * Get the ringtone uri from the given unique id. Note that this method returns null on api 26
     * and newer.
     */
    @Nullable
    Uri getRingtoneFromUniqueId(String uniqueId);

    /**
     * Get the ringtone uri from the given unique id. Note that this method returns null on api 26
     * and newer.
     */
    @Nullable
    Uri getContactRingtone(String uniqueId);

    /**
     * Get the ringtone uri from the given unique id. Note that this method returns null on api 26
     * and newer.
     */
    @Nullable
    Uri getGroupRingtone(String uniqueId);

    /**
     * Get the default ringtone for contacts. Note that this method returns null on api 26 and
     * newer.
     */
    @Nullable
    Uri getDefaultContactRingtone();

    /**
     * Get the default ringtone for groups. Note that this method returns null on api 26 and newer.
     */
    @Nullable
    Uri getDefaultGroupRingtone();

    /**
     * Check whether the given conversation is silent or not. Note that starting from api 26, this
     * method always returns false as the sound is managed by the system notification channel
     * settings.
     */
    boolean isSilent(String uniqueId, boolean isGroup);

    boolean hasCustomRingtone(String uniqueId);

    void removeCustomRingtone(String uniqueId);

    void resetRingtones(Context context);
}
