package ch.threema.app.usecases

import ch.threema.app.usecases.avatar.GetAndPrepareAvatarUseCase
import ch.threema.app.usecases.contacts.GetPersonUseCase
import ch.threema.app.usecases.conversations.WatchAvatarIterationsUseCase
import ch.threema.app.usecases.groups.GetGroupDisplayNameUseCase
import ch.threema.app.usecases.groups.WatchGroupCallsUseCase
import org.koin.core.module.dsl.factoryOf
import org.koin.dsl.module

val useCasesModule = module {
    factoryOf(::ExportDebugLogUseCase)
    factoryOf(::GetAndPrepareAvatarUseCase)
    factoryOf(::GetDebugMetaDataUseCase)
    factoryOf(::GetGroupDisplayNameUseCase)
    factoryOf(::GetPersonUseCase)
    factoryOf(::OverrideOneTimeHintsUseCase)
    factoryOf(::ShareDebugLogUseCase)
    factoryOf(::WatchAvatarIterationsUseCase)
    factoryOf(::WatchGroupCallsUseCase)
    factoryOf(::WatchTypingIdentitiesUseCase)
}
