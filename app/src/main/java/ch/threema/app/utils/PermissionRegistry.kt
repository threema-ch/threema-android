/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2022 Threema GmbH
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

package ch.threema.app.utils

import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.UiThread
import androidx.appcompat.app.AppCompatActivity
import ch.threema.base.utils.LoggingUtil
import kotlinx.coroutines.CompletableDeferred

private val logger = LoggingUtil.getThreemaLogger("PermissionRegistry")

@UiThread
class PermissionRegistry(private val activity: AppCompatActivity) {
    data class PermissionResponse(
        val granted: Boolean,
        val prompted: Boolean,
    )

    private var requestCode: Int = 0
    private val pendingRequests = mutableMapOf<Int, CompletableDeferred<PermissionResponse>>()

    suspend fun requestMicrophonePermissions(): PermissionResponse = requestPermissions { code ->
        logger.info("Request microphone permission (#{})", code)
        ConfigUtils.requestAudioPermissions(activity, null, code)
    }

    suspend fun requestCameraPermissions(): PermissionResponse = requestPermissions { code ->
        logger.info("Request camera permission (#{})", code)
        ConfigUtils.requestCameraPermissions(activity, null, code)
    }

    suspend fun requestBluetoothPermission(showHelpDialog: Boolean): PermissionResponse = requestPermissions { code ->
        logger.info("Request nearby devices permission (#{})", code)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ConfigUtils.requestBluetoothConnectPermissions(activity, null, code, showHelpDialog)
        } else {
            // Permission not required pre version S and therefore we can treat it as granted
            true
        }
    }

    fun handlePermissionResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        // All permissions must be granted or we fail
        val granted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }
        val pendingRequest = pendingRequests.remove(requestCode)
        if (pendingRequest == null) {
            logger.warn("No request found for permission result, permissions: '{}'", permissions)
            return
        }
        val applied = pendingRequest.complete(PermissionResponse(
            granted = granted,
            prompted = true
        ))
        if (applied) {
            logger.debug("Permissions granted: '{}'", permissions)
        } else {
            logger.warn("Request was already resolved, permissions: '{}'", permissions)
        }
    }

    private suspend fun requestPermissions(requestFn: (Int) -> Boolean): PermissionResponse {
        val requestCode = this.requestCode++
        return if (requestFn(requestCode)) {
            logger.info("Permission granted (#{})", requestCode)
            return PermissionResponse(
                granted = true, prompted = false
            )
        } else {
            CompletableDeferred<PermissionResponse>().also {
                pendingRequests[requestCode] = it
            }.await()
        }
    }
}
