package ch.threema.app.motionviews.widget

import android.net.Uri
import ch.threema.app.ui.MediaItem

/**
 * An action entity that stores the uri of the previous (full size) image with its orientation
 * parameters.
 */
class CropEntity(private val lastUri: Uri, private val orientation: MediaItem.Orientation) :
    ActionEntity {
    fun getLastUri() = lastUri
    fun getOrientation() = orientation
}
