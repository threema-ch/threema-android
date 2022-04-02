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

package ch.threema.testutils

import org.mockito.ArgumentMatcher

/**
 * Compare equality using toString().
 *
 * This can be used if classes do not implement `.equals()` properly, but do provide a
 * detailed `.toString()` implementation (for example [org.json.JSONObject]).
 */
class ToStringEqualityArgumentMatcher<T>(thisObject: T) : ArgumentMatcher<T> {
    private val thisObjectString: String = thisObject.toString()

    override fun matches(argument: T): Boolean {
        return thisObjectString == argument.toString()
    }
}
