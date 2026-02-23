package ch.threema.app.utils.contracts

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContract
import ch.threema.app.utils.RingtoneUtil

object PickRingtoneContract : ActivityResultContract<PickRingtoneContract.Input, Uri?>() {
    override fun createIntent(context: Context, input: Input): Intent =
        with(input) {
            RingtoneUtil.getRingtonePickerIntent(type, currentUri, defaultUri)
        }

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? =
        intent
            ?.takeIf { resultCode == Activity.RESULT_OK }
            ?.run {
                getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI) ?: Uri.EMPTY
            }

    data class Input(val type: Int, val currentUri: Uri?, val defaultUri: Uri?)
}

fun ActivityResultLauncher<PickRingtoneContract.Input>.launch(
    type: Int,
    currentUri: Uri?,
    defaultUri: Uri?,
) {
    launch(PickRingtoneContract.Input(type, currentUri, defaultUri))
}
