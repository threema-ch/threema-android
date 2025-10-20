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

package ch.threema.app.activities.wizard

import android.content.Intent
import android.os.Bundle
import androidx.core.app.NotificationManagerCompat
import ch.threema.app.R
import ch.threema.app.activities.ThreemaAppCompatActivity
import ch.threema.app.backuprestore.csv.RestoreService
import ch.threema.app.services.UserService
import org.koin.android.ext.android.inject

class WizardStartActivity : ThreemaAppCompatActivity() {

    private val userService: UserService by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NotificationManagerCompat.from(this).cancel(RestoreService.RESTORE_COMPLETION_NOTIFICATION_ID)
        launchNextActivity()
    }

    private fun launchNextActivity() {
        val intent = if (userService.hasIdentity()) {
            Intent(this, WizardBaseActivity::class.java)
        } else {
            Intent(this, WizardIntroActivity::class.java)
        }

        startActivity(intent)
        overridePendingTransition(R.anim.abc_fade_in, R.anim.abc_fade_out)
        finish()
    }
}
