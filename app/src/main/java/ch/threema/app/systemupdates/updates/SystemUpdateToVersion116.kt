/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2025 Threema GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License, version 3,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

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
