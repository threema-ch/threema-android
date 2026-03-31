package ch.threema.app.globalsearch

import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val globalSearchFeatureModule = module {
    viewModelOf(::GlobalSearchViewModel)
}
