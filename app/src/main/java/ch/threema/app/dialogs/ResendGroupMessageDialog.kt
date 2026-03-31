package ch.threema.app.dialogs

import android.app.Dialog
import android.os.Bundle
import ch.threema.app.R
import ch.threema.app.preference.service.PreferenceService
import ch.threema.app.services.ContactService
import ch.threema.app.utils.ContactUtil
import ch.threema.app.utils.logScreenVisibility
import ch.threema.base.utils.getThreemaLogger
import ch.threema.domain.types.IdentityString
import com.google.android.material.dialog.MaterialAlertDialogBuilder

private val logger = getThreemaLogger("ResendGroupMessageDialog")

class ResendGroupMessageDialog(
    private val rejectedIdentities: Set<IdentityString>,
    private val contactService: ContactService,
    private val preferenceService: PreferenceService,
    private val callback: ResendMessageCallback,
) : ThreemaDialogFragment() {
    init {
        logScreenVisibility(logger)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val concatenatedContactNames = ContactUtil.joinDisplayNames(
            context,
            contactService.getByIdentities(rejectedIdentities.toList()),
            preferenceService.getContactNameFormat(),
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

        @JvmStatic
        fun getInstance(
            rejectedIdentities: Set<IdentityString>,
            contactService: ContactService,
            preferenceService: PreferenceService,
            resendMessageCallback: ResendMessageCallback,
        ): ResendGroupMessageDialog =
            ResendGroupMessageDialog(
                rejectedIdentities,
                contactService,
                preferenceService,
                resendMessageCallback,
            )
    }

    interface ResendMessageCallback {
        fun onPositiveClicked()
    }
}
