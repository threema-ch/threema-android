package ch.threema.app.preference

import androidx.preference.DropDownPreference
import androidx.preference.Preference
import ch.threema.base.utils.getThreemaLogger
import kotlin.reflect.KClass

private val logger = getThreemaLogger("PreferenceExtensions")

fun Preference.onClick(action: () -> Unit) {
    setOnPreferenceClickListener {
        action()
        true
    }
}

fun Preference.onChange(action: () -> Unit) {
    onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, _ ->
        action()
        true
    }
}

inline fun <reified T : Any> Preference.onChange(noinline action: (newValue: T) -> Unit) {
    onChange(T::class, action)
}

fun <T : Any> Preference.onChange(valueType: KClass<T>, action: (newValue: T) -> Unit) {
    onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, newValue: Any? ->
        if (valueType.isInstance(newValue)) {
            @Suppress("UNCHECKED_CAST")
            action(newValue as T)
            true
        } else {
            logger.error("Preference.onChange failed, value of unexpected type: {}", newValue)
            false
        }
    }
}

/**
 * There is a bug in [DropDownPreference], where it can get stuck in an invalid state. If a value is selected but the change listener
 * returns false, indicating that the selection should not be applied, that option can then no longer be selected until a different option
 * has been selected. This method fixes this issue by briefly disabling the preference.
 */
fun DropDownPreference.refresh() {
    if (isEnabled) {
        isEnabled = false
        isEnabled = true
    }
}
