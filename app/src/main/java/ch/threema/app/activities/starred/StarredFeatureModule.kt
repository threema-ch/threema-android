package ch.threema.app.activities.starred

import ch.threema.app.usecases.GetStarredMessagesUseCase
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val starredFeatureModule = module {
    viewModelOf(::StarredMessagesViewModel)
    factoryOf(::GetStarredMessagesUseCase)
}
