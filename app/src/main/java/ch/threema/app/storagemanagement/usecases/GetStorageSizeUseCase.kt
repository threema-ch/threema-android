package ch.threema.app.storagemanagement.usecases

import ch.threema.app.files.AppDirectoryProvider
import ch.threema.app.services.MessageService
import ch.threema.app.utils.DispatcherProvider
import ch.threema.common.getTotalSize
import kotlinx.coroutines.withContext

class GetStorageSizeUseCase(
    private val appDirectoryProvider: AppDirectoryProvider,
    private val messageService: MessageService,
    private val dispatcherProvider: DispatcherProvider,
) {
    suspend fun call(): Result = withContext(dispatcherProvider.io) {
        Result(
            totalBytes = appDirectoryProvider.userFilesDirectory.totalSpace,
            freeBytes = appDirectoryProvider.userFilesDirectory.usableSpace,
            usedBytes = appDirectoryProvider.userFilesDirectory.getTotalSize() +
                appDirectoryProvider.legacyUserFilesDirectory.getTotalSize(),
            messageCount = messageService.getTotalMessageCount(),
        )
    }

    data class Result(
        val totalBytes: Long,
        val freeBytes: Long,
        val usedBytes: Long,
        val messageCount: Long,
    )
}
