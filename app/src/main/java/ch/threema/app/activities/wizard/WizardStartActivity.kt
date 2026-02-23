package ch.threema.app.activities.wizard

import android.content.Intent
import android.os.Bundle
import androidx.core.app.NotificationManagerCompat
import ch.threema.app.R
import ch.threema.app.activities.ThreemaAppCompatActivity
import ch.threema.app.backuprestore.csv.RestoreService
import ch.threema.app.services.UserService
import org.koin.android.ext.android.inject

class WizardStartActivity : ThreemaAppCompatActivity() {

    private val userService: UserService by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NotificationManagerCompat.from(this).cancel(RestoreService.RESTORE_COMPLETION_NOTIFICATION_ID)
        launchNextActivity()
    }

    private fun launchNextActivity() {
        val intent = if (userService.hasIdentity()) {
            Intent(this, WizardBaseActivity::class.java)
        } else {
            Intent(this, WizardIntroActivity::class.java)
        }

        startActivity(intent)
        overridePendingTransition(R.anim.abc_fade_in, R.anim.abc_fade_out)
        finish()
    }
}
