package ch.threema.app.home

import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val homeFeatureModule = module {
    viewModelOf(::HomeViewModel)
    factoryOf(::MasterKeyPersisting)
}
