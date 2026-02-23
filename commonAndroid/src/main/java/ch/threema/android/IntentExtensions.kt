package ch.threema.android

import android.content.Intent
import android.os.Build
import java.io.Serializable

@Suppress("DEPRECATION")
inline fun <reified T : Any?> Intent.getParcelable(key: String): T? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) getParcelableExtra(key, T::class.java) else getParcelableExtra(key)

@Suppress("DEPRECATION")
inline fun <reified T : Serializable?> Intent.getSerializable(key: String): Serializable? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) getSerializableExtra(key, T::class.java) else getSerializableExtra(key)

/**
 * Get the int value of the extras or null if no element with [key] is available. Note that 0 is returned in case there is a value for the [key] but
 * it is not an int.
 */
fun Intent.getIntOrNull(key: String): Int? =
    if (extras?.containsKey(key) != true) {
        null
    } else {
        extras?.getInt(key)
    }
