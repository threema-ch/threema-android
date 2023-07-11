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

package ch.threema.app.utils

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Parcel
import android.os.Parcelable
import androidx.activity.result.ActivityResultLauncher
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import ch.threema.app.R
import ch.threema.app.activities.PermissionRequestActivity
import ch.threema.app.activities.PermissionRequestActivity.Companion.INTENT_PERMISSION_REQUESTS

/**
 * Launches the [PermissionRequestActivity] with three permissions:
 * - Microphone (required)
 * - Nearby Devices (optional)
 * - Phone (optional)
 *
 * The given permission launcher will receive [Activity.RESULT_OK] when at least the microphone
 * permission is given. If microphone permission is denied, then [Activity.RESULT_CANCELED] is
 * received.
 *
 * @param activity           the activity that starts the permission request
 * @param permissionLauncher the permission launcher that is triggered after the permissions are
 *                           granted or denied
 * @param runIfGranted       the runnable that is executed if the permissions are granted before
 *                           the permission request activity has been started
 */
fun requestCallPermissions(
    activity: Activity,
    permissionLauncher: ActivityResultLauncher<Intent>,
    runIfGranted: (() -> Unit)?,
) {
    val requests = arrayListOf(
        PermissionRequest(
            Permission.PERMISSION_MICROPHONE,
            activity.getString(R.string.call_mic_permission_description),
            R.drawable.ic_microphone_outline,
            optional = false,
            null,
        ),
        PermissionRequest(
            Permission.PERMISSION_BLUETOOTH,
            activity.getString(R.string.call_nearby_devices_permission_description),
            R.drawable.ic_bluetooth,
            optional = true,
            activity.getString(R.string.preferences__ignore_bluetooth_permission_request),
        ),
        PermissionRequest(
            Permission.PERMISSION_READ_PHONE_STATE,
            activity.getString(R.string.call_phone_permission_description),
            R.drawable.ic_phone_in_talk,
            optional = true,
            activity.getString(R.string.preferences__ignore_read_phone_state_permission_request)
        )
    )
    launchForRequests(activity, permissionLauncher, runIfGranted, requests)
}

/**
 * Launches the [PermissionRequestActivity] with three permissions:
 * - Microphone (required)
 * - Nearby Devices (optional)
 * - Phone (optional)
 *
 * The given permission launcher will receive [Activity.RESULT_OK] when at least the microphone
 * permission is given. If microphone permission is denied, then [Activity.RESULT_CANCELED] is
 * received.
 *
 * @param activity           the activity that starts the permission request
 * @param permissionLauncher the permission launcher that is triggered after the permissions are
 *                           granted or denied
 * @param runIfGranted       the runnable that is executed if the permissions are granted before
 *                           the permission request activity has been started
 */
fun requestGroupCallPermissions(
    activity: Activity,
    permissionLauncher: ActivityResultLauncher<Intent>,
    runIfGranted: (() -> Unit)?,
) {
    val requests: ArrayList<PermissionRequest> = arrayListOf(
        PermissionRequest(
            Permission.PERMISSION_MICROPHONE,
            activity.getString(R.string.group_call_mic_permission_description),
            R.drawable.ic_microphone_outline,
            optional = false,
            null,
        ),
        PermissionRequest(
            Permission.PERMISSION_BLUETOOTH,
            activity.getString(R.string.group_call_nearby_devices_permission_description),
            R.drawable.ic_bluetooth,
            optional = true,
            activity.getString(R.string.preferences__ignore_bluetooth_permission_request),
        ),
        PermissionRequest(
            Permission.PERMISSION_READ_PHONE_STATE,
            activity.getString(R.string.group_call_phone_permission_description),
            R.drawable.ic_phone_in_talk,
            optional = true,
            activity.getString(R.string.preferences__ignore_read_phone_state_permission_request)
        )
    )
    requests.removeAll { !it.permission.isRequired() }

    if (runIfGranted == null || permissionRequestNeeded(activity, requests)) {
        val intent = Intent(activity, PermissionRequestActivity::class.java)
        intent.putExtra(INTENT_PERMISSION_REQUESTS, requests)
        permissionLauncher.launch(intent)
    } else {
        runIfGranted()
    }
}

private fun launchForRequests(
    activity: Activity,
    permissionLauncher: ActivityResultLauncher<Intent>,
    runIfGranted: (() -> Unit)?,
    requests: ArrayList<PermissionRequest>,
) {
    // Remove not required permissions (depending on android version)
    requests.removeAll { !it.permission.isRequired() }

    if (runIfGranted == null || permissionRequestNeeded(activity, requests)) {
        val intent = Intent(activity, PermissionRequestActivity::class.java)
        intent.putExtra(INTENT_PERMISSION_REQUESTS, requests)
        permissionLauncher.launch(intent)
    } else {
        runIfGranted()
    }
}

/**
 * We use this enum to specify permissions as there might be different permission strings depending
 * on the Android version.
 */
enum class Permission {
    PERMISSION_BLUETOOTH,
    PERMISSION_CAMERA,
    PERMISSION_MICROPHONE,
    PERMISSION_READ_PHONE_STATE;

    /**
     * Some permissions are not required on certain API levels.
     */
    fun isRequired(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return false
        }

        return when (this) {
            PERMISSION_BLUETOOTH -> Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
            PERMISSION_READ_PHONE_STATE -> Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
            else -> true
        }
    }

    /**
     * For some cases we need different permission strings depending on the API level.
     */
    fun getPermissionString(): String {
        return when (this) {
            PERMISSION_BLUETOOTH -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Manifest.permission.BLUETOOTH_CONNECT
            } else {
                Manifest.permission.BLUETOOTH
            }
            PERMISSION_CAMERA -> Manifest.permission.CAMERA
            PERMISSION_MICROPHONE -> Manifest.permission.RECORD_AUDIO
            PERMISSION_READ_PHONE_STATE -> Manifest.permission.READ_PHONE_STATE
        }
    }

    /**
     * Permissions may have different names depending on the API level.
     */
    fun getPermissionName(context: Context): String {
        return when (this) {
            PERMISSION_BLUETOOTH -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getString(R.string.permission_nearby_devices)
            } else {
                context.getString(R.string.permission_bluetooth)
            }
            PERMISSION_CAMERA -> context.getString(R.string.permission_camera)
            PERMISSION_MICROPHONE -> context.getString(R.string.permission_microphone)
            PERMISSION_READ_PHONE_STATE -> context.getString(R.string.permission_read_phone_state)
        }
    }

}

/**
 * The PermissionRequest is used to specify the permission that is requested.
 */
data class PermissionRequest(
    val permission: Permission,                 // the permission that is requested
    val description: String,                    // the explanation that is shown
    @DrawableRes val icon: Int,                 // the icon of the permission
    val optional: Boolean,                      // false if the permission is required
    val permissionIgnorePreference: String?,    // the 'never-ask-again'-preference (if nonnull)
) : Parcelable {

    constructor(parcel: Parcel) : this(
        Permission.valueOf(parcel.readString()!!),
        parcel.readString()!!,
        parcel.readInt(),
        parcel.readByte().toBoolean(),
        parcel.readString(),
    )

    override fun describeContents(): Int = 0

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeString(permission.name)
        dest.writeString(description)
        dest.writeInt(icon)
        dest.writeByte(optional.toByte())
        dest.writeString(permissionIgnorePreference)
    }

    companion object CREATOR : Parcelable.Creator<PermissionRequest> {
        override fun createFromParcel(parcel: Parcel): PermissionRequest {
            return PermissionRequest(parcel)
        }

        override fun newArray(size: Int): Array<PermissionRequest?> {
            return arrayOfNulls(size)
        }

        private fun Byte.toBoolean(): Boolean {
            return this != 0.toByte()
        }

        private fun Boolean.toByte(): Byte {
            return if (this) {
                1.toByte()
            } else {
                0.toByte()
            }
        }
    }

    fun isGrantedOrIgnored(context: Context, preferences: SharedPreferences): Boolean {
        val granted = ContextCompat.checkSelfPermission(
            context,
            permission.getPermissionString()
        ) == PackageManager.PERMISSION_GRANTED
        return if (granted) {
            true
        } else {
            isIgnored(preferences)
        }
    }

    private fun isIgnored(preferences: SharedPreferences): Boolean {
        return if (permissionIgnorePreference != null) {
            preferences.getBoolean(permissionIgnorePreference, false)
        } else {
            false
        }
    }
}

private fun permissionRequestNeeded(
    context: Context,
    permissionRequests: List<PermissionRequest>
): Boolean {
    val preferences = PreferenceManager.getDefaultSharedPreferences(context)
    return !permissionRequests.all { it.isGrantedOrIgnored(context, preferences) }
}
