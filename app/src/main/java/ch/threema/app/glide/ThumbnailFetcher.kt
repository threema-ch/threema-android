/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2022-2024 Threema GmbH
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

package ch.threema.app.glide

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import ch.threema.app.R
import ch.threema.app.ThreemaApplication
import ch.threema.app.services.FileService
import ch.threema.app.utils.ConfigUtils
import ch.threema.storage.models.AbstractMessageModel
import com.bumptech.glide.Priority
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.data.DataFetcher

/**
 * This class is used to get the thumbnails from the database or create placeholders. The results of the loaded bitmaps will be cached by glide (if possible).
 */
class ThumbnailFetcher(
    private val context: Context,
    private val messageModel: AbstractMessageModel,
    ) : DataFetcher<Bitmap> {

    private val fileService: FileService? by lazy { ThreemaApplication.getServiceManager()?.fileService }

    override fun loadData(priority: Priority, callback: DataFetcher.DataCallback<in Bitmap>) {
        val messageModel = messageModel

        val thumbnail: Bitmap? = try {
            fileService?.getMessageThumbnailBitmap(messageModel, null)
        } catch (e: java.lang.Exception) {
            null
        }

        callback.onDataReady(thumbnail)
    }

    override fun cleanup() {
        // Nothing to cleanup
    }

    override fun cancel() {
        // Nothing to do here
    }

    override fun getDataClass(): Class<Bitmap> = Bitmap::class.java

    override fun getDataSource(): DataSource = DataSource.LOCAL
}
