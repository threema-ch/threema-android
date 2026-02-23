package ch.threema.app.voip

import ch.threema.app.voip.viewmodel.GroupCallViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val voipFeatureModule = module {
    viewModelOf(::GroupCallViewModel)
}
