package ch.threema.data.repositories

import ch.threema.app.managers.CoreServiceManager
import ch.threema.data.ModelCache
import ch.threema.data.storage.EditHistoryDaoImpl
import ch.threema.data.storage.EmojiReactionsDaoImpl
import ch.threema.data.storage.SqliteDatabaseBackend

class ModelRepositories(
    coreServiceManager: CoreServiceManager,
) {
    private val cache = ModelCache()
    private val databaseBackend = SqliteDatabaseBackend(coreServiceManager.databaseService)
    private val editHistoryDao = EditHistoryDaoImpl(coreServiceManager.databaseService)
    private val emojiReactionDao = EmojiReactionsDaoImpl(coreServiceManager.databaseService)

    val contacts = ContactModelRepository(cache.contacts, databaseBackend, coreServiceManager)
    val groups = GroupModelRepository(cache.groups, databaseBackend, coreServiceManager)
    val editHistory = EditHistoryRepository(cache.editHistory, editHistoryDao, coreServiceManager)
    val emojiReaction = EmojiReactionsRepository(cache.emojiReaction, emojiReactionDao, coreServiceManager)
}
