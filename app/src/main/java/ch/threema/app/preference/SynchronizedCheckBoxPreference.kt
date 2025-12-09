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

package ch.threema.app.preference

import android.content.Context
import android.util.AttributeSet
import androidx.preference.CheckBoxPreference
import ch.threema.app.ThreemaApplication
import ch.threema.app.preference.service.SynchronizedBooleanSetting
import ch.threema.base.utils.getThreemaLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private val logger = getThreemaLogger("SynchronizedCheckBoxPreference")

class SynchronizedCheckBoxPreference : CheckBoxPreference {
    @Suppress("unused")
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes)

    @Suppress("unused")
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    @Suppress("unused")
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    private val synchronizedSetting: SynchronizedBooleanSetting?

    private var stateCollector: Job? = null

    init {
        val serviceManager = ThreemaApplication.getServiceManager()
        if (serviceManager != null) {
            synchronizedSetting = serviceManager.preferenceService.getSynchronizedBooleanSettingByKey(key)

            if (synchronizedSetting == null) {
                logger.error("Could not get synchronized setting with key '$key'")
                disablePreference()
            }
        } else {
            logger.error("Cannot get synchronized setting with key '$key' as the service manager is null")
            synchronizedSetting = null
        }
    }

    override fun onClick() {
        if (synchronizedSetting == null) {
            logger.error("Cannot click on preference with key {} because its setting is null", key)
            return
        }
        super.onClick()
    }

    override fun onAttached() {
        super.onAttached()

        if (synchronizedSetting == null) {
            return
        }

        stateCollector = CoroutineScope(Dispatchers.Main).launch {
            synchronizedSetting.asStateFlow().collect { value ->
                mChecked = value
                notifyDependencyChange(shouldDisableDependents())
                notifyChanged()
            }
        }
    }

    override fun onDetached() {
        super.onDetached()

        stateCollector?.cancel()
    }

    override fun persistBoolean(value: Boolean): Boolean {
        return if (synchronizedSetting != null) {
            logger.debug("Setting value {}", value)

            // Only set the value again if it is different to prevent unnecessary reflection
            if (synchronizedSetting.get() != value) {
                synchronizedSetting.setFromLocal(value)
            }
            true
        } else {
            logger.error("Cannot persist preference with key '$key' as the synchronized setting is unavailable")
            false
        }
    }

    override fun setEnabled(enabled: Boolean) {
        if (enabled && synchronizedSetting == null) {
            // We cannot enable this preference if the synchronized setting is null
            return
        }

        super.setEnabled(enabled)
    }

    private fun disablePreference() {
        isEnabled = false
    }
}
