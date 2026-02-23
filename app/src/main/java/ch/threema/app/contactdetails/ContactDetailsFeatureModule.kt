package ch.threema.app.contactdetails

import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val contactDetailsFeatureModule = module {
    viewModel { parameters ->
        ContactDetailViewModel(
            contactModelRepository = get(),
            identity = parameters.get(),
        )
    }
}
