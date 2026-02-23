package ch.threema.app.glide

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat
import androidx.vectordrawable.graphics.drawable.VectorDrawableCompat
import ch.threema.app.R
import ch.threema.app.utils.AvatarConverterUtil
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.data.DataFetcher

/**
 * This class provides the basic functionality to build default avatars. It must be overridden by specific avatar fetchers.
 */
abstract class AvatarFetcher(private val context: Context) : DataFetcher<Bitmap> {

    private val avatarSizeSmall: Int by lazy { context.resources.getDimensionPixelSize(R.dimen.avatar_size_small) }
    private val avatarSizeHiRes: Int by lazy { context.resources.getDimensionPixelSize(R.dimen.avatar_size_hires) }

    override fun cleanup() {
        // Nothing to cleanup
    }

    override fun cancel() {
        // Nothing to do here
    }

    override fun getDataClass(): Class<Bitmap> = Bitmap::class.java

    override fun getDataSource(): DataSource = DataSource.LOCAL

    @ColorInt
    protected fun getBackgroundColor(options: AvatarOptions): Int =
        if (options.darkerBackground) {
            ContextCompat.getColor(context, R.color.material_grey_300)
        } else {
            Color.WHITE
        }

    /**
     * Create a bitmap of the given drawable with the given color in high resolution. Used for large views like
     * in GroupDetailActivity.
     */
    protected fun buildDefaultAvatarHighRes(
        drawable: VectorDrawableCompat?,
        color: Int,
        backgroundColor: Int,
    ): Bitmap =
        AvatarConverterUtil.buildDefaultAvatarHighRes(drawable, avatarSizeHiRes, color, backgroundColor)

    /**
     * Create a bitmap of the given drawable with the given color in low resolution. Used for smaller views
     * like in ContactListAdapter.
     */
    protected fun buildDefaultAvatarLowRes(drawable: VectorDrawableCompat?, color: Int): Bitmap =
        AvatarConverterUtil.getAvatarBitmap(drawable, color, avatarSizeSmall)
}
