/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2025 Threema GmbH
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

package ch.threema.app.multidevice.wizard

import android.content.Context
import androidx.annotation.AttrRes
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.style.TextDecoration
import ch.threema.app.BuildFlavor
import ch.threema.app.R
import ch.threema.app.compose.common.linkifyWeb
import ch.threema.app.utils.ConfigUtils

/**
 * @param iconTintAttrRes If `null` the original colors from the passed icon are used. This can lead to a loss of contrast.
 */
sealed class LinkingResult(
    @DrawableRes val iconRes: Int,
    @AttrRes val iconTintAttrRes: Int? = null,
    @StringRes val titleTextRes: Int,
    @StringRes val bodyTextRes: Int,
    @StringRes val primaryButtonTextRes: Int,
) {
    open fun resolveTitleText(context: Context): String = context.getString(titleTextRes)

    open fun resolveBodyText(context: Context): AnnotatedString = AnnotatedString(
        text = context.getString(bodyTextRes),
    )

    data object Success : LinkingResult(
        iconRes = R.drawable.emoji_party_popper,
        titleTextRes = R.string.device_linked_successfully,
        bodyTextRes = R.string.device_linked_successfully_explain,
        primaryButtonTextRes = R.string.label_continue,
    )

    sealed class Failure(
        @DrawableRes iconRes: Int = R.drawable.ic_error_rounded,
        @AttrRes iconTintRes: Int? = R.attr.colorError,
        @StringRes titleTextRes: Int,
        @StringRes bodyTextRes: Int,
        @StringRes primaryButtonTextRes: Int = R.string.close,
    ) : LinkingResult(
        iconRes = iconRes,
        iconTintAttrRes = iconTintRes,
        titleTextRes = titleTextRes,
        bodyTextRes = bodyTextRes,
        primaryButtonTextRes = primaryButtonTextRes,
    ) {
        data object Generic : Failure(
            titleTextRes = R.string.device_linking_error_generic_title,
            bodyTextRes = R.string.device_linking_error_generic_body,
        )

        data object GenericNetwork : Failure(
            titleTextRes = R.string.device_linking_error_generic_network_title,
            bodyTextRes = R.string.device_linking_error_generic_network_body,
        )

        data object UnknownQrCode : Failure(
            titleTextRes = R.string.device_linking_error_unknown_qr_code_title,
            bodyTextRes = R.string.device_linking_error_unknown_qr_code_body,
        ) {
            override fun resolveBodyText(context: Context): AnnotatedString = context.getString(bodyTextRes)
                .linkifyWeb(
                    url = context.getString(BuildFlavor.current.desktopClientFlavor.downloadLink),
                    linkStyle = SpanStyle(
                        textDecoration = TextDecoration.Underline,
                        color = Color(ConfigUtils.getColorFromAttribute(context, R.attr.colorPrimary)),
                    ),
                )
        }

        data object ThreemaWebQrCode : Failure(
            titleTextRes = R.string.device_linking_error_web_qr_code_title,
            bodyTextRes = R.string.device_linking_error_web_qr_code_body,
        ) {
            override fun resolveBodyText(context: Context): AnnotatedString = context.getString(bodyTextRes)
                .linkifyWeb(
                    url = context.getString(BuildFlavor.current.desktopClientFlavor.downloadLink),
                    linkStyle = SpanStyle(
                        textDecoration = TextDecoration.Underline,
                        color = Color(ConfigUtils.getColorFromAttribute(context, R.attr.colorPrimary)),
                    ),
                )
        }

        data object OldRendezvousProtocolVersion : Failure(
            titleTextRes = R.string.device_linking_error_old_rendezvous_version_title,
            bodyTextRes = R.string.device_linking_error_old_rendezvous_version_body,
        )

        data object EmojiMismatch : Failure(
            titleTextRes = R.string.device_linking_error_emoji_mismatch_title,
            bodyTextRes = R.string.device_linking_error_emoji_mismatch_body,
        )

        data object Unexpected : Failure(
            titleTextRes = R.string.device_linking_error_unexpected_title,
            bodyTextRes = R.string.device_linking_error_unexpected_body,
        )

        data class InvalidContact(private val invalidIdentity: String) : Failure(
            titleTextRes = R.string.device_linking_error_invalid_contact_title,
            bodyTextRes = R.string.device_linking_error_invalid_contact_body,
        ) {
            override fun resolveBodyText(context: Context): AnnotatedString {
                return AnnotatedString(context.getString(bodyTextRes, invalidIdentity))
            }
        }
    }
}
