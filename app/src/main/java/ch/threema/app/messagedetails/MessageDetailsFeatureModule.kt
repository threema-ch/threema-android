package ch.threema.app.messagedetails

import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val messageDetailsFeatureModule = module {
    viewModel { parameters ->
        MessageDetailsViewModel(
            messageService = get(),
            emojiReactionsRepository = get(),
            preferenceService = get(),
            messageId = parameters[0],
            messageType = parameters[1],
        )
    }
}
