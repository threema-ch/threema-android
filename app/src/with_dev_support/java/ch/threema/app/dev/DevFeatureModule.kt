package ch.threema.app.dev

import ch.threema.app.dev.preference.ContentCreator
import org.koin.core.module.dsl.factoryOf
import org.koin.dsl.module

val devFeatureModule = module {
    factoryOf(::ContentCreator)
}
