package ch.threema.app.utils

import android.net.Uri

interface LinkConfirmationListener {
    /**
     * This method is called when a suspicious url has been clicked which needs user confirmation before opening.
     * This might be due to an IDN homograph attack or other reasons. See {@link UrlUtil#isSafeUri(Uri)} for details.
     * If opening has been confirmed by the user, the url can be opened using {@link LinkifyUtil#openLink(Uri, Context, BottomSheetGridDialogListener)}
     *
     * @param warning Warning text to show to the user for confirmation
     * @param uri Url which needs confirmation before opening
     */
    fun onLinkNeedsConfirmation(warning: String, uri: Uri)

    companion object {
        const val DIALOG_TAG_CONFIRM_LINK: String = "cnfl"
    }
}
