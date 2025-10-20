/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2025 Threema GmbH
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

package ch.threema.app.preference.service

import android.content.Context
import ch.threema.app.R
import ch.threema.app.multidevice.MultiDeviceManager
import ch.threema.app.stores.PreferenceStore
import ch.threema.app.tasks.ReflectSettingsSyncTask
import ch.threema.base.crypto.NonceFactory
import ch.threema.base.utils.LoggingUtil
import ch.threema.domain.taskmanager.Task
import ch.threema.domain.taskmanager.TaskCodec
import ch.threema.domain.taskmanager.TaskManager
import ch.threema.protobuf.d2d.sync.MdD2DSync.Settings.O2oCallVideoPolicy

private val logger = LoggingUtil.getThreemaLogger("O2oCallVideoPolicySetting")

/**
 * The setting whether 1:1 calls should be allowed to use video. Stores true if video is allowed.
 */
class O2oCallVideoPolicySetting internal constructor(
    private val preferenceService: PreferenceService,
    private val multiDeviceManager: MultiDeviceManager,
    private val nonceFactory: NonceFactory,
    taskManager: TaskManager,
    preferenceStore: PreferenceStore,
    context: Context,
) : SynchronizedBooleanSetting(
    preferenceKey = context.getString(preferenceKeyStringRes),
    preferenceStore = preferenceStore,
    multiDeviceManager = multiDeviceManager,
    taskManager = taskManager,
) {
    override fun instantiateReflectionTask(): Task<*, TaskCodec> {
        return ReflectSettingsSyncTask.ReflectO2oCallVideoPolicySyncUpdate(
            multiDeviceManager,
            nonceFactory,
            preferenceService,
        )
    }

    fun getO2oCallVideoPolicy(): O2oCallVideoPolicy =
        when (get()) {
            true -> O2oCallVideoPolicy.ALLOW_VIDEO
            false -> O2oCallVideoPolicy.DENY_VIDEO
        }

    fun setFromSync(o2oCallVideoPolicy: O2oCallVideoPolicy) {
        val value = when (o2oCallVideoPolicy) {
            O2oCallVideoPolicy.ALLOW_VIDEO -> true
            O2oCallVideoPolicy.DENY_VIDEO -> false
            O2oCallVideoPolicy.UNRECOGNIZED -> {
                logger.warn("Cannot set unrecognized 1:1 call video policy")
                return
            }
        }

        setFromSync(value)
    }

    companion object {
        @JvmStatic
        val preferenceKeyStringRes = R.string.preferences__voip_video_enable
    }
}
