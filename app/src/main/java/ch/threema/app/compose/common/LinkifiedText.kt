package ch.threema.app.compose.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import ch.threema.app.compose.typography.SpanStyles

/**
 * Using this over [linkifyWeb] will ensure no unnecessary re-computation will happen during re-compositions.
 *
 * Changing the value of [url] and/or [linkTextStyle] will trigger a re-computation of the [AnnotatedString].
 */
@Composable
fun String.rememberLinkifyWeb(url: String, linkTextStyle: SpanStyle = SpanStyles.linkPrimary): AnnotatedString =
    remember(url, linkTextStyle) {
        linkifyWeb(url, linkTextStyle)
    }

/**
 *  Specify the clickable part in the ongoing text in this form:
 *  `I am a sentence and [this part] is clickable.` Where `this part` will end up formatted
 *  by [linkStyle] and open the specified [url] in a web browser.
 *
 *  @param url Using [androidx.compose.ui.platform.UriHandler] to try to open this url in a web browser. Phone numbers,
 *  email addresses or other URIs will not be handled.
 *
 *  @return The annotated string containing the [url] at the specified section in the text, or just this text
 *  without a clickable section, if no `[]`-marker was found.
 */
fun String.linkifyWeb(url: String, linkStyle: SpanStyle): AnnotatedString {
    val linkTextMatch: MatchResult = Regex("\\[([^}]*)\\]").find(this)
        ?: return AnnotatedString(this)
    val linkText: String = linkTextMatch.groupValues.getOrNull(1)
        ?: return AnnotatedString(this)

    val prefix: String = substring(
        startIndex = 0,
        endIndex = linkTextMatch.range.first,
    )
    val suffix: String = substring(
        startIndex = (linkTextMatch.range.last + 1).coerceAtMost(this.length),
        endIndex = this.length,
    )

    return buildAnnotatedString {
        append(prefix)
        withLink(
            LinkAnnotation.Url(
                url = url,
                styles = TextLinkStyles(linkStyle),
            ),
        ) {
            append(linkText)
        }
        append(suffix)
    }
}
