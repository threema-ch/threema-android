package ch.threema.app.archive

import ch.threema.app.usecases.WatchGroupCallsUseCase
import ch.threema.app.usecases.WatchTypingIdentitiesUseCase
import ch.threema.app.usecases.conversations.WatchArchivedConversationsUseCase
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val archiveFeatureModule = module {
    viewModelOf(::ArchiveViewModel)
    factoryOf(::WatchArchivedConversationsUseCase)
    factoryOf(::WatchGroupCallsUseCase)
    factoryOf(::WatchTypingIdentitiesUseCase)
}
