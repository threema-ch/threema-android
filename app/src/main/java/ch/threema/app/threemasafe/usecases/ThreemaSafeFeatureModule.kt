package ch.threema.app.threemasafe.usecases

import org.koin.core.module.dsl.factoryOf
import org.koin.dsl.module

val threemaSafeFeatureModule = module {
    factoryOf(::CheckBadPasswordUseCase)
}
