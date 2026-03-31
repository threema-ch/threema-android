package ch.threema.app.workers

import org.koin.core.module.dsl.factoryOf
import org.koin.dsl.module

val workersFeatureModule = module {
    factoryOf(AutoDeleteWorker::Scheduler)
}
