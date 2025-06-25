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

package ch.threema.app.drafts

import ch.threema.app.ThreemaApplication
import ch.threema.app.preference.service.PreferenceService
import ch.threema.base.utils.LoggingUtil
import ch.threema.storage.models.AbstractMessageModel
import java.util.HashMap

private val logger = LoggingUtil.getThreemaLogger("DraftManager")

object DraftManager {

    private var messageDrafts = mutableMapOf<String, String>()
    private var quoteDrafts = mutableMapOf<String, String>()

    @JvmStatic
    fun getMessageDraft(chatId: String?): String? = synchronized(this) {
        messageDrafts[chatId]
    }

    @JvmStatic
    fun getQuoteDraft(chatId: String?): String? = synchronized(this) {
        quoteDrafts[chatId]
    }

    @JvmStatic
    fun putMessageDraft(chatId: String, value: CharSequence?, quotedMessageModel: AbstractMessageModel?): Unit = synchronized(this) {
        if (value.isNullOrBlank()) {
            messageDrafts.remove(chatId)
            quoteDrafts.remove(chatId)
        } else {
            messageDrafts[chatId] = value.toString()
            val apiMessageId = quotedMessageModel?.apiMessageId
            if (apiMessageId != null) {
                quoteDrafts[chatId] = apiMessageId
            } else {
                quoteDrafts.remove(chatId)
            }
        }

        try {
            val preferenceService = ThreemaApplication.requireServiceManager().preferenceService
            preferenceService.messageDrafts = HashMap(messageDrafts)
            preferenceService.quoteDrafts = HashMap(quoteDrafts)
        } catch (e: Exception) {
            logger.error("Failed to store message drafts", e)
        }
    }

    @JvmStatic
    fun retrieveMessageDraftsFromStorage(preferenceService: PreferenceService): Unit = synchronized(this) {
        try {
            messageDrafts = preferenceService.messageDrafts.toMutableMap()
            quoteDrafts = preferenceService.quoteDrafts.toMutableMap()
        } catch (e: Exception) {
            logger.error("Failed to retrieve message drafts from storage", e)
        }
    }
}
