package ch.threema.app.protocolsteps

import ch.threema.app.processors.incomingcspmessage.workdelta.WorkSyncDeltaChangeDeterminationSteps
import org.koin.core.module.dsl.factoryOf
import org.koin.dsl.module

val protocolStepsModule = module {
    factoryOf(::IdentityBlockedSteps)
    factoryOf(::ValidContactsLookupSteps)
    factoryOf(::WorkSyncDeltaChangeDeterminationSteps)
}
