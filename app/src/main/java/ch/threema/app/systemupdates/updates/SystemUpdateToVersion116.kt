package ch.threema.app.systemupdates.updates

import ch.threema.app.services.ConversationService
import ch.threema.app.utils.ShortcutUtil
import ch.threema.base.utils.getThreemaLogger
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

private val logger = getThreemaLogger("SystemUpdateToVersion116")

class SystemUpdateToVersion116 : SystemUpdate, KoinComponent {

    private val conversationService: ConversationService by inject()

    override fun run() {
        try {
            conversationService.getAll(false).forEach { conversation ->
                ShortcutUtil.updatePinnedShortcut(conversation.messageReceiver)
            }
        } catch (e: Exception) {
            logger.warn("Failed to update pinned shortcuts", e)
        }
    }

    override fun getVersion() = VERSION

    override fun getDescription() = "update icons of all pinned shortcuts"

    companion object {
        const val VERSION = 116
    }
}
