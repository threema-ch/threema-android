package ch.threema.app.identitylinks

import org.koin.core.module.dsl.factoryOf
import org.koin.dsl.module

val identityLinkFeatureModule = module {
    factoryOf(::VerifyMobileNumberUseCase)
}
