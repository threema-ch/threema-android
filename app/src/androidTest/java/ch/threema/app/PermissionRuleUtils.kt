package ch.threema.app

import android.Manifest
import android.os.Build
import androidx.test.rule.GrantPermissionRule

/**
 * Get the permission rule for the notification permission. This method can be used to only grant
 * the permission for Android TIRAMISU and newer.
 */
fun getNotificationPermissionRule(): GrantPermissionRule {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)
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
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
        )
    }
}

/**
 * Get the permission to read images and videos from android 13, and read/write external storage on
 * older android versions.
 */
fun getReadImagesVideosPermissionRule(): GrantPermissionRule {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        GrantPermissionRule.grant(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
        )
    } else {
        GrantPermissionRule.grant(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
        )
    }
}

/**
 * Get the microphone permission rule.
 */
fun getMicrophonePermissionRule(): GrantPermissionRule =
    GrantPermissionRule.grant(Manifest.permission.RECORD_AUDIO)

/**
 * Get [Manifest.permission.BLUETOOTH] or [Manifest.permission.BLUETOOTH_CONNECT] permission rule
 * depending on the android version.
 */
fun getBluetoothPermissionRule(): GrantPermissionRule {
    return GrantPermissionRule.grant(
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Manifest.permission.BLUETOOTH_CONNECT
        } else {
            Manifest.permission.BLUETOOTH
        },
    )
}

/**
 * Get the [Manifest.permission.READ_PHONE_STATE] permission rule on android 12 or higher and an
 * empty permission rule on older versions.
 */
fun getReadPhoneStatePermissionRule(): GrantPermissionRule {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        GrantPermissionRule.grant(Manifest.permission.READ_PHONE_STATE)
    } else {
        GrantPermissionRule.grant()
    }
}
