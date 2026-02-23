package ch.threema.app.crashreporting

import org.koin.core.module.dsl.factoryOf
import org.koin.dsl.module

val crashReportingModule = module {
    factoryOf(::CrashReportingHelper)
    factory {
        ExceptionRecordStore(
            recordsDirectory = ExceptionRecordStore.getRecordsDirectory(get()),
            timeProvider = get(),
            uuidGenerator = get(),
        )
    }
}
