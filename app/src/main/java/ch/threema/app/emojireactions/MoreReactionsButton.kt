package ch.threema.app.emojireactions

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.TextView
import ch.threema.app.R
import ch.threema.common.consume
import com.google.android.material.card.MaterialCardView

class MoreReactionsButton : MaterialCardView {
    private var labelText: String? = null
    var onMoreReactionsButtonClickListener: OnMoreReactionsButtonClickListener? = null

    private val gestureDetector =
        GestureDetector(
            context,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onSingleTapUp(e: MotionEvent) = consume {
                    performClick()
                }

                override fun onLongPress(e: MotionEvent) {
                    performLongClick()
                }
            },
        )

    private val labelView: TextView

    @JvmOverloads
    constructor(context: Context, attrs: AttributeSet? = null) : super(context, attrs)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    init {
        layoutParams = LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )
        isClickable = true
        isFocusable = true
        isDuplicateParentStateEnabled = false

        LayoutInflater.from(context).inflate(R.layout.view_more_reactions_button, this as ViewGroup)

        labelView = findViewById(R.id.reaction_label)
    }

    private fun renderContents() {
        labelView.text = labelText
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent) = consume {
        gestureDetector.onTouchEvent(event)
        if (event.action == MotionEvent.ACTION_UP) {
            isPressed = false
        } else if (event.action == MotionEvent.ACTION_DOWN) {
            isPressed = true
        }
    }

    override fun performClick(): Boolean {
        onMoreReactionsButtonClickListener?.onMoreReactionsButtonClick()
        return super.performClick()
    }

    override fun performLongClick(): Boolean {
        onMoreReactionsButtonClickListener?.onMoreReactionsButtonLongClick()
        return super.performLongClick()
    }

    fun setButtonData(text: String) {
        labelText = text
        renderContents()
    }

    interface OnMoreReactionsButtonClickListener {
        fun onMoreReactionsButtonClick()
        fun onMoreReactionsButtonLongClick()
    }
}
