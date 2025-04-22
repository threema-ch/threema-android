/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2024-2025 Threema GmbH
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

package ch.threema.app.utils

import android.content.Context
import android.net.Uri
import android.text.Spannable
import android.text.SpannableString
import android.text.Spanned
import android.text.SpannedString
import android.text.style.BackgroundColorSpan
import android.text.style.ClickableSpan
import android.text.style.ForegroundColorSpan
import android.view.View
import androidx.annotation.ColorInt
import androidx.core.net.toUri
import androidx.core.text.toSpanned
import ch.threema.app.R
import java.util.Locale

fun String.capitalize(): String =
    replaceFirstChar { it.titlecase(Locale.getDefault()) }

fun CharSequence.truncate(maxLength: Int): CharSequence {
    require(maxLength >= 0)
    return if (length > maxLength) substring(0, maxLength) + "…" else this
}

/**
 *  This will highlight **all** occurrences of [filterText] in this char-sequence.
 *
 *  @param filterText Can actually be null, empty or blank
 *  @param drawBackground If set to `true` the occurrences of [filterText] will have a background behind them.
 *  If set to `false` the highlight effect is achieved by painting the occurrences of [filterText] in a different text color.
 */
fun CharSequence?.highlightMatches(
    context: Context,
    filterText: String?,
    drawBackground: Boolean,
    shouldNormalize: Boolean,
): Spannable {
    if (isNullOrBlank()) {
        return SpannableString("")
    } else if (filterText.isNullOrBlank()) {
        return SpannableString(this)
    }

    val spannableResult: Spannable = SpannableString(this)

    val fullUpperText: String = if (shouldNormalize) {
        LocaleUtil.normalize(this.toString())
    } else {
        this.toString().lowercase(Locale.getDefault())
    }
    val filterUpperText: String = if (shouldNormalize) {
        LocaleUtil.normalize(filterText)
    } else {
        filterText.lowercase(Locale.getDefault())
    }

    @ColorInt
    val highlightColor: Int = ConfigUtils.getColorFromAttribute(context, R.attr.colorPrimary)

    @ColorInt
    val foregroundColor = if (drawBackground) {
        ConfigUtils.getColorFromAttribute(context, R.attr.colorOnPrimary)
    } else {
        highlightColor
    }

    var start: Int
    var end: Int
    var matchIndex = 0

    while (
        fullUpperText.indexOf(filterUpperText, matchIndex).also { start = it } >= 0
    ) {
        end = start + filterText.length
        if (end <= this.length) {
            if (drawBackground) {
                spannableResult.setSpan(
                    BackgroundColorSpan(highlightColor),
                    start,
                    end,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
            }
            spannableResult.setSpan(
                ForegroundColorSpan(foregroundColor),
                start,
                end,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
        }
        matchIndex = start + 1
    }
    return spannableResult
}

/**
 *  Specify the clickable part in the ongoing text in this form: `I am a sentence and [this part] is clickable.`
 *  Where `this part` will end up formatted and clickable.
 *
 *  See [ch.threema.app.compose.common.linkifyWeb] for Compose version returning [androidx.compose.ui.text.AnnotatedString].
 *
 *  @return The spanned string containing the [url] at the specified section in the text, or just this text
 *  without a clickable section, of no `[]`-marker was found.
 *
 */
fun String.linkifyWeb(url: String, onClickLink: (Uri) -> Unit): Spanned {
    val linkTextMatch: MatchResult = Regex("\\[([^}]*)\\]").find(this)
        ?: return SpannedString(this)
    val result = this.replaceFirst("[", "").replaceFirst("]", "")
    return SpannableString(result).apply {
        setSpan(
            /* what = */
            ClickSpan { onClickLink(url.toUri()) },
            /* start = */
            linkTextMatch.range.first.coerceAtLeast(0),
            /* end = */
            (linkTextMatch.range.last - 1).coerceAtMost(length),
            /* flags = */
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
        )
    }.toSpanned()
}

private class ClickSpan(val onClick: () -> Unit) : ClickableSpan() {
    override fun onClick(widget: View) {
        onClick()
    }
}
