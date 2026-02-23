package ch.threema.app.glide

import android.graphics.Bitmap
import ch.threema.app.di.injectNonBinding
import ch.threema.app.services.FileService
import ch.threema.storage.models.AbstractMessageModel
import com.bumptech.glide.load.Options
import com.bumptech.glide.load.model.ModelLoader
import com.bumptech.glide.signature.ObjectKey
import org.koin.core.component.KoinComponent

class ThumbnailLoader : ModelLoader<AbstractMessageModel, Bitmap>, KoinComponent {

    private val fileService: FileService? by injectNonBinding()

    override fun buildLoadData(
        model: AbstractMessageModel,
        width: Int,
        height: Int,
        options: Options,
    ): ModelLoader.LoadData<Bitmap>? {
        return ModelLoader.LoadData(
            ObjectKey(model),
            ThumbnailFetcher(
                fileService = fileService ?: return null,
                messageModel = model,
            ),
        )
    }

    override fun handles(model: AbstractMessageModel) = true
}
