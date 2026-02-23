package ch.threema.app.webclient

import ch.threema.app.webclient.activities.SessionsViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val webclientFeatureModule = module {
    viewModelOf(::SessionsViewModel)
}
