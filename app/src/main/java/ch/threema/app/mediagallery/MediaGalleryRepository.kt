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

package ch.threema.app.mediagallery

import android.os.AsyncTask
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import ch.threema.app.messagereceiver.MessageReceiver
import ch.threema.app.services.MessageService
import ch.threema.storage.models.AbstractMessageModel
import ch.threema.storage.models.MessageType

class MediaGalleryRepository {

    private val _messages: MutableLiveData<List<AbstractMessageModel?>?> = MutableLiveData(null)
    val messages: LiveData<List<AbstractMessageModel?>?> = _messages

    /**
     *  Loads the message models from database where the content type is in [contentTypes] and publish the results
     *  through [messages] observable.
     *
     *  @param contentTypes An empty set will always lead to zero results
     */
    fun loadMessages(messageReceiver: MessageReceiver<*>, contentTypes: Set<Int>) {
        if (contentTypes.isEmpty()) {
            _messages.value = emptyList<AbstractMessageModel>()
            return
        }

        @Suppress("DEPRECATION", "StaticFieldLeak")
        object : AsyncTask<String?, Void?, Void?>() {
            override fun doInBackground(vararg params: String?): Void? {
                _messages.postValue(
                    messageReceiver.loadMessages(
                        buildMessageFilter(
                            contentTypes = contentTypes,
                        ),
                    ),
                )
                return null
            }
        }.execute()
    }

    /**
     * @param contentTypes This filter will only query message models that are of a type contained in this set.
     *                     An empty set is an invalid value, as it would always lead to zero message results.
     */
    private fun buildMessageFilter(contentTypes: Set<Int>): MessageService.MessageFilter {
        require(contentTypes.isNotEmpty()) { "Must at least query messages of one specified content type" }
        return object : MessageService.MessageFilter {
            override fun getPageSize(): Long = 0
            override fun getPageReferenceId(): Int = 0
            override fun withStatusMessages(): Boolean = false
            override fun withUnsaved(): Boolean = true
            override fun onlyUnread(): Boolean = false
            override fun onlyDownloaded(): Boolean = false
            override fun types(): Array<MessageType>? = null
            override fun contentTypes(): IntArray? = contentTypes.toIntArray()
            override fun displayTags(): IntArray? = null
        }
    }
}
