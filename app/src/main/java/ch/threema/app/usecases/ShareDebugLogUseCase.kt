package ch.threema.app.usecases

import android.content.Context
import ch.threema.app.utils.DispatcherProvider
import ch.threema.app.utils.ShareUtil
import kotlinx.coroutines.withContext

class ShareDebugLogUseCase(
    private val appContext: Context,
    private val exportDebugLogUseCase: ExportDebugLogUseCase,
    private val dispatcherProvider: DispatcherProvider,
) {
    suspend fun call() {
        val zipFile = exportDebugLogUseCase.call()
        withContext(dispatcherProvider.main) {
            ShareUtil.shareFile(appContext, zipFile, "debug_log.zip", "application/zip")
        }
    }
}
