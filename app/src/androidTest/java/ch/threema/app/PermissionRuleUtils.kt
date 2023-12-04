/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2023 Threema GmbH
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

package ch.threema.app

import android.os.Build
import androidx.test.rule.GrantPermissionRule

/**
 * Get the permission rule for the notification permission. This method can be used to only grant
 * the permission for Android TIRAMISU and newer.
 */
fun getNotificationPermissionRule(): GrantPermissionRule {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        GrantPermissionRule.grant(android.Manifest.permission.POST_NOTIFICATIONS)
    } else {
        GrantPermissionRule.grant()
    }
}

/**
 * Get the READ_EXTERNAL_STORAGE and WRITE_EXTERNAL_STORAGE permissions. This method can be used to
 * grant the permissions only for those android versions that need them.
 */
fun getReadWriteExternalStoragePermissionRule(): GrantPermissionRule {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        // Not needed for Q and newer, therefore return an empty grant permission rule
        GrantPermissionRule.grant()
    } else {
        GrantPermissionRule.grant(
            android.Manifest.permission.READ_EXTERNAL_STORAGE,
            android.Manifest.permission.WRITE_EXTERNAL_STORAGE
        )
    }
}
