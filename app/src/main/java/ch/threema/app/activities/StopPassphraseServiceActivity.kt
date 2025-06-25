/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2018-2025 Threema GmbH
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

package ch.threema.app.activities

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import ch.threema.app.ThreemaApplication
import ch.threema.app.services.PassphraseService
import ch.threema.app.utils.ConfigUtils
import ch.threema.base.utils.LoggingUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private val logger = LoggingUtil.getThreemaLogger("StopPassphraseServiceActivity")

/**
 * Simple activity to stop passphrase service, lock master key and finish the app removing it from recents list - to be used from the persistent notification
 */
class StopPassphraseServiceActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val masterKey = ThreemaApplication.getMasterKey()
        if (masterKey.isProtected && !masterKey.isLocked) {
            val serviceManager = ThreemaApplication.getServiceManager()
            val connection = serviceManager?.connection
            if (connection != null && connection.isRunning) {
                lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        connection.stop()
                    } catch (e: InterruptedException) {
                        logger.error("Interrupted in onCreate while stopping threema connection", e)
                    }
                }
            }

            serviceManager?.notificationService?.cancelConversationNotificationsOnLockApp()

            masterKey.lock()
            ThreemaApplication.onMasterKeyLocked()
            PassphraseService.stop(this@StopPassphraseServiceActivity)
            ConfigUtils.scheduleAppRestart(this@StopPassphraseServiceActivity, 2000, null)
        }

        finishAndRemoveTask()
    }
}
