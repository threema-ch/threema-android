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

package ch.threema.app.activities.notificationpolicy

import android.os.Bundle
import android.view.View
import ch.threema.app.AppConstants
import ch.threema.app.services.RingtoneService
import ch.threema.app.utils.ContactUtil
import ch.threema.app.utils.logScreenVisibility
import ch.threema.base.utils.LoggingUtil
import ch.threema.data.models.ContactModel
import ch.threema.data.repositories.ContactModelRepository
import ch.threema.domain.types.Identity
import org.koin.android.ext.android.inject

private val logger = LoggingUtil.getThreemaLogger("ContactNotificationsActivity")

class ContactNotificationsActivity : NotificationsActivity() {
    init {
        logScreenVisibility(logger)
    }

    private val ringtoneService: RingtoneService by inject()
    private val contactModelRepository: ContactModelRepository by inject()

    private val contactIdentity: Identity? by lazy {
        intent.getStringExtra(AppConstants.INTENT_DATA_CONTACT)
    }

    private val contactModel: ContactModel? by lazy {
        contactIdentity?.let(contactModelRepository::getByIdentity)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (contactIdentity.isNullOrEmpty() || contactModel == null) {
            finish()
            return
        }

        uid = ContactUtil.getUniqueIdString(contactIdentity)

        refreshSettings()
    }

    public override fun refreshSettings() {
        defaultRingtone = ringtoneService.defaultContactRingtone
        selectedRingtone = ringtoneService.getContactRingtone(uid)

        super.refreshSettings()
    }

    override fun setupButtons() {
        super.setupButtons()

        radioSilentExceptMentions.visibility = View.GONE
    }

    override fun onSettingChanged(mutedOverrideUntil: Long?) {
        contactModel?.setNotificationTriggerPolicyOverrideFromLocal(mutedOverrideUntil)
    }

    override fun isMutedRightNow(): Boolean {
        val currentContactModelData = contactModel?.data ?: return false
        return currentContactModelData.currentNotificationTriggerPolicyOverride.muteAppliesRightNow
    }

    // This setting is only available for group chats
    override fun isMutedExceptMentions(): Boolean = false

    override fun getNotificationTriggerPolicyOverrideValue(): Long? {
        val currentContactModelData = contactModel?.data ?: return null
        return currentContactModelData.notificationTriggerPolicyOverride
    }
}
