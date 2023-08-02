/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2023 Threema GmbH
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

package ch.threema.app.adapters

import android.annotation.SuppressLint
import android.os.AsyncTask
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import ch.threema.app.ThreemaApplication
import ch.threema.app.activities.MediaGalleryActivity
import ch.threema.app.activities.MediaGalleryActivity.*
import ch.threema.app.messagereceiver.MessageReceiver
import ch.threema.app.services.MessageService
import ch.threema.app.services.MessageService.MessageFilter
import ch.threema.base.ThreemaException
import ch.threema.storage.models.AbstractMessageModel
import ch.threema.storage.models.MessageType
import ch.threema.storage.models.data.MessageContentsType

class MediaGalleryRepository {
    private var abstractMessageModels: MutableLiveData<List<AbstractMessageModel?>?>? = null
    private var messageService: MessageService? = null
    private var messageReceiver: MessageReceiver<*>? = null
    private var filter: IntArray? = null

    init {
        val serviceManager = ThreemaApplication.getServiceManager()
        if (serviceManager != null) {
            messageService = null
            try {
                messageService = serviceManager.messageService
                abstractMessageModels = object : MutableLiveData<List<AbstractMessageModel?>?>() {
                    override fun getValue(): List<AbstractMessageModel?>? {
                        return messageReceiver?.loadMessages(getMessageFilter())
                    }
                }
            } catch (e: ThreemaException) {
                //
            }
        }
    }

    fun getAbstractMessageModels(): LiveData<List<AbstractMessageModel?>?>? {
        return abstractMessageModels
    }

    @SuppressLint("StaticFieldLeak")
    fun onDataChanged() {
        object : AsyncTask<String?, Void?, Void?>() {
            @Deprecated("Deprecated in Java")
            override fun doInBackground(vararg params: String?): Void? {
                abstractMessageModels?.postValue(
                    messageReceiver?.loadMessages(getMessageFilter())
                )
                return null
            }
        }.execute()
    }

    private fun getMessageFilter() : MessageFilter {
        return object : MessageFilter {
            override fun getPageSize(): Long { return 0 }
            override fun getPageReferenceId(): Int { return 0 }
            override fun withStatusMessages(): Boolean { return false }
            override fun withUnsaved(): Boolean { return true }
            override fun onlyUnread(): Boolean { return false }
            override fun onlyDownloaded(): Boolean { return false }
            override fun types(): Array<MessageType>? { return null }
            override fun contentTypes(): IntArray { return getContentTypes() }
        }
    }

    fun getContentTypes() : IntArray {
        if (filter == null) {
            return contentTypes
        }
        return filter as IntArray
    }

    fun setFilter(contentTypes: IntArray?) {
        filter = contentTypes
    }

    fun setMessageReceiver(messageReceiver: MessageReceiver<*>) {
        this.messageReceiver = messageReceiver
    }
}

