package ch.threema.app.passphrase

import org.koin.core.module.dsl.factoryOf
import org.koin.dsl.module

val passphraseFeatureModule = module {
    factoryOf(::PassphraseStateMonitor)
}
