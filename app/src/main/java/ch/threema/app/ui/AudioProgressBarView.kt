/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2022-2023 Threema GmbH
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

package ch.threema.app.ui

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import androidx.core.graphics.applyCanvas
import androidx.transition.ChangeClipBounds
import androidx.transition.Transition
import androidx.transition.TransitionManager
import ch.threema.app.R
import ch.threema.app.ThreemaApplication
import ch.threema.app.cache.ThumbnailCache
import ch.threema.app.utils.RuntimeUtil
import ch.threema.base.utils.LoggingUtil
import ch.threema.storage.models.AbstractMessageModel
import kotlin.math.min
import kotlin.math.roundToInt

class AudioProgressBarView : androidx.appcompat.widget.AppCompatSeekBar, AudioWaveformGeneratorTask.AudioWaveformGeneratorListener {
    private val logger = LoggingUtil.getThreemaLogger("AudioProgressBarView")

    private var barHeight = 20
    private var barWidth = 5
    private var spaceWidth: Int = 3
    private var barMinHeight = 4
    private var halfBarMinHeight = 2F

    var barColor = Color.TRANSPARENT
    var barColorActivated = Color.TRANSPARENT
    private lateinit var barPaint: Paint
    private lateinit var barPaintActivated: Paint
    private var viewWidth: Int = 0
    private var viewHeight: Int = 0
    private var numSamples: Int = 24
    private var waveBitmap: Bitmap? = null
    private var emptyBitmap: Bitmap? = null
    private var waveFormTask: AudioWaveformGeneratorTask? = null
    private var thumbnailCache: ThumbnailCache<Any>? = null
    private var messageModel: AbstractMessageModel? = null
    private var changeBounds: Transition = ChangeClipBounds()

    // we calculate a sufficiently large number of samples upfront so we don't need to wait for onLayout
    private val numPreCalculatedSamples = 30

    // radius of bar edges in px
    private val radius = 2F

    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        init(attrs)
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        init(attrs)
    }

    fun init(attrs: AttributeSet?) {
        val typedArray = context.theme.obtainStyledAttributes(attrs, R.styleable.AudioProgressBarView, 0, 0)

        with(typedArray) {
            barHeight = getDimensionPixelSize(R.styleable.AudioProgressBarView_barHeight, barHeight)
            barWidth = getDimensionPixelSize(R.styleable.AudioProgressBarView_barWidth, barWidth)
            spaceWidth = getDimensionPixelSize(R.styleable.AudioProgressBarView_spaceWidth, spaceWidth)
            barMinHeight = getDimensionPixelSize(R.styleable.AudioProgressBarView_barMinHeight, barMinHeight)
            halfBarMinHeight = barMinHeight / 2F
            barColor = getColor(R.styleable.AudioProgressBarView_barColor, barColor)
            barColorActivated = getColor(R.styleable.AudioProgressBarView_barColorActivated, barColorActivated)
            recycle()
        }

        barPaint = Paint().apply {
            isAntiAlias = true
            color = barColor
        }

        barPaintActivated = Paint().apply {
            isAntiAlias = true
            colorFilter = PorterDuffColorFilter(barColorActivated, PorterDuff.Mode.SRC_IN)
        }

        changeBounds.duration = 800
        changeBounds.interpolator = DecelerateInterpolator()
        changeBounds.addTarget(this)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)

        viewWidth = right - left
        viewHeight = bottom - top

        numSamples = viewWidth / (barWidth + spaceWidth)
    }

    override fun onDraw(canvas: Canvas) {
        if (!isAttachedToWindow) {
            return
        }

        if (max == 0) {
            max = 100
        }

        var drawBitmap = waveBitmap
        if (drawBitmap == null) {
            if (emptyBitmap != null) {
                drawBitmap = emptyBitmap
            } else {
                emptyBitmap = createEmptyBitmap()
                drawBitmap = emptyBitmap
            }
        }

        if (drawBitmap != null) {
            canvas.apply {
                save()
                clipRect((viewWidth * progress / max), 0, viewWidth, viewHeight)
                drawBitmap(drawBitmap, 0F, 0F, barPaint)
                restore()
            }

            canvas.apply {
                save()
                clipRect(0, 0, (viewWidth * progress / max), viewHeight)
                drawBitmap(drawBitmap, 0F, 0F, barPaintActivated)
                restore()
            }
        }
        super.onDraw(canvas)
    }

    private fun createEmptyBitmap() : Bitmap {
        val tmpBitmap = Bitmap.createBitmap(viewWidth, barHeight, Bitmap.Config.ARGB_8888)
        val unusedHeight: Float = (barHeight / 2F) - halfBarMinHeight
        val halfSpace = spaceWidth.toFloat() / 2F

        for (i: Int in 0 until numSamples) {
            tmpBitmap.applyCanvas {
                drawRoundRect(
                        RectF(
                                halfSpace + (i * (barWidth + spaceWidth)),
                                unusedHeight,
                                halfSpace + (i * (barWidth + spaceWidth)) + barWidth,
                                barHeight - unusedHeight
                        ),
                        radius,
                        radius,
                        barPaint
                )
            }
        }
        return tmpBitmap
    }

    private fun createWaveformBitmap(samplesData: List<Float>) : Bitmap {
        val tmpBitmap = Bitmap.createBitmap(viewWidth, barHeight, Bitmap.Config.ARGB_8888)
        val factor: Float = samplesData.size.toFloat() / numSamples.toFloat()
        val halfSpace = spaceWidth.toFloat() / 2F
        val halfBarHeight = barHeight / 2F

        for (i: Int in 0 until numSamples) {
            val sample = samplesData[(i * factor).roundToInt()]
            val unusedHeight: Float = min(halfBarHeight * (1F - sample), halfBarHeight - halfBarMinHeight)

            tmpBitmap.applyCanvas {
                drawRoundRect(
                        RectF(
                                halfSpace + (i * (barWidth + spaceWidth)),
                                unusedHeight,
                                halfSpace + (i * (barWidth + spaceWidth)) + barWidth,
                                barHeight - unusedHeight
                        ),
                        radius,
                        radius,
                        barPaint
                )
            }
        }
        return tmpBitmap
    }

    override fun onDataReady(newMessageModel: AbstractMessageModel, sampleData: List<Float>) {
        if (!isAttachedToWindow) {
            return
        }

        if (viewHeight == 0 || barHeight == 0) {
            return
        }

        waveFormTask = null
        if (messageModel?.id == newMessageModel.id) {
            if (sampleData.isNotEmpty()) {
                waveBitmap = createWaveformBitmap(sampleData)
                thumbnailCache?.set(messageModel?.id, waveBitmap)
                postInvalidate()
                show()
            }
        }
    }

    override fun onError(errorMessageModel: AbstractMessageModel, errorMessage: String?) {
        waveFormTask = null
    }

    override fun onCanceled(cancelMessageModel: AbstractMessageModel) {
        waveFormTask = null
    }

    override fun onDetachedFromWindow() {
        waveFormTask?.cancel()
        waveFormTask = null

        super.onDetachedFromWindow()
    }

    fun setMessageModel(newMessageModel: AbstractMessageModel?, thumbnailCache: ThumbnailCache<Any>?) {
        if (newMessageModel == null) {
            return
        }

        if (thumbnailCache == null) {
            return
        }

        this.thumbnailCache = thumbnailCache

        if (messageModel?.id != newMessageModel.id) {
            // recycled view
            waveFormTask?.let {
                if (it.getMessageId() == newMessageModel.id) {
                    return
                } else {
                    it.cancel()
                }
            }
        }

        val cachedBitmap = thumbnailCache.get(newMessageModel.id)
        if (cachedBitmap != null) {
            messageModel = newMessageModel
            waveBitmap = cachedBitmap

            postInvalidate()
        } else {
            waveFormTask?.let {
                if (it.getMessageId() == newMessageModel.id) {
                    return
                }
            }

            waveBitmap = null
            messageModel = newMessageModel
            waveFormTask = AudioWaveformGeneratorTask(newMessageModel,
                    numPreCalculatedSamples,
                    this@AudioProgressBarView)

            ThreemaApplication.voiceMessageThumbnailExecutorService.execute(Thread(waveFormTask, "WaveformGenerator"))
        }
    }

    private fun show() {
        if (messageModel == null) {
            return
        }

        RuntimeUtil.runOnUiThread {
            if (viewWidth > 0) {
                if (messageModel != null) {
                    clipBounds = Rect(0, ((viewHeight / 2F) - halfBarMinHeight).toInt(), viewWidth, ((viewHeight / 2F) + halfBarMinHeight).toInt())
                    TransitionManager.beginDelayedTransition(parent as ViewGroup, changeBounds)
                }
                clipBounds = Rect(0, 0, viewWidth, viewHeight)
                visibility = VISIBLE
            }
        }
    }
}
