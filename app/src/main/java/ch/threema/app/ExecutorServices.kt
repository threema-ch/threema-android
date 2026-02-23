package ch.threema.app

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

object ExecutorServices {
    @JvmStatic
    val sendMessageExecutorService: ExecutorService = Executors.newFixedThreadPool(4)

    @JvmStatic
    val sendMessageSingleThreadExecutorService: ExecutorService = Executors.newSingleThreadExecutor()

    @JvmStatic
    val voiceMessageThumbnailExecutorService: ExecutorService = Executors.newFixedThreadPool(4)
}
