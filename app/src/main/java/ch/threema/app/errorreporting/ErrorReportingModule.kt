package ch.threema.app.errorreporting

import android.os.Build
import ch.threema.app.BuildConfig
import ch.threema.app.BuildFlavor
import org.koin.core.module.dsl.factoryOf
import org.koin.dsl.module

val errorReportingModule = module {
    factoryOf(::ErrorReportingHelper)
    factoryOf(SendErrorReportWorker::Scheduler)
    factory {
        ErrorRecordStore(
            recordsDirectory = ErrorRecordStore.getRecordsDirectory(get()),
            timeProvider = get(),
            uuidGenerator = get(),
        )
    }
    factoryOf(::SentryService)
    factoryOf(::SentryIdProvider)
    factory {
        SentryService.Config(
            host = BuildConfig.SENTRY_HOST,
            projectId = BuildConfig.SENTRY_PROJECT_ID,
            publicApiKey = BuildConfig.SENTRY_PUBLIC_API_KEY,
        )
    }
    factory {
        SentryService.MetaInfo(
            androidSdkVersion = Build.VERSION.SDK_INT,
            appVersion = BuildConfig.VERSION_NAME,
            versionCode = BuildConfig.DEFAULT_VERSION_CODE,
            buildFlavor = BuildFlavor.current.fullDisplayName,
            deviceModel = Build.MODEL,
        )
    }
}
