/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2022 Threema GmbH
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

package ch.threema.app.dialogs

import android.app.Dialog
import android.os.Bundle
import android.view.View
import android.widget.EditText
import ch.threema.app.R
import ch.threema.app.utils.EditTextUtil
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class CallbackTextEntryDialog : ThreemaDialogFragment() {

    private var title: String? = null
    private var initialText: String? = null
    private var callback: OnButtonClickedCallback? = null
    private var editText: EditText? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialogView: View? = activity?.layoutInflater?.inflate(R.layout.dialog_text_entry, null)
        editText = dialogView?.findViewById(R.id.edit_text)
        editText?.setText(initialText)
        editText?.setSelection(editText?.text?.length ?: 0)
        editText?.isFocusable = true
        editText?.requestFocus()

        val builder = MaterialAlertDialogBuilder(requireActivity())
        builder.setView(dialogView)

        title?.let {
            builder.setTitle(it)
        }

        builder.setPositiveButton(getString(R.string.ok)) { _, _ ->
            EditTextUtil.hideSoftKeyboard(editText)
            callback?.onPositiveClicked(editText?.text?.toString() ?: "")
        }

        builder.setNegativeButton(getString(R.string.cancel)) { _, _ ->
            EditTextUtil.hideSoftKeyboard(editText)
            callback?.onNegativeClicked()
        }

        return builder.create()
    }

    override fun onResume() {
        super.onResume()

        // Show keyboard
        editText?.postDelayed({
            EditTextUtil.showSoftKeyboard(editText)
        }, 100)
    }

    companion object {
        fun getInstance(
            title: String?,
            initialText: String?,
            onButtonClickedCallback: OnButtonClickedCallback
        ): CallbackTextEntryDialog {
            return CallbackTextEntryDialog().also {
                it.title = title
                it.initialText = initialText
                it.callback = onButtonClickedCallback
            }
        }
    }

    interface OnButtonClickedCallback {
        fun onPositiveClicked(text: String)
        fun onNegativeClicked()
    }

}
