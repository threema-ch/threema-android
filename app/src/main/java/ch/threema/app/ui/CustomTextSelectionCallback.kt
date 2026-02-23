package ch.threema.app.ui

import android.view.ActionMode
import android.widget.TextView

abstract class CustomTextSelectionCallback : ActionMode.Callback {
    protected var textView: TextView? = null
    fun setTextViewRef(textView: TextView) {
        this.textView = textView
    }
}
