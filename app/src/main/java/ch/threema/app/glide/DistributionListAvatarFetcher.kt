package ch.threema.app.glide

import android.content.Context
import android.graphics.Bitmap
import androidx.annotation.ColorInt
import androidx.vectordrawable.graphics.drawable.VectorDrawableCompat
import ch.threema.app.R
import ch.threema.app.services.DistributionListService
import ch.threema.app.services.avatarcache.AvatarCacheServiceImpl
import com.bumptech.glide.Priority
import com.bumptech.glide.load.data.DataFetcher

/**
 * This class is used to get the avatars from the database or create the default avatars. The computation/loading of these kind of bitmaps is faster than for contacts
 * or groups as only low resolution default avatars are needed.
 */
class DistributionListAvatarFetcher(
    private val context: Context,
    private val distributionListService: DistributionListService,
    private val distributionListConfig: AvatarCacheServiceImpl.DistributionListAvatarConfig,
) : AvatarFetcher(context) {
    private val distributionListDefaultAvatar: VectorDrawableCompat? by lazy {
        VectorDrawableCompat.create(
            context.resources,
            R.drawable.ic_distribution_list,
            null,
        )
    }

    override fun loadData(priority: Priority, callback: DataFetcher.DataCallback<in Bitmap>) {
        @ColorInt
        val avatarColor = distributionListService.getAvatarColor(distributionListConfig.subject)
        callback.onDataReady(
            buildDefaultAvatarLowRes(
                drawable = distributionListDefaultAvatar,
                color = avatarColor,
            ),
        )
    }
}
