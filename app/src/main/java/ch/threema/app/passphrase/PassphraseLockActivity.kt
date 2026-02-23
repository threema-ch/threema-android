package ch.threema.app.passphrase

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import ch.threema.android.buildActivityIntent
import ch.threema.localcrypto.MasterKeyManager
import org.koin.android.ext.android.inject

/**
 * This activity is used to lock the master key with the currently configured passphrase.
 * It will be called from the persistent notification when the user wishes to lock the app.
 * When started, it will lock the master key.
 */
class PassphraseLockActivity : ComponentActivity() {

    private val masterKeyManager: MasterKeyManager by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (masterKeyManager.isProtectedWithPassphrase() && !masterKeyManager.isLockedWithPassphrase()) {
            masterKeyManager.lockWithPassphrase()
        }
        finishAndRemoveTask()
    }

    companion object {
        @JvmStatic
        fun createIntent(context: Context) = buildActivityIntent<PassphraseLockActivity>(context)
    }
}
