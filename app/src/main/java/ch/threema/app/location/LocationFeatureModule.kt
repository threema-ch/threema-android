package ch.threema.app.location

import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val locationFeatureModule = module {
    viewModelOf(::LocationAutocompleteViewModel)
    factoryOf(::PoiRepository)
}
