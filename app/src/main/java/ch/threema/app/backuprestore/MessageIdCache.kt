/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2024-2025 Threema GmbH
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

package ch.threema.app.backuprestore

import ch.threema.base.utils.LoggingUtil
import ch.threema.domain.models.MessageId

private val logger = LoggingUtil.getThreemaLogger("MessageIdCache")

/**
 * A cache for message ids as used in our database but can be retrieved by other infos (see MessageKey).
 * This is used when a data backup is restored, as we must not store database ids in a backup and must
 * therefore use another format for data that is related by the id.
 *
 * Note that this cache only retains a single entry. It is therefore only usable when the ids are
 * requested in a sorted manner (aka all entries referencing the same id are queried successively)
 * and must therefore not be computed over and over again.
 */
class MessageIdCache<K : MessageIdCache.MessageKey>(val computeIfAbsent: (key: K) -> Int) {
    private var entry: Pair<K, Int>? = null

    @Throws(NoSuchElementException::class)
    fun get(key: K): Int {
        val currentEntry = entry
        return if (currentEntry?.first?.equals(key) == true) {
            currentEntry.second
        } else {
            try {
                computeIfAbsent(key).also { computedValue ->
                    entry = key to computedValue
                }
            } catch (exception: Exception) {
                logger.warn("Could not compute value", exception)
                throw NoSuchElementException()
            }
        }
    }

    sealed interface MessageKey {
        val apiMessageId: String

        val messageId: MessageId
            get() = MessageId.fromString(apiMessageId)
    }

    data class ContactMessageKey(
        val contactIdentity: String,
        override val apiMessageId: String,
    ) : MessageKey

    data class GroupMessageKey(
        val apiGroupId: String,
        val groupCreatorIdentity: String,
        override val apiMessageId: String,
    ) : MessageKey
}
