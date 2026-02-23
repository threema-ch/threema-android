package ch.threema.app.compose.edithistory

import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val editHistoryFeatureModule = module {
    viewModel { parameters ->
        EditHistoryViewModel(
            editHistoryRepository = get(),
            messageUid = parameters.get(),
        )
    }
}
