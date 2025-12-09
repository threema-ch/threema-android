/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2013-2025 Threema GmbH
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

import android.content.Intent
import android.os.Bundle
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import ch.threema.android.context
import ch.threema.app.R
import ch.threema.app.home.HomeActivity
import ch.threema.app.startup.AppStartupActivity
import ch.threema.app.startup.AppStartupMonitor
import ch.threema.app.startup.models.AppSystem
import ch.threema.common.waitAtMost
import ch.threema.localcrypto.MasterKeyManager
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class MainActivity : ThreemaAppCompatActivity() {

    private val appStartupMonitor: AppStartupMonitor by inject()
    private val masterKeyManager: MasterKeyManager by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        splashScreen.setKeepOnScreenCondition {
            // keep the splash screen visible until the activity is finished
            true
        }
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            brieflyAwaitServiceManagerIfNeeded()
            continueToHomeActivity()
        }
    }

    /**
     * If the master key is not protected (with passphrase or remote secrets), then we expect the app to become ready quickly.
     * To avoid switching to the [AppStartupActivity] loading spinner and immediately leaving it again and thereby
     * unnecessarily finishing and recreating [HomeActivity], we wait here for a few seconds while
     * displaying the splash screen. In the unlikely event that the service manager does not become ready
     * within this time, we continue normally and let [HomeActivity] handle the waiting.
     * We only wait for the [AppSystem.UNLOCKED_MASTER_KEY], not [AppSystem.DATABASE_UPDATES]
     * or [AppSystem.SYSTEM_UPDATES], as those might take significantly longer
     * and we want to display [AppStartupActivity] for those.
     */
    private suspend fun brieflyAwaitServiceManagerIfNeeded() {
        if (appStartupMonitor.hasErrors()) {
            return
        }
        waitAtMost(3.seconds) {
            if (!masterKeyManager.isProtected()) {
                appStartupMonitor.awaitSystem(AppSystem.UNLOCKED_MASTER_KEY)
            }
        }
    }

    private fun continueToHomeActivity() {
        val intent = Intent(context, HomeActivity::class.java)
        startActivity(intent)
        overridePendingTransition(R.anim.abc_fade_in, R.anim.abc_fade_out)
        finish()
    }
}
