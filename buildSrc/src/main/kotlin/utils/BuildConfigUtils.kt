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

package utils

import com.android.build.api.dsl.VariantDimension

fun VariantDimension.intBuildConfigField(name: String, value: Int) {
    buildConfigField("int", name, value.toString())
}

fun VariantDimension.stringBuildConfigField(name: String, value: String?) {
    buildConfigField("String", name, if (value != null) "\"$value\"" else "null")
}

fun VariantDimension.booleanBuildConfigField(name: String, value: Boolean) {
    buildConfigField("boolean", name, value.toString())
}

fun VariantDimension.stringArrayBuildConfigField(name: String, value: Array<String>) {
    buildConfigField(
        "String[]",
        name,
        "new String[] {${value.joinToString(separator = ", ") { "\"$it\"" }}}",
    )
}

fun VariantDimension.intArrayBuildConfigField(name: String, value: IntArray) {
    buildConfigField("int[]", name, "{${value.joinToString(separator = ", ")}}")
}

fun VariantDimension.byteArrayBuildConfigField(name: String, value: ByteArray?) {
    buildConfigField(
        "byte[]",
        name,
        if (value != null) {
            "new byte[] {${value.joinToString(separator = ", ") { "(byte) 0x%02x".format(it) }}}"
        } else {
            "null"
        },
    )
}
