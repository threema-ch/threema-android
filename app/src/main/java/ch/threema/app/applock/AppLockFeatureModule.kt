package ch.threema.app.applock

import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val appLockFeatureModule = module {
    singleOf(::AppLockUtil)
}
