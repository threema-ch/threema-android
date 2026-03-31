package ch.threema.app.home.usecases

import android.content.Context
import ch.threema.app.AppConstants.THREEMA_CHANNEL_IDENTITY
import ch.threema.app.services.ContactService
import ch.threema.app.services.MessageService
import ch.threema.app.utils.ConfigUtils
import ch.threema.base.utils.getThreemaLogger
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay

private val logger = getThreemaLogger("SetUpThreemaChannelUseCase")

class SetUpThreemaChannelUseCase(
    private val appContext: Context,
    private val messageService: MessageService,
    private val contactService: ContactService,
) {
    suspend fun call() {
        try {
            val threemaChannelModel = contactService.getByIdentity(THREEMA_CHANNEL_IDENTITY)
            if (threemaChannelModel == null) {
                logger.error("Threema channel contact model does not exist")
                return
            }

            val receiver = contactService.createReceiver(threemaChannelModel)
            if (!shouldUseGerman()) {
                delay(1000.milliseconds)
                messageService.sendText("en", receiver)
                delay(500.milliseconds)
            }
            delay(1000.milliseconds)
            messageService.sendText(START_NEWS_COMMAND, receiver)
            delay(1500.milliseconds)
            messageService.sendText(
                if (ConfigUtils.isWorkBuild()) WORK_COMMAND else START_ANDROID_COMMAND,
                receiver,
            )
            delay(1500.milliseconds)
            messageService.sendText(INFO_COMMAND, receiver)
        } catch (e: Exception) {
            logger.error("Failed to set up threema channel", e)
        }
    }

    private fun shouldUseGerman(): Boolean {
        val localeList = appContext.resources.configuration.locales
        for (i in 0 until localeList.size()) {
            val locale = localeList[i]
            val language = locale.language
            if (language.startsWith("de") || language.startsWith("gsw")) {
                return true
            }
        }
        return false
    }

    companion object {
        private const val INFO_COMMAND = "Info"
        private const val START_NEWS_COMMAND = "Start News"
        private const val START_ANDROID_COMMAND = "Start Android"
        private const val WORK_COMMAND = "Start Threema Work"
    }
}
