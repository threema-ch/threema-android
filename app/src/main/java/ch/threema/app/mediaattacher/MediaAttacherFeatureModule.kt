package ch.threema.app.mediaattacher

import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val mediaAttacherFeatureModule = module {
    viewModelOf(::EditSendContactViewModel)
    viewModelOf(::MediaAttachViewModel)
}
