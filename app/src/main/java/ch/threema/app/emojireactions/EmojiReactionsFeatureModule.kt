package ch.threema.app.emojireactions

import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val emojiReactionsFeatureModule = module {
    viewModel { parameters ->
        EmojiReactionsViewModel(
            emojiReactionsRepository = get(),
            messageService = get(),
            reactionMessageIdentifier = parameters.get(),
        )
    }
}
