package ch.threema.app.voip.activities

import android.content.Context
import android.widget.ImageView
import androidx.lifecycle.lifecycleScope
import ch.threema.app.services.ContactService
import ch.threema.app.utils.BitmapUtil
import ch.threema.app.utils.DispatcherProvider
import ch.threema.domain.types.IdentityString
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * This class only exists to allow the use of Kotlin-only functionality such as coroutines from the Java class [CallActivity].
 */
class CallActivityHelper(
    private val appContext: Context,
    private val contactService: ContactService,
    private val dispatcherProvider: DispatcherProvider,
) {
    fun setBlurredBackground(
        activity: CallActivity,
        backgroundView: ImageView,
        contactIdentity: IdentityString,
    ) {
        activity.lifecycleScope.launch {
            val blurredProfilePicture = withContext(dispatcherProvider.worker) {
                val profilePicture = contactService.getAvatar(contactIdentity, true)
                BitmapUtil.blurBitmap(profilePicture, appContext)
            }
            backgroundView.setImageBitmap(blurredProfilePicture)
        }
    }
}
