package ch.threema.app.fragments.composemessage

import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val composeMessageFeatureModule = module {
    viewModelOf(::ComposeMessageViewModel)

    factory { parameters ->
        ComposeMessageFragmentUtils(
            appContext = get(),
            messageService = get(),
            userService = get(),
            contactService = get(),
            preferenceService = get(),
            emojiReactionsRepository = get(),
            dispatcherProvider = get(),
            fragment = parameters.get(),
            receiver = parameters.get(),
            isGroupChat = parameters.get(),
        )
    }
}
