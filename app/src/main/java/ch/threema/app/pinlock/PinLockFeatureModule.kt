package ch.threema.app.pinlock

import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val pinLockFeatureModule = module {
    viewModel { parameters ->
        PinLockViewModel(
            lockAppService = get(),
            preferenceService = get(),
            timeProvider = get(),
            isCheckOnly = parameters.get(),
        )
    }
}
