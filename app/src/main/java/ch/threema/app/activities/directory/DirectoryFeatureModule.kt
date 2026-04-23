package ch.threema.app.activities.directory

import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val directoryFeatureModule = module {
    viewModelOf(::DirectoryViewModel)
}
