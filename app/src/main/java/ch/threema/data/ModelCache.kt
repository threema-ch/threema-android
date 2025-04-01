/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2024 Threema GmbH
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

package ch.threema.data

import ch.threema.data.models.BaseModel
import ch.threema.data.models.ContactModel
import ch.threema.data.models.EditHistoryListModel
import ch.threema.data.models.EmojiReactionsModel
import ch.threema.data.models.GroupIdentity
import ch.threema.data.models.GroupModel
import ch.threema.data.repositories.EmojiReactionsRepository

/**
 * The model cache holds a [ModelTypeCache] for every model type.
 *
 * Note: This class should be initialized only once in the application
 * (except for use cases like testing).
 */
class ModelCache {
    // Contacts are identified by their identity
    val contacts = ModelTypeCache<String, ContactModel>()

    // Groups are identified by their group identity (creator identity and group id)
    val groups = ModelTypeCache<GroupIdentity, GroupModel>()

    // Edit history entries are identified by their reference to a message's uid
    val editHistory = ModelTypeCache<String, EditHistoryListModel>()

    // Emoji reactions are uniquely identified by a composition of the message's id (int) and type
    val emojiReaction =
        ModelTypeCache<EmojiReactionsRepository.ReactionMessageIdentifier, EmojiReactionsModel>()
}

/**
 * The model type cache holds models of a certain type. It ensures that every model
 * is instantiated only once.
 *
 * Internally, it uses a [WeakValueMap], so the values are not prevented from being
 * garbage collected by the cache.
 */
class ModelTypeCache<TIdentifier, TModel : BaseModel<*, *>> {
    private val map = WeakValueMap<TIdentifier, TModel>()

    /**
     * Return the cached model with the specified [identifier].
     */
    fun get(identifier: TIdentifier): TModel? = this.map.get(identifier)

    /**
     * Return the cached model with the specified [identifier].
     *
     * If it cannot be found, create the model using the [miss] function, cache it
     * and return it.
     */
    fun getOrCreate(identifier: TIdentifier, miss: () -> TModel?): TModel? =
        this.map.getOrCreate(identifier, miss)

    /**
     * Remove the model with the specified [identifier] from the cache and return it.
     */
    fun remove(identifier: TIdentifier): TModel? = this.map.remove(identifier)
}
