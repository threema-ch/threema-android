package ch.threema.app.camera

import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val cameraFeatureModule = module {
    viewModelOf(::CameraViewModel)
}
