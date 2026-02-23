package ch.threema.common

import java.security.SecureRandom
import org.koin.dsl.module

val commonModule = module {
    factory<DispatcherProvider> { DispatcherProvider.default }
    factory<TimeProvider> { TimeProvider.default }
    factory<SecureRandom> { secureRandom() }
    factory<UUIDGenerator> { UUIDGenerator.default }
}
