package ch.threema.app.mediagallery

import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val mediaGalleryFeatureModule = module {
    factoryOf(::MediaGalleryRepository)
    viewModel { parameters ->
        MediaGalleryViewModel(
            mediaGalleryRepository = get(),
            preferenceService = get(),
            messageReceiver = parameters.get(),
        )
    }
}
