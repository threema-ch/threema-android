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

package ch.threema.base.utils

import java.time.Instant
import java.util.Date
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

fun now() = Date()

operator fun Date.minus(other: Date): Duration = (time - other.time).milliseconds

operator fun Date.plus(duration: Duration) = Date(time + duration.inWholeMilliseconds)

operator fun Date.minus(duration: Duration) = Date(time - duration.inWholeMilliseconds)

operator fun Instant.minus(other: Instant): Duration = (toEpochMilli() - other.toEpochMilli()).milliseconds

operator fun Instant.plus(duration: Duration): Instant = Instant.ofEpochMilli(toEpochMilli() + duration.inWholeMilliseconds)

operator fun Instant.minus(duration: Duration): Instant = Instant.ofEpochMilli(toEpochMilli() - duration.inWholeMilliseconds)
