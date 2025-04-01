/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2019-2025 Threema GmbH
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

import org.junit.Assert
import kotlin.reflect.KClass

infix fun (() -> Any?).willThrow(throwableClass: KClass<out Throwable>) {
    Assert.assertThrows(throwableClass.java) {
        try {
            this()
        } catch (throwable: Throwable) {
            if (throwable::class == throwableClass) {
                println("PASSED ${throwable::class.java.simpleName}: ${throwable.message}")
            }
            throw throwable
        }
    }
}
