package ch.threema.app.voicemessage

import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val voiceMessageFeatureModule = module {
    viewModel { params ->
        VoiceRecorderViewModel(
            messageReceiver = params.get(),
            application = get(),
            fileService = get(),
            preferenceService = get(),
            messageService = get(),
        )
    }
}
