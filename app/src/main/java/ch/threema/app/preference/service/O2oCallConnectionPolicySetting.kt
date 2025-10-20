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
import ch.threema.protobuf.d2d.sync.MdD2DSync.Settings.O2oCallConnectionPolicy

private val logger = LoggingUtil.getThreemaLogger("O2oCallConnectionPolicySetting")

/**
 * The setting whether 1:1 calls should be relayed through Threema servers. Stores true if they should be relayed through Threema servers.
 */
class O2oCallConnectionPolicySetting internal constructor(
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
        return ReflectSettingsSyncTask.ReflectO2oCallConnectionPolicySyncUpdate(
            multiDeviceManager,
            nonceFactory,
            preferenceService,
        )
    }

    fun getO2oCallConnectionPolicy(): O2oCallConnectionPolicy =
        when (get()) {
            true -> O2oCallConnectionPolicy.REQUIRE_RELAYED_CONNECTION
            false -> O2oCallConnectionPolicy.ALLOW_DIRECT_CONNECTION
        }

    fun setFromSync(o2oCallConnectionPolicy: O2oCallConnectionPolicy) {
        val value = when (o2oCallConnectionPolicy) {
            O2oCallConnectionPolicy.REQUIRE_RELAYED_CONNECTION -> true
            O2oCallConnectionPolicy.ALLOW_DIRECT_CONNECTION -> false
            O2oCallConnectionPolicy.UNRECOGNIZED -> {
                logger.warn("Cannot set unrecognized 1:1 call connection policy")
                return
            }
        }

        setFromSync(value)
    }

    companion object {
        @JvmStatic
        val preferenceKeyStringRes = R.string.preferences__voip_force_turn
    }
}
