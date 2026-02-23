package ch.threema.app.activities.wizard.components

import android.content.Context
import android.util.AttributeSet
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.AbstractComposeView
import ch.threema.app.R
import ch.threema.app.compose.theme.ThreemaTheme

class WizardButtonXml @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : AbstractComposeView(context, attrs, defStyleAttr) {
    var text: String by mutableStateOf("")
    var style: WizardButtonStyle by mutableStateOf(defaultButtonStyle)
    var isButtonEnabled: Boolean by mutableStateOf(true)
    var trailingIconRes: Int? by mutableStateOf(null)

    init {
        val typedArray = context.theme.obtainStyledAttributes(
            attrs,
            R.styleable.WizardButtonXmlWrapper,
            defStyleAttr,
            0,
        )
        with(typedArray) {
            getResourceId(R.styleable.WizardButtonXmlWrapper_wizardButton_text, NO_RES_ID).let { initialTextResId ->
                if (initialTextResId != NO_RES_ID) {
                    text = context.getString(initialTextResId)
                }
            }
            getResourceId(R.styleable.WizardButtonXmlWrapper_wizardButton_trailingIcon, NO_RES_ID).let { initialTrailingIconResId ->
                if (initialTrailingIconResId != NO_RES_ID) {
                    trailingIconRes = initialTrailingIconResId
                }
            }
            getInt(R.styleable.WizardButtonXmlWrapper_wizardButton_style, defaultButtonStyle.ordinal).let { initialStyle ->
                if (initialStyle != style.ordinal) {
                    style = WizardButtonStyle.entries[initialStyle]
                }
            }
            recycle()
        }
    }

    @Composable
    override fun Content() {
        ThreemaTheme(isDarkTheme = true) {
            WizardButton(
                text = text,
                style = style,
                trailingIconRes = trailingIconRes,
                isEnabled = isButtonEnabled,
                onClick = {
                    super.performClick()
                },
            )
        }
    }

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        isButtonEnabled = enabled
    }

    companion object {
        private val defaultButtonStyle = WizardButtonStyle.DEFAULT
        private const val NO_RES_ID = 0
    }
}
