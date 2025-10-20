/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2018-2025 Threema GmbH
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

package ch.threema.app.activities

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap.CompressFormat
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.MenuItem
import android.view.View
import android.view.ViewTreeObserver.OnGlobalLayoutListener
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.ViewCompat
import ch.threema.app.R
import ch.threema.app.ui.InsetSides.Companion.lbr
import ch.threema.app.ui.applyDeviceInsetsAsPadding
import ch.threema.app.utils.BitmapUtil
import ch.threema.app.utils.buildActivityIntent
import ch.threema.app.utils.getIntOrNull
import ch.threema.app.utils.getParcelable
import ch.threema.app.utils.getSerializable
import ch.threema.app.utils.logScreenVisibility
import ch.threema.base.utils.LoggingUtil
import ch.threema.domain.protocol.csp.ProtocolDefines
import com.canhub.cropper.CropImageView
import com.canhub.cropper.CropImageView.CropShape
import com.canhub.cropper.CropImageView.RequestSizeOptions
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import java.util.concurrent.atomic.AtomicBoolean

private val logger = LoggingUtil.getThreemaLogger("CropImageActivity")

class CropImageActivity : ThreemaToolbarActivity() {
    init {
        this.logScreenVisibility(logger)

        // Always use night mode for this activity. Note that setting it here avoids the activity being recreated.
        getDelegate().localNightMode = AppCompatDelegate.MODE_NIGHT_YES
    }

    private val cropStarted = AtomicBoolean(false)

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val toolbar = findViewById<MaterialToolbar?>(R.id.crop_toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val cropImageView = findViewById<CropImageView>(R.id.crop_image)

        val cropImageParameters = try {
            intent.toCropImageParameters()
        } catch (e: IllegalStateException) {
            logger.error("Invalid intent", e)
            setResult(RESULT_CANCELED)
            finish()
            return
        }

        findViewById<ExtendedFloatingActionButton>(R.id.floating)
            .setOnClickListener { onSaveClicked(cropImageView, cropImageParameters) }

        cropImageView.setOnSetImageUriCompleteListener { view: CropImageView, _, error: Exception? ->
            if (error != null) {
                logger.error("Could not load image", error)
                return@setOnSetImageUriCompleteListener
            }
            // non-exif
            if ((cropImageParameters.flip and BitmapUtil.FLIP_HORIZONTAL) == BitmapUtil.FLIP_HORIZONTAL) {
                view.flipImageHorizontally()
            }
            if ((cropImageParameters.flip and BitmapUtil.FLIP_VERTICAL) == BitmapUtil.FLIP_VERTICAL) {
                view.flipImageVertically()
            }
            if (cropImageParameters.rotation != 0) {
                view.rotateImage(cropImageParameters.rotation)
            }

            // Additional flip and rotation
            if ((cropImageParameters.additionalFlip and BitmapUtil.FLIP_HORIZONTAL) == BitmapUtil.FLIP_HORIZONTAL) {
                view.flipImageHorizontally()
            }
            if ((cropImageParameters.additionalFlip and BitmapUtil.FLIP_VERTICAL) == BitmapUtil.FLIP_VERTICAL) {
                view.flipImageVertically()
            }
            if (cropImageParameters.additionalRotation != 0) {
                view.rotateImage(cropImageParameters.additionalRotation)
            }

            val aspectX = cropImageParameters.aspectX
            val aspectY = cropImageParameters.aspectY
            if (aspectX != null && aspectY != null && aspectX > 0 && aspectY > 0) {
                view.setAspectRatio(aspectX, aspectY)
            }
        }
        if (savedInstanceState == null) {
            cropImageView.cropShape = when (cropImageParameters.shape) {
                Shape.RECTANGLE -> CropShape.RECTANGLE
                Shape.OVAL -> CropShape.OVAL
            }
            cropImageView.setImageUriAsync(cropImageParameters.sourceUri)
        }
        cropImageView.setOnCropImageCompleteListener { _, _ ->
            cropCompleted(cropImageParameters.saveUri)
        }

        findViewById<View>(android.R.id.content).let { contentView ->
            contentView.getViewTreeObserver().addOnGlobalLayoutListener(
                object : OnGlobalLayoutListener {
                    override fun onGlobalLayout() {
                        contentView!!.getViewTreeObserver().removeOnGlobalLayoutListener(this)
                        excludeGestures(cropImageView)
                    }
                },
            )
        }
    }

    override fun handleDeviceInsets() {
        super.handleDeviceInsets()
        findViewById<View?>(R.id.crop_parent)
            .applyDeviceInsetsAsPadding(
                lbr(),
            )
    }

    override fun getLayoutResource() = R.layout.activity_crop

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            setResult(RESULT_CANCELED)
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun cropCompleted(saveUri: Uri) {
        setResult(RESULT_OK, Intent().putExtra(MediaStore.EXTRA_OUTPUT, saveUri))
        finish()
    }

    private fun onSaveClicked(imageView: CropImageView, cropImageParameters: CropImageParameters) {
        // Only allow cropping once
        if (cropStarted.getAndSet(true)) {
            return
        }
        val maxWidth = cropImageParameters.maxWidth
        val maxHeight = cropImageParameters.maxHeight
        if (maxWidth != null && maxHeight != null) {
            imageView.croppedImageAsync(
                cropImageParameters.compressFormat,
                100,
                maxWidth,
                maxHeight,
                RequestSizeOptions.RESIZE_INSIDE,
                cropImageParameters.saveUri,
            )
        } else {
            imageView.croppedImageAsync(
                cropImageParameters.compressFormat,
                100,
                0,
                0,
                RequestSizeOptions.NONE,
                cropImageParameters.saveUri,
            )
        }
    }

    private fun excludeGestures(imageView: CropImageView) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return
        }

        val maxHeight = getResources().getDimensionPixelSize(R.dimen.gesture_exclusion_max_height)
        val drawingRect = Rect()
        imageView.getDrawingRect(drawingRect)

        var y = 0
        var realHeight = drawingRect.height()
        if (realHeight > maxHeight) {
            y = (realHeight - maxHeight) / 2
            realHeight = maxHeight
        }

        val exclusionRect = Rect(
            0,
            y,
            getResources().getDimensionPixelSize(R.dimen.gesture_exclusion_border_width),
            y + realHeight,
        )
        ViewCompat.setSystemGestureExclusionRects(imageView, mutableListOf<Rect?>(exclusionRect))
    }

    companion object {
        private const val EXTRA_MAX_WIDTH = "max_width"
        private const val EXTRA_MAX_HEIGHT = "max_height"
        private const val EXTRA_ASPECT_X = "aspect_x"
        private const val EXTRA_ASPECT_Y = "aspect_y"
        private const val EXTRA_FLIP = "flip"
        private const val EXTRA_ROTATION = "rotation"
        private const val EXTRA_SHAPE = "shape"
        private const val EXTRA_ADDITIONAL_FLIP = "additional_flip"
        private const val EXTRA_ADDITIONAL_ROTATION = "additional_rotation"
        private const val EXTRA_COMPRESS_FORMAT_ORDINAL = "compress_format"

        @JvmStatic
        fun createIntent(context: Context, cropImageParameters: CropImageParameters) = buildActivityIntent<CropImageActivity>(context) {
            cropImageParameters.maxWidth?.let { maxWidth -> putExtra(EXTRA_MAX_WIDTH, maxWidth) }
            cropImageParameters.maxHeight?.let { maxHeight -> putExtra(EXTRA_MAX_HEIGHT, maxHeight) }
            cropImageParameters.aspectX?.let { aspectX -> putExtra(EXTRA_ASPECT_X, aspectX) }
            cropImageParameters.aspectY?.let { aspectY -> putExtra(EXTRA_ASPECT_Y, aspectY) }
            putExtra(EXTRA_FLIP, cropImageParameters.flip)
            putExtra(EXTRA_ROTATION, cropImageParameters.rotation)
            putExtra(EXTRA_ADDITIONAL_FLIP, cropImageParameters.additionalFlip)
            putExtra(EXTRA_ADDITIONAL_ROTATION, cropImageParameters.additionalRotation)
            putExtra(EXTRA_COMPRESS_FORMAT_ORDINAL, cropImageParameters.compressFormat.ordinal)
            putExtra(EXTRA_SHAPE, cropImageParameters.shape)
            putExtra(MediaStore.EXTRA_OUTPUT, cropImageParameters.saveUri)
            data = cropImageParameters.sourceUri
        }

        private fun Intent.toCropImageParameters(): CropImageParameters {
            val sourceUri = data
            check(sourceUri != null)
            val saveUri = getParcelable<Uri>(MediaStore.EXTRA_OUTPUT)
            check(saveUri != null)

            return CropImageParameters(
                maxWidth = getIntOrNull(EXTRA_MAX_WIDTH),
                maxHeight = getIntOrNull(EXTRA_MAX_HEIGHT),
                aspectX = getIntOrNull(EXTRA_ASPECT_X),
                aspectY = getIntOrNull(EXTRA_ASPECT_Y),
                flip = getIntExtra(EXTRA_FLIP, BitmapUtil.FLIP_NONE),
                rotation = getIntExtra(EXTRA_ROTATION, 0),
                additionalFlip = getIntExtra(EXTRA_ADDITIONAL_FLIP, BitmapUtil.FLIP_NONE),
                additionalRotation = getIntExtra(EXTRA_ADDITIONAL_ROTATION, 0),
                compressFormat = getCompressFormat(),
                shape = getSerializable<Shape>(EXTRA_SHAPE) as? Shape? ?: Shape.RECTANGLE,
                sourceUri = sourceUri,
                saveUri = saveUri,
            )
        }

        private fun Intent.getCompressFormat(): CompressFormat =
            getIntOrNull(EXTRA_COMPRESS_FORMAT_ORDINAL)
                ?.let { ordinal ->
                    CompressFormat.entries.getOrNull(ordinal)
                }
                ?: CompressFormat.PNG
    }

    data class CropImageParameters @JvmOverloads constructor(
        /**
         * The maximum width of the image. The width is only restricted if this and [maxHeight] contain both a positive value.
         */
        var maxWidth: Int? = null,
        /**
         * The maximum height of the image. The height is only restricted if this and [maxWidth] contain both a positive value.
         */
        var maxHeight: Int? = null,
        /**
         * Together with [aspectY] this represents the aspect ratio of the cropped image. Note that it is only enforced if this and [aspectY] contain both
         * a positive value.
         */
        var aspectX: Int? = null,
        /**
         * Together with [aspectX] this represents the aspect ratio of the cropped image. Note that it is only enforced if this and [aspectX] contain both
         * a positive value.
         */
        var aspectY: Int? = null,
        /**
         * The flip value can contain the bits [BitmapUtil.FLIP_VERTICAL] and/or [BitmapUtil.FLIP_HORIZONTAL] to represent a vertical and/or horizontal
         * flip. The flip is applied before the [rotation] is applied.
         */
        var flip: Int = BitmapUtil.FLIP_NONE,
        /**
         * The clockwise rotation in degrees. Negative values represent counter-clockwise rotations. The rotation is applied after [flip].
         */
        var rotation: Int = 0,
        /**
         * The additional flip has the same properties as [flip] but is applied additionally to it after the [rotation] has been applied.
         */
        var additionalFlip: Int = BitmapUtil.FLIP_NONE,
        /**
         * The additional rotation contains a rotation that is applied after the [additionalFlip].
         */
        var additionalRotation: Int = 0,
        /**
         * The compress format that will be used for the cropped image. Note that regardless of the format, maximum quality will be used.
         */
        var compressFormat: CompressFormat = CompressFormat.PNG,
        /**
         * The shape of the cropped image.
         */
        var shape: Shape = Shape.RECTANGLE,
        /**
         * The uri that is used to load the base image.
         */
        var sourceUri: Uri,
        /**
         * The uri where the cropped image will be stored to.
         */
        var saveUri: Uri,
    ) {
        companion object {
            @JvmStatic
            fun getProfilePictureParameters(sourceUri: Uri, saveUri: Uri) = CropImageParameters(
                maxWidth = ProtocolDefines.PROFILE_PICTURE_WIDTH_PX,
                maxHeight = ProtocolDefines.PROFILE_PICTURE_HEIGHT_PX,
                aspectX = 1,
                aspectY = 1,
                compressFormat = CompressFormat.JPEG,
                shape = Shape.OVAL,
                sourceUri = sourceUri,
                saveUri = saveUri,
            )
        }
    }

    /**
     * The shape of the cropping.
     */
    enum class Shape {
        /**
         * The cropped image will be a rectangle. Depending on the aspect ratio, it may also be a square.
         */
        RECTANGLE,

        /**
         * The cropped image will be an oval. Depending on the aspect ratio, it may also be a circle.
         */
        OVAL,
    }
}
