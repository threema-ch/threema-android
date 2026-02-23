package ch.threema.app.ui

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources
import ch.threema.app.R
import ch.threema.app.utils.IconUtil
import ch.threema.app.utils.MimeUtil

class BigFileView : FrameLayout {
    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)

    private val fileTypeView: TextView
    private val fileIconView: ImageView
    private val filenameView: TextView

    init {
        LayoutInflater.from(context).inflate(R.layout.view_big_file, this, true)
        fileTypeView = findViewById(R.id.big_file_type)
        fileIconView = findViewById(R.id.big_file_image_view)
        filenameView = findViewById(R.id.big_filename_view)
    }

    fun setMediaItem(mediaItem: MediaItem) {
        val mimeIcon = IconUtil.getMimeIcon(mediaItem.mimeType)
        fileTypeView.text = MimeUtil.getMimeDescription(context, mediaItem.mimeType)
        fileIconView.setImageDrawable(AppCompatResources.getDrawable(context, mimeIcon))
        setFilename(mediaItem.filename)
    }

    fun setFilename(filename: String?) {
        filenameView.text = filename
    }
}
