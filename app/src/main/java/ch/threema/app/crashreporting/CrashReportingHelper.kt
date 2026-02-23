package ch.threema.app.crashreporting

import ch.threema.app.preference.service.PreferenceService
import ch.threema.base.SessionScoped

@SessionScoped
class CrashReportingHelper(
    private val preferenceService: PreferenceService,
    private val exceptionRecordStore: ExceptionRecordStore,
) {
    fun shouldPrompt(): Boolean =
        preferenceService.crashReportingState == PreferenceService.CrashReportingState.ALWAYS_ASK &&
            exceptionRecordStore.hasRecords()

    fun deleteRecords() {
        exceptionRecordStore.deleteRecords()
    }
}
