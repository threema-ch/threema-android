package ch.threema.app.ui

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.util.AttributeSet
import androidx.annotation.RequiresApi
import androidx.core.view.inputmethod.EditorInfoCompat
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import ch.threema.app.preference.service.KeyboardDataCollectionPolicySetting
import ch.threema.app.utils.DispatcherProvider
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.Koin
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.mp.KoinPlatformTools

open class ThreemaEditText : TextInputEditText, KoinComponent {
    private val sharedPreferences: SharedPreferences by inject()
    private val dispatcherProvider: DispatcherProvider by inject()

    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet?, defStyle: Int) : super(context, attrs, defStyle)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        updatePrivacyFlag()
    }

    private fun updatePrivacyFlag() {
        findViewTreeLifecycleOwner()?.lifecycleScope?.launch {
            if (shouldPreventKeyboardDataCollection()) {
                imeOptions = imeOptions or EditorInfoCompat.IME_FLAG_NO_PERSONALIZED_LEARNING
            }
        }
    }

    private suspend fun shouldPreventKeyboardDataCollection() = withContext(dispatcherProvider.io) {
        sharedPreferences.getBoolean(resources.getString(KeyboardDataCollectionPolicySetting.preferenceKeyStringRes), false)
    }

    override fun onTextContextMenuItem(id: Int): Boolean =
        // Hack to prevent rich text pasting
        super.onTextContextMenuItem(
            if (id == android.R.id.paste) android.R.id.pasteAsPlainText else id,
        )

    @RequiresApi(Build.VERSION_CODES.O)
    override fun getAutofillType(): Int {
        // disable Autofill in EditText due to privacy and TransactionTooLargeException as well as bug https://issuetracker.google.com/issues/67675432
        return AUTOFILL_TYPE_NONE
    }

    override fun dispatchWindowFocusChanged(hasFocus: Boolean) {
        try {
            super.dispatchWindowFocusChanged(hasFocus)
        } catch (_: Exception) {
            // catch Security Exception in com.samsung.android.content.clipboard.SemClipboardManager.getLatestClip() on Samsung devices
        }
    }

    // The following override is needed because default implementations from within the `KoinComponent` interface are not available for Java sources
    override fun getKoin(): Koin = KoinPlatformTools.defaultContext().get()
}
