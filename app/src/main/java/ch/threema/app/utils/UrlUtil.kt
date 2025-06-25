/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2022-2025 Threema GmbH
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

import android.net.Uri
import java.util.regex.Pattern

object UrlUtil {
    private val ascii: Pattern = Pattern.compile("^[\\x00-\\x7F]*$")
    private val HYPHEN_MINUS_CHARACTER = Char(0x002D)

    /**
     * Characters that are excluded from the identifier check.
     */
    private val nonIdentifierExceptions = setOf(HYPHEN_MINUS_CHARACTER)

    /**
     * Sets of scripts that may be mixed without a warning.
     *
     * For example, Hiragana, Katakana, Han and Latin are frequently mixed, this is OK.
     */
    private val validUnicodeScriptSets = listOf<Set<UnicodeUtil.UnicodeScript>>(
        HashSet<UnicodeUtil.UnicodeScript>().apply {
            add(UnicodeUtil.UnicodeScript.LATIN)
            add(UnicodeUtil.UnicodeScript.HAN)
            add(UnicodeUtil.UnicodeScript.HIRAGANA)
            add(UnicodeUtil.UnicodeScript.KATAKANA)
        },
        HashSet<UnicodeUtil.UnicodeScript>().apply {
            add(UnicodeUtil.UnicodeScript.LATIN)
            add(UnicodeUtil.UnicodeScript.HAN)
            add(UnicodeUtil.UnicodeScript.BOPOMOFO)
        },
        HashSet<UnicodeUtil.UnicodeScript>().apply {
            add(UnicodeUtil.UnicodeScript.LATIN)
            add(UnicodeUtil.UnicodeScript.HAN)
            add(UnicodeUtil.UnicodeScript.HANGUL)
        },
    )

    /**
     * Checks if the hostname of a given Uri consists of a mix of different unicode scripts implying
     * a possible IDNA homograph attack (https://en.wikipedia.org/wiki/IDN_homograph_attack) through
     * similar looking characters.
     *
     * Additionally, all the characters of the hostname must be valid unicode identifiers per UTS 39.
     * The script mixing rules apply for each domain label (component) separately.
     *
     * This is partially inspired by validation rules in Chromium and Firefox
     * @see <a href="https://chromium.googlesource.com/chromium/src/+/main/docs/idn.md">Chromium IDN</a>
     * @see <a href="https://wiki.mozilla.org/IDN_Display_Algorithm#Algorithm">Mozilla IDN Display Algorithm</a>
     *
     * @param uri URI to check
     * @return true if URI seems safe, false if it might be problematic
     */
    @JvmStatic
    fun isSafeUri(uri: Uri): Boolean {
        val host = uri.host

        if (host.isNullOrEmpty()) {
            return false
        }

        val components = host.split(".").filter { it.isNotEmpty() }

        if (components.isEmpty()) {
            return false
        }

        return components.map(::isLegalComponent).none { !it }
    }

    /**
     * Checks that the given component consists of only ASCII or allowed mixable scripts.
     */
    private fun isLegalComponent(component: String): Boolean {
        // Skip further tests if the component consists of ASCII only
        if (ascii.matcher(component).matches()) {
            return true
        }

        // Check that every character belongs to the allowed identifiers per UTS 39
        if (component.filter { !nonIdentifierExceptions.contains(it) }
                .any { !Character.isUnicodeIdentifierPart(it) }
        ) {
            return false
        }

        // Check that script mixing is only allowed based on the "Highly Restrictive" profile of UTS 39
        val scripts = component.map(UnicodeUtil::getScript)
            .filter { it != UnicodeUtil.UnicodeScript.COMMON && it != UnicodeUtil.UnicodeScript.INHERITED }
            .toSet()

        if (scripts.size >= 2 && !validUnicodeScriptSets.any { scripts.subtract(it).isEmpty() }) {
            return false
        }

        return true
    }
}
