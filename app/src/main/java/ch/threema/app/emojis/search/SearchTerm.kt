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

package ch.threema.app.emojis.search

import androidx.room.*

@Entity(
    primaryKeys = ["emoji_sequence", "language", "term"],
    foreignKeys = [
        ForeignKey(
            entity = Emoji::class,
            parentColumns = ["sequence"],
            childColumns = ["emoji_sequence"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class SearchTerm(
    @ColumnInfo(name = "emoji_sequence") val emojiSequence: String,
    val language: String,
    val term: String
)


