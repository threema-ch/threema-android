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
     *  Add the given [model] to this cache if it is not already present
     */
    fun putIfAbsent(identifier: TIdentifier, model: TModel) {
        if (get(identifier) == null) {
            this.map.put(identifier, model)
        }
    }

    /**
     * Remove the model with the specified [identifier] from the cache and return it.
     */
    fun remove(identifier: TIdentifier): TModel? = this.map.remove(identifier)
}
