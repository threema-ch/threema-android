package ch.threema.app.compose.common.text.conversation

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import ch.threema.android.ResolvableString
import ch.threema.app.R
import ch.threema.domain.types.Identity

@Immutable
sealed interface MentionFeature {

    @Immutable
    data object Off : MentionFeature

    @Immutable
    data class On(
        val ownIdentity: Identity,
        val identityDisplayNames: Map<Identity, ResolvableString>,
        val onClickMention: ((Identity) -> Unit)? = null,
        val paddingVertical: TextUnit = 1.sp,
        val cornerRadius: TextUnit = 4.sp,
    ) : MentionFeature {

        companion object {

            internal fun colors(context: Context): MentionSpanColors {
                return MentionSpanColors(
                    background = Color(ContextCompat.getColor(context, R.color.mention_background)),
                    textColor = Color(ContextCompat.getColor(context, R.color.mention_text_color)),
                    textColorAtSign = Color(ContextCompat.getColor(context, R.color.mention_text_color)).copy(
                        alpha = .3f,
                    ),
                )
            }

            internal fun colorsInverted(context: Context): MentionSpanColors {
                return MentionSpanColors(
                    background = Color(ContextCompat.getColor(context, R.color.mention_background_inverted)),
                    textColor = Color(ContextCompat.getColor(context, R.color.mention_text_color_inverted)),
                    textColorAtSign = Color(ContextCompat.getColor(context, R.color.mention_text_color_inverted)).copy(
                        alpha = .3f,
                    ),
                )
            }
        }
    }
}

@Immutable
data class MentionSpanColors(
    val background: Color,
    val textColor: Color,
    val textColorAtSign: Color,
)
