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

package ch.threema.app.fragments.mediaviews

import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import ch.threema.app.R
import ch.threema.base.utils.LoggingUtil
import pl.droidsonroids.gif.GifDrawable
import pl.droidsonroids.gif.GifImageView
import java.io.File
import java.io.IOException
import java.lang.ref.WeakReference

private val logger = LoggingUtil.getThreemaLogger("GifViewFragment")

/**
 * This fragment is used to show GIFs.
 */
class GifViewFragment: MediaViewFragment() {

    private lateinit var thumbnailImageView: WeakReference<ImageView>
    private lateinit var gifImageViewRef: WeakReference<GifImageView>

    override fun created(savedInstanceState: Bundle?) {
        thumbnailImageView = WeakReference(rootViewReference.get()?.findViewById(R.id.gif_thumbnail))
        gifImageViewRef = WeakReference(rootViewReference.get()?.findViewById(R.id.gif_view))
    }

    override fun getFragmentResourceId(): Int = R.layout.fragment_media_viewer_gif

    override fun inquireClose(): Boolean = true

    override fun handleDecryptingFile() {
        // nothing to do
    }

    override fun handleDecryptFailure() {
        // nothing to do
    }

    override fun showThumbnail(thumbnail: Drawable) {
        gifImageViewRef.get()?.visibility = View.INVISIBLE
        thumbnailImageView.get()?.visibility = View.VISIBLE
        thumbnailImageView.get()?.setImageDrawable(thumbnail)
    }

    override fun handleDecryptedFile(file: File?) {
        if (file == null) {
            logger.error("Cannot show gif: file is null")
            return
        }
        showGif(file)
    }

    /**
     * Show gif and hide the progress bar
     *
     * @param file the gif file
     */
    private fun showGif(file: File) {
        try {
            val gifDrawable = GifDrawable(requireContext().contentResolver, Uri.fromFile(file))
            gifImageViewRef.get()?.setImageDrawable(gifDrawable)
            gifImageViewRef.get()?.visibility = View.VISIBLE
            thumbnailImageView.get()?.visibility = View.GONE
            gifDrawable.start()
        } catch (e: IOException) {
            logger.error("Could not show gif", e)
        }
    }

}
