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
import androidx.annotation.UiThread
import ch.threema.app.ThreemaApplication
import ch.threema.app.utils.GroupUtil
import ch.threema.app.utils.logScreenVisibility
import ch.threema.base.utils.LoggingUtil
import ch.threema.data.datatypes.NotificationTriggerPolicyOverride
import ch.threema.data.repositories.GroupModelRepository

private val logger = LoggingUtil.getThreemaLogger("GroupNotificationsActivity")

class GroupNotificationsActivity : NotificationsActivity() {
    init {
        logScreenVisibility(logger)
    }

    private companion object {
        const val GROUP_ID_NOT_PASSED = -1
    }

    private val groupModelRepository: GroupModelRepository by lazy {
        ThreemaApplication.requireServiceManager().modelRepositories.groups
    }

    private val localGroupId: Int by lazy {
        intent.getIntExtra(ThreemaApplication.INTENT_DATA_GROUP_DATABASE_ID, GROUP_ID_NOT_PASSED)
    }

    private val groupModel: ch.threema.data.models.GroupModel? by lazy {
        groupModelRepository.getByLocalGroupDbId(localGroupId.toLong())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (localGroupId == GROUP_ID_NOT_PASSED || groupModel == null) {
            finish()
            return
        }
        uid = GroupUtil.getUniqueIdString(groupModel)

        refreshSettings()
    }

    public override fun refreshSettings() {
        defaultRingtone = ringtoneService.defaultGroupRingtone
        selectedRingtone = ringtoneService.getGroupRingtone(uid)

        super.refreshSettings()
    }

    @UiThread
    override fun updateUI() {
        super.updateUI()
    }

    override fun setupButtons() {
        super.setupButtons()
        radioSilentExceptMentions.visibility = View.VISIBLE
    }

    override fun onSettingChanged(muteOverrideUntil: Long?) {
        groupModel?.setNotificationTriggerPolicyOverrideFromLocal(muteOverrideUntil)
    }

    override fun isMutedRightNow(): Boolean {
        val currentGroupModelData = groupModel?.data?.value ?: return false
        return currentGroupModelData.currentNotificationTriggerPolicyOverride.muteAppliesRightNow
    }

    override fun isMutedExceptMentions(): Boolean {
        val currentGroupModelData = groupModel?.data?.value ?: return false
        return currentGroupModelData.currentNotificationTriggerPolicyOverride is NotificationTriggerPolicyOverride.MutedIndefiniteExceptMentions
    }

    override fun getNotificationTriggerPolicyOverrideValue(): Long? {
        val currentGroupModelData = groupModel?.data?.value ?: return null
        return currentGroupModelData.notificationTriggerPolicyOverride
    }
}
