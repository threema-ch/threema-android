/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2024 Threema GmbH
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
import ch.threema.app.R
import ch.threema.app.notifications.NotificationUtil
import ch.threema.app.stores.PreferenceStoreInterface

class NotificationPreferenceServiceImpl(
    private val context: Context,
    private val preferenceStore: PreferenceStoreInterface,
) : NotificationPreferenceService {

    override fun isMasterKeyNewMessageNotifications(): Boolean {
        return preferenceStore.getBoolean(getKeyName(R.string.preferences__masterkey_notification_newmsg))
    }

    override fun setWizardRunning(running: Boolean) {
        preferenceStore.save(getKeyName(R.string.preferences__wizard_running), running)
    }

    override fun getWizardRunning(): Boolean {
        return preferenceStore.getBoolean(getKeyName(R.string.preferences__wizard_running))
    }

    override fun getNotificationSound(): Uri? {
        return ringtoneKeyToUri(R.string.preferences__notification_sound)
    }

    override fun getGroupNotificationSound(): Uri? {
        return ringtoneKeyToUri(R.string.preferences__group_notification_sound)
    }

    override fun getGroupCallRingtone(): Uri? {
        return ringtoneKeyToUri(R.string.preferences__group_calls_ringtone)
    }

    override fun isVibrate(): Boolean {
        return preferenceStore.getBoolean(getKeyName(R.string.preferences__vibrate))
    }

    override fun isGroupVibrate(): Boolean {
        return preferenceStore.getBoolean(getKeyName(R.string.preferences__group_vibrate))
    }

    override fun isGroupCallVibrate(): Boolean {
        return preferenceStore.getBoolean(getKeyName(R.string.preferences__group_calls_vibration))
    }

    override fun isShowMessagePreview(): Boolean {
        return preferenceStore.getBoolean(getKeyName(R.string.preferences__notification_preview))
    }

    override fun setNotificationPriority(value: Int) {
        preferenceStore.save(
            getKeyName(R.string.preferences__notification_priority),
            value.toString()
        )
    }

    override fun getNotificationPriority(): Int {
        return NotificationUtil.getNotificationPriority(context)
    }

    override fun getDisableSmartReplies(): Boolean {
        return preferenceStore.getBoolean(
            getKeyName(R.string.preferences__disable_smart_replies),
            false
        )
    }

    private fun ringtoneKeyToUri(@StringRes ringtoneKey: Int): Uri? {
        val ringtone = preferenceStore.getString(this.getKeyName(ringtoneKey))
        if (!ringtone.isNullOrBlank() &&  ringtone != ServicesConstants.PREFERENCES_NULL) {
            return Uri.parse(ringtone)
        }
        return null
    }

    private fun getKeyName(@StringRes resourceId: Int): String {
        return context.getString(resourceId)
    }
}
