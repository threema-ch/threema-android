package ch.threema.app.files

import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val filesFeatureModule = module {
    singleOf(::AppDirectoryProvider)
    factoryOf(::AppLogoFileHandleProvider)
    factoryOf(::MessageFileHandleProvider)
    factoryOf(::GroupProfilePictureFileHandleProvider)
    factoryOf(::ProfilePictureFileHandleProvider)
    factoryOf(::WallpaperFileHandleProvider)
}
