/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2023-2025 Threema GmbH
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
import ch.threema.app.R
import ch.threema.app.services.ContactService
import ch.threema.app.utils.ContactUtil
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class ResendGroupMessageDialog(
    private val rejectedIdentities: Set<String>,
    private val contactService: ContactService,
    private val callback: ResendMessageCallback,
) : ThreemaDialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val concatenatedContactNames = ContactUtil.joinDisplayNames(
            context,
            contactService.getByIdentities(rejectedIdentities.toList()),
        )
        val builder = MaterialAlertDialogBuilder(requireActivity())
        builder.setTitle(getString(R.string.resend_message_dialog_title))
        builder.setMessage(
            getString(
                R.string.resend_message_dialog_message,
                concatenatedContactNames,
            ),
        )
        builder.setPositiveButton(getString(R.string.ok)) { _, _ ->
            callback.onPositiveClicked()
        }
        builder.setNegativeButton(getString(R.string.cancel)) { _, _ -> }

        return builder.create()
    }

    companion object {
        fun getInstance(
            rejectedIdentities: Set<String>,
            contactService: ContactService,
            resendMessageCallback: ResendMessageCallback,
        ): ResendGroupMessageDialog {
            return ResendGroupMessageDialog(
                rejectedIdentities,
                contactService,
                resendMessageCallback,
            )
        }
    }

    interface ResendMessageCallback {
        fun onPositiveClicked()
    }
}
