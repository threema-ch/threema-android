package ch.threema.app.archive

import ch.threema.app.usecases.conversations.WatchArchivedConversationsUseCase
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val archiveFeatureModule = module {
    viewModelOf(::ArchiveViewModel)
    factoryOf(::WatchArchivedConversationsUseCase)
}
