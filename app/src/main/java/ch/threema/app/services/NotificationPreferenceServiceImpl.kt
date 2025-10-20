/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2024-2025 Threema GmbH
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

package ch.threema.app.services

import android.content.Context
import android.net.Uri
import androidx.annotation.StringRes
import androidx.core.app.NotificationCompat
import androidx.core.net.toUri
import ch.threema.app.R
import ch.threema.app.stores.PreferenceStore

class NotificationPreferenceServiceImpl(
    private val context: Context,
    private val preferenceStore: PreferenceStore,
) : NotificationPreferenceService {
    override fun isMasterKeyNewMessageNotifications(): Boolean =
        preferenceStore.getBoolean(getKeyName(R.string.preferences__masterkey_notification_newmsg))

    override fun setWizardRunning(running: Boolean) =
        preferenceStore.save(getKeyName(R.string.preferences__wizard_running), running)

    override fun getWizardRunning(): Boolean =
        preferenceStore.getBoolean(getKeyName(R.string.preferences__wizard_running))

    override fun getLegacyNotificationSound(): Uri? =
        preferenceStore.getRingtoneUri(R.string.preferences__notification_sound)

    override fun setLegacyNotificationSound(uri: Uri?) {
        preferenceStore.save(getKeyName(R.string.preferences__notification_sound), uri?.toString())
    }

    override fun getLegacyGroupNotificationSound(): Uri? =
        preferenceStore.getRingtoneUri(R.string.preferences__group_notification_sound)

    override fun setLegacyGroupNotificationSound(uri: Uri?) {
        preferenceStore.save(getKeyName(R.string.preferences__group_notification_sound), uri?.toString())
    }

    override fun getLegacyVoipCallRingtone(): Uri? =
        preferenceStore.getRingtoneUri(R.string.preferences__voip_ringtone)

    override fun setLegacyVoipCallRingtone(uri: Uri?) {
        preferenceStore.save(getKeyName(R.string.preferences__voip_ringtone), uri?.toString())
    }

    override fun getLegacyGroupCallRingtone(): Uri? =
        preferenceStore.getRingtoneUri(R.string.preferences__group_calls_ringtone)

    override fun isLegacyNotificationVibrate(): Boolean =
        preferenceStore.getBoolean(getKeyName(R.string.preferences__vibrate))

    override fun isLegacyGroupVibrate(): Boolean =
        preferenceStore.getBoolean(getKeyName(R.string.preferences__group_vibrate))

    override fun isLegacyVoipCallVibrate(): Boolean =
        preferenceStore.getBoolean(getKeyName(R.string.preferences__voip_vibration))

    override fun isLegacyGroupCallVibrate(): Boolean =
        preferenceStore.getBoolean(getKeyName(R.string.preferences__group_calls_vibration))

    override fun isLegacyNotificationLightEnabled(): Boolean =
        preferenceStore.getBoolean(getKeyName(R.string.preferences__notification_light_single), true)

    override fun isLegacyGroupNotificationLightEnabled(): Boolean =
        preferenceStore.getBoolean(getKeyName(R.string.preferences__notification_light_group), true)

    override fun isShowMessagePreview(): Boolean =
        preferenceStore.getBoolean(getKeyName(R.string.preferences__notification_preview))

    override fun getLegacyNotificationPriority(): Int =
        preferenceStore.getString(getKeyName(R.string.preferences__notification_priority))
            ?.toIntOrNull()
            ?: NotificationCompat.PRIORITY_HIGH

    override fun setLegacyNotificationPriority(value: Int) {
        preferenceStore.save(
            getKeyName(R.string.preferences__notification_priority),
            value.toString(),
        )
    }

    override fun getLegacyRingtones(): Map<String, String?> =
        preferenceStore.getMap(getKeyName(R.string.preferences__individual_ringtones))

    override fun setLegacyRingtones(ringtones: Map<String, String?>) {
        preferenceStore.save(getKeyName(R.string.preferences__individual_ringtones), ringtones)
    }

    override fun getDisableSmartReplies(): Boolean =
        preferenceStore.getBoolean(getKeyName(R.string.preferences__disable_smart_replies), false)

    private fun getKeyName(@StringRes resourceId: Int): String =
        context.getString(resourceId)

    private fun PreferenceStore.getRingtoneUri(@StringRes ringtoneKey: Int): Uri? =
        getString(getKeyName(ringtoneKey))
            .takeUnless { it.isNullOrBlank() }
            ?.takeUnless { it == ServicesConstants.PREFERENCES_NULL }
            ?.toUri()
}
