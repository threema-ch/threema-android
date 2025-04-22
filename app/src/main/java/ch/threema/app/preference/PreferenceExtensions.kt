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

import androidx.preference.Preference
import ch.threema.base.utils.LoggingUtil
import kotlin.reflect.KClass

private val logger = LoggingUtil.getThreemaLogger("PreferenceExtensions")

fun Preference.onClick(action: () -> Unit) {
    setOnPreferenceClickListener {
        action()
        true
    }
}

fun Preference.onChange(action: () -> Unit) {
    onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, newValue: Any? ->
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
