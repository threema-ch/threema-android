package ch.threema.app.logging

import ch.threema.app.BuildConfig
import ch.threema.logging.backend.DebugLogFileManager
import org.koin.core.module.dsl.factoryOf
import org.koin.dsl.module

val loggingFeatureModule = module {
    factoryOf(::AppVersionLogger)
    factoryOf(::DebugLogHelper)
    factoryOf(::ExitReasonLogger)
    factoryOf(::DebugLogFileManager)

    factory {
        AppVersionHistoryManager(
            appContext = get(),
            timeProvider = get(),
            currentVersionName = BuildConfig.VERSION_NAME,
            currentVersionCode = BuildConfig.DEFAULT_VERSION_CODE,
        )
    }
}
