package ch.threema.app.systemupdates.updates

import ch.threema.app.preference.service.PreferenceService
import ch.threema.app.services.ConversationService
import ch.threema.app.utils.ShortcutUtil
import ch.threema.base.utils.getThreemaLogger
import org.koin.core.component.inject

private val logger = getThreemaLogger("SystemUpdateToVersion124")

/**
 * Note: this exact update used to be SystemUpdateToVersion116, but since we need to rerun it,
 * it was renamed and its version number adjusted.
 */
class SystemUpdateToVersion124 : SystemUpdate {

    private val conversationService: ConversationService by inject()
    private val preferenceService: PreferenceService by inject()

    override fun run() {
        try {
            conversationService.getAll(false).forEach { conversation ->
                ShortcutUtil.updatePinnedShortcut(
                    conversation.messageReceiver,
                    preferenceService.getContactNameFormat(),
                )
            }
        } catch (e: Exception) {
            logger.warn("Failed to update pinned shortcuts", e)
        }
    }

    override val version = 124

    override fun getDescription() = "update icons of all pinned shortcuts"
}
