package ch.threema.app.reset

import org.koin.core.module.dsl.factoryOf
import org.koin.dsl.module

val resetFeatureModule = module {
    factoryOf(::ResetAppTask)
    factoryOf(::DeleteAllContactsHelper)
    factoryOf(::ResetAppTaskJavaCompat)
}
