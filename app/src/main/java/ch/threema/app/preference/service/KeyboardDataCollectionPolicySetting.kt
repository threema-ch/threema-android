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
import ch.threema.protobuf.d2d.sync.MdD2DSync.Settings.KeyboardDataCollectionPolicy

private val logger = LoggingUtil.getThreemaLogger("KeyboardDataCollectionPolicySetting")

/**
 * The setting whether the keyboard should be requested to disable data collection. Stores true if the collection should be prevented.
 */
class KeyboardDataCollectionPolicySetting internal constructor(
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
        return ReflectSettingsSyncTask.ReflectKeyboardDataCollectionPolicySyncUpdate(
            multiDeviceManager,
            nonceFactory,
            preferenceService,
        )
    }

    fun getKeyboardDataCollectionPolicy(): KeyboardDataCollectionPolicy =
        when (get()) {
            true -> KeyboardDataCollectionPolicy.DENY_DATA_COLLECTION
            false -> KeyboardDataCollectionPolicy.ALLOW_DATA_COLLECTION
        }

    fun setFromSync(keyboardDataCollectionPolicy: KeyboardDataCollectionPolicy) {
        val value = when (keyboardDataCollectionPolicy) {
            KeyboardDataCollectionPolicy.DENY_DATA_COLLECTION -> true
            KeyboardDataCollectionPolicy.ALLOW_DATA_COLLECTION -> false
            KeyboardDataCollectionPolicy.UNRECOGNIZED -> {
                logger.warn("Cannot set unrecognized keyboard data collection policy")
                return
            }
        }

        setFromSync(value)
    }

    companion object {
        @JvmStatic
        val preferenceKeyStringRes = R.string.preferences__incognito_keyboard
    }
}
