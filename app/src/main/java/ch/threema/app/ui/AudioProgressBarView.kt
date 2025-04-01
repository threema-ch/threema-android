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

package ch.threema.app.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Rect
import android.graphics.RectF
import android.os.Build
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import androidx.core.content.ContextCompat
import androidx.core.graphics.applyCanvas
import androidx.transition.ChangeClipBounds
import androidx.transition.Transition
import androidx.transition.TransitionManager
import ch.threema.app.R
import ch.threema.app.ThreemaApplication
import ch.threema.app.cache.ThumbnailCache
import ch.threema.app.utils.ConfigUtils
import ch.threema.app.utils.RuntimeUtil
import ch.threema.base.utils.LoggingUtil
import ch.threema.storage.models.AbstractMessageModel
import kotlin.math.min
import kotlin.math.roundToInt

private val logger = LoggingUtil.getThreemaLogger("AudioProgressBarView")

class AudioProgressBarView : androidx.appcompat.widget.AppCompatSeekBar,
    AudioWaveformGeneratorTask.AudioWaveformGeneratorListener {

    private var barHeight = 20
    private var barWidth = 5
    private var spaceWidth: Int = 3
    private var barMinHeight = 4
    private var halfBarMinHeight = 2F
    private var state = 0

    private lateinit var barColor: ColorStateList
    private var barColorActivated = Color.TRANSPARENT
    private lateinit var barPaint: Paint
    private lateinit var barPaintChecked: Paint
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

    private var numPreCalculatedSamples: Int = 0

    // radius of bar edges in px
    private val radius = 2F

    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        init(attrs)
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    ) {
        init(attrs)
    }

    fun init(attrs: AttributeSet?) {
        barColor =
            ContextCompat.getColorStateList(context, R.color.bubble_send_text_colorstatelist)!!
        val typedArray =
            context.theme.obtainStyledAttributes(attrs, R.styleable.AudioProgressBarView, 0, 0)

        with(typedArray) {
            barHeight = getDimensionPixelSize(R.styleable.AudioProgressBarView_barHeight, barHeight)
            barWidth = getDimensionPixelSize(R.styleable.AudioProgressBarView_barWidth, barWidth)
            spaceWidth =
                getDimensionPixelSize(R.styleable.AudioProgressBarView_spaceWidth, spaceWidth)
            barMinHeight =
                getDimensionPixelSize(R.styleable.AudioProgressBarView_barMinHeight, barMinHeight)
            halfBarMinHeight = barMinHeight / 2F
            barColor = getColorStateList(R.styleable.AudioProgressBarView_barColor)!!
            barColorActivated =
                getColor(R.styleable.AudioProgressBarView_barColorActivated, barColorActivated)
            recycle()
        }

        numPreCalculatedSamples = guessSuitableAmountOfSamples(context)

        barPaint = Paint().apply {
            isAntiAlias = true
            color = if (Build.VERSION.SDK_INT >= 23) {
                barColor.defaultColor
            } else {
                ConfigUtils.getColorFromAttribute(context, R.attr.colorOnBackground)
            }
        }

        barPaintChecked = Paint().apply {
            isAntiAlias = true
            val checkedColor: Int = if (Build.VERSION.SDK_INT >= 23) {
                barColor.getColorForState(
                    intArrayOf(android.R.attr.state_activated),
                    barColor.defaultColor
                )
            } else {
                ConfigUtils.getColorFromAttribute(context, R.attr.colorOnPrimary)
            }
            colorFilter = PorterDuffColorFilter(checkedColor, PorterDuff.Mode.SRC_IN)
        }

        barPaintActivated = Paint().apply {
            isAntiAlias = true
            colorFilter = PorterDuffColorFilter(barColorActivated, PorterDuff.Mode.SRC_IN)
        }

        changeBounds.duration = 800
        changeBounds.interpolator = DecelerateInterpolator()
        changeBounds.addTarget(this)

        visibility = View.INVISIBLE
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
            if (state == 1) {
                canvas.apply {
                    save()
                    clipRect(0, 0, viewWidth, viewHeight)
                    drawBitmap(drawBitmap, 0F, 0F, barPaintChecked)
                    restore()
                }

            } else {
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
        }
        super.onDraw(canvas)
    }

    /**
     *  We calculate a sufficiently large number of samples upfront so we don't need to wait for onLayout
     *  to tell us the width of our view.
     *  The number will adapt based on the devices screen width. Because on tablets we want to present more samples.
     */
    private fun guessSuitableAmountOfSamples(context: Context): Int {
        return (30f + (context.resources.displayMetrics.widthPixels / 40f)).roundToInt()
    }

    private fun createEmptyBitmap(): Bitmap {
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

    private fun createWaveformBitmap(samplesData: List<Float>): Bitmap {
        val tmpBitmap = Bitmap.createBitmap(viewWidth, barHeight, Bitmap.Config.ARGB_8888)
        if (samplesData.size < numSamples) {
            logger.warn(
                "Insufficient amount of calculated samples: {} < {}",
                samplesData.size,
                numSamples
            )
            return tmpBitmap
        }
        val factor: Float = samplesData.size.toFloat() / numSamples.toFloat()
        val halfSpace = spaceWidth.toFloat() / 2F
        val halfBarHeight = barHeight / 2F

        for (i: Int in 0 until numSamples) {
            val sample = samplesData[(i * factor).roundToInt()]
            val unusedHeight: Float =
                min(halfBarHeight * (1F - sample), halfBarHeight - halfBarMinHeight)

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


    override fun onCreateDrawableState(extraSpace: Int): IntArray {
        val drawableState = super.onCreateDrawableState(extraSpace)

        if (::barPaint.isInitialized) {
            state = if (drawableState.contains(android.R.attr.state_activated)) 1 else 0
        }
        return drawableState
    }

    fun setMessageModel(
        newMessageModel: AbstractMessageModel?,
        thumbnailCache: ThumbnailCache<Any>?
    ) {
        if (newMessageModel == null) {
            return
        }

        if (thumbnailCache == null) {
            return
        }

        this.thumbnailCache = thumbnailCache

        if (messageModel?.id != newMessageModel.id) {
            // recycled view
            waveFormTask?.let { task ->
                if (task.getMessageId() == newMessageModel.id) {
                    return
                } else {
                    task.cancel()
                }
            }
        }

        val cachedBitmap = thumbnailCache.get(newMessageModel.id)
        if (cachedBitmap != null) {
            messageModel = newMessageModel
            waveBitmap = cachedBitmap

            postInvalidate()
            visibility = VISIBLE
        } else {
            waveFormTask?.let { task ->
                if (task.getMessageId() == newMessageModel.id) {
                    return
                }
            }

            waveBitmap = null
            messageModel = newMessageModel
            waveFormTask = AudioWaveformGeneratorTask(
                newMessageModel,
                numPreCalculatedSamples,
                this@AudioProgressBarView
            )

            ThreemaApplication.voiceMessageThumbnailExecutorService.execute(
                Thread(
                    waveFormTask,
                    "WaveformGenerator"
                )
            )
        }
    }

    private fun show() {
        if (messageModel == null) {
            return
        }

        RuntimeUtil.runOnUiThread {
            if (viewWidth > 0) {
                if (messageModel != null) {
                    clipBounds = Rect(
                        0,
                        ((viewHeight / 2F) - halfBarMinHeight).toInt(),
                        viewWidth,
                        ((viewHeight / 2F) + halfBarMinHeight).toInt()
                    )
                    TransitionManager.beginDelayedTransition(parent as ViewGroup, changeBounds)
                }
                clipBounds = Rect(0, 0, viewWidth, viewHeight)
                visibility = VISIBLE
            }
        }
    }
}
