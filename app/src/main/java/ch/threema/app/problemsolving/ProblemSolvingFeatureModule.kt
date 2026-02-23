package ch.threema.app.problemsolving

import org.koin.core.module.dsl.factoryOf
import org.koin.dsl.module

val problemSolvingFeatureModule = module {
    factoryOf(::GetProblemsUseCase)
}
