package ch.threema.app.apptaskexecutor

import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val appTaskExecutorFeatureModule = module {
    singleOf(::AppTaskExecutor)
    factoryOf(::AppTaskPersistenceProvider)
}
