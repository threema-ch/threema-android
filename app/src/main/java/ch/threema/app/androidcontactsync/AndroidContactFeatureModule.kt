package ch.threema.app.androidcontactsync

import ch.threema.app.androidcontactsync.read.AndroidContactReader
import ch.threema.app.androidcontactsync.read.LookupInfoReader
import ch.threema.app.androidcontactsync.read.RawContactCursorProvider
import ch.threema.app.androidcontactsync.read.RawContactReader
import ch.threema.app.androidcontactsync.usecases.GetAndroidContactNameUseCase
import ch.threema.app.androidcontactsync.usecases.GetRawContactNameUseCase
import ch.threema.app.androidcontactsync.usecases.UpdateContactNameUseCase
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val androidContactFeatureModule = module {
    singleOf(::AndroidContactChangeMonitor)
    singleOf(::AndroidContactReader)
    singleOf(::GetAndroidContactNameUseCase)
    singleOf(::GetRawContactNameUseCase)
    singleOf(::LookupInfoReader)
    singleOf(::RawContactCursorProvider)
    singleOf(::RawContactReader)
    singleOf(::UpdateContactNameUseCase)
}
