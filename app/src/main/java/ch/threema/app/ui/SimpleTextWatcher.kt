package ch.threema.app.ui

import android.text.Editable
import android.text.TextWatcher

abstract class SimpleTextWatcher : TextWatcher {
    override fun beforeTextChanged(text: CharSequence, start: Int, count: Int, after: Int) {
        // do nothing by default
    }

    override fun afterTextChanged(editable: Editable) {
        // do nothing by default
    }

    override fun onTextChanged(text: CharSequence, start: Int, before: Int, count: Int) {
        // do nothing by default
    }
}
