/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2013-2025 Threema GmbH
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

package ch.threema.app.activities

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import ch.threema.android.buildActivityIntent
import ch.threema.android.showToast
import ch.threema.app.AppConstants
import ch.threema.app.R
import ch.threema.app.dialogs.GenericProgressDialog
import ch.threema.app.dialogs.PasswordEntryDialog
import ch.threema.app.dialogs.PasswordEntryDialog.PasswordEntryDialogClickListener
import ch.threema.app.preference.service.PreferenceService
import ch.threema.app.services.UserService
import ch.threema.app.startup.finishAndRestartLaterIfNotReady
import ch.threema.app.utils.DialogUtil
import ch.threema.app.utils.DispatcherProvider
import ch.threema.app.utils.logScreenVisibility
import ch.threema.base.utils.getThreemaLogger
import ch.threema.domain.identitybackup.IdentityBackup
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject

private val logger = getThreemaLogger("ExportIDActivity")

class ExportIDActivity : AppCompatActivity(), PasswordEntryDialogClickListener {
    init {
        logScreenVisibility(logger)
    }

    private val preferenceService: PreferenceService by inject()
    private val userService: UserService by inject()
    private val dispatcherProvider: DispatcherProvider by inject()

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (finishAndRestartLaterIfNotReady()) {
            return
        }

        val dialogFragment = PasswordEntryDialog.newInstance(
            /* title = */
            R.string.backup_title,
            /* message = */
            R.string.backup_password_summary,
            /* hint = */
            R.string.password_hint,
            /* positive = */
            R.string.ok,
            /* negative = */
            R.string.cancel,
            /* minLength = */
            AppConstants.MIN_PW_LENGTH_BACKUP,
            /* maxLength = */
            AppConstants.MAX_PW_LENGTH_BACKUP,
            /* confirmHint = */
            R.string.backup_password_again_summary,
            /* inputType = */
            0,
            /* checkboxText = */
            0,
            /* showForgotPwHint = */
            PasswordEntryDialog.ForgotHintType.NONE,
        )
        dialogFragment.show(supportFragmentManager, DIALOG_TAG_SET_ID_BACKUP_PW)
    }

    override fun onYes(tag: String, text: String, isChecked: Boolean, data: Any?) {
        when (tag) {
            DIALOG_TAG_SET_ID_BACKUP_PW -> createIDBackup(text)
        }
    }

    override fun onNo(tag: String) {
        finish()
    }

    private fun createIDBackup(password: String) {
        lifecycleScope.launch {
            showProgressDialog()

            try {
                withContext(dispatcherProvider.worker) {
                    val identityBackup = try {
                        IdentityBackup.encryptIdentityBackup(
                            password,
                            IdentityBackup.PlainBackupData(
                                userService.identity!!,
                                userService.privateKey,
                            ),
                        )
                    } catch (e: Exception) {
                        logger.error("Failed to generate backup", e)
                        showToast(R.string.an_error_occurred)
                        return@withContext
                    }

                    preferenceService.incrementIDBackupCount()

                    withContext(dispatcherProvider.main) {
                        displayIdentityBackup(identityBackup.data)
                    }
                }
            } finally {
                hideProgressDialog()
            }
        }
    }

    private fun showProgressDialog() {
        GenericProgressDialog.newInstance(R.string.generating_backup_data, R.string.please_wait)
            .show(supportFragmentManager, DIALOG_PROGRESS_ID)
    }

    private fun hideProgressDialog() {
        DialogUtil.dismissDialog(supportFragmentManager, DIALOG_PROGRESS_ID, true)
    }

    private fun displayIdentityBackup(result: String) {
        val intent = Intent(this, ExportIDResultActivity::class.java)
            .putExtra(AppConstants.INTENT_DATA_ID_BACKUP, result)
            .putExtra(AppConstants.INTENT_DATA_CONTACT, userService.identity)

        startActivity(intent)
        finish()
    }

    companion object {
        private const val DIALOG_TAG_SET_ID_BACKUP_PW = "setIDBackupPW"
        private const val DIALOG_PROGRESS_ID = "idBackup"

        @JvmStatic
        fun createIntent(context: Context) = buildActivityIntent<ExportIDActivity>(context)
    }
}
