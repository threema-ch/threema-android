package ch.threema.app.protocolsteps

import org.koin.core.module.dsl.factoryOf
import org.koin.dsl.module

val protocolStepsModule = module {
    factoryOf(::IdentityBlockedSteps)
    factoryOf(::ValidContactsLookupSteps)
}
