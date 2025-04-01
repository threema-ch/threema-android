/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2013-2024 Threema GmbH
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

package ch.threema.app.activities;

import android.content.Intent;
import android.content.res.Configuration;
import android.os.AsyncTask;
import android.os.Bundle;

import org.slf4j.Logger;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.DialogFragment;
import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.dialogs.GenericProgressDialog;
import ch.threema.app.dialogs.PasswordEntryDialog;
import ch.threema.app.managers.ServiceManager;
import ch.threema.app.services.PreferenceService;
import ch.threema.app.services.UserService;
import ch.threema.app.utils.DialogUtil;
import ch.threema.app.utils.RuntimeUtil;
import ch.threema.base.ThreemaException;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.domain.identitybackup.IdentityBackupGenerator;

public class ExportIDActivity extends AppCompatActivity implements PasswordEntryDialog.PasswordEntryDialogClickListener {
    private static final Logger logger = LoggingUtil.getThreemaLogger("ExportIDActivity");

    private static final String DIALOG_TAG_SET_ID_BACKUP_PW = "setIDBackupPW";
    private static final String DIALOG_PROGRESS_ID = "idBackup";
    private PreferenceService preferenceService;
    private UserService userService;
    private String identity;

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final ServiceManager serviceManager = ThreemaApplication.getServiceManager();
        preferenceService = serviceManager.getPreferenceService();
        userService = serviceManager.getUserService();

        if (userService == null || preferenceService == null) {
            logger.error("services not available", this);
            this.finish();
        }

        this.identity = userService.getIdentity();
        DialogFragment dialogFragment = PasswordEntryDialog.newInstance(
            R.string.backup_title,
            R.string.backup_password_summary,
            R.string.password_hint,
            R.string.ok,
            R.string.cancel,
            ThreemaApplication.MIN_PW_LENGTH_BACKUP,
            ThreemaApplication.MAX_PW_LENGTH_BACKUP,
            R.string.backup_password_again_summary,
            0, 0, PasswordEntryDialog.ForgotHintType.NONE);
        dialogFragment.show(getSupportFragmentManager(), DIALOG_TAG_SET_ID_BACKUP_PW);
    }

    private void displayIDBackup(String result) {
        Intent intent = new Intent(this, ExportIDResultActivity.class);
        intent.putExtra(ThreemaApplication.INTENT_DATA_ID_BACKUP, result);
        intent.putExtra(ThreemaApplication.INTENT_DATA_CONTACT, identity);

        startActivity(intent);
        finish();
    }

    private void createIDBackup(final String password) {
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected void onPreExecute() {
                super.onPreExecute();

                GenericProgressDialog.newInstance(R.string.generating_backup_data, R.string.please_wait)
                    .show(getSupportFragmentManager(), DIALOG_PROGRESS_ID);
            }

            @Override
            protected Void doInBackground(Void... params) {
                IdentityBackupGenerator identityBackupGenerator = new IdentityBackupGenerator(identity, userService.getPrivateKey());
                try {
                    final String result = identityBackupGenerator.generateBackup(password);
                    preferenceService.incrementIDBackupCount();

                    RuntimeUtil.runOnUiThread(() -> displayIDBackup(result));
                } catch (ThreemaException e) {
                    logger.debug("no idbackup");
                }
                return null;
            }

            @Override
            protected void onPostExecute(Void aVoid) {
                super.onPostExecute(aVoid);

                DialogUtil.dismissDialog(getSupportFragmentManager(), DIALOG_PROGRESS_ID, true);
            }
        }.execute();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        // We override this method to avoid restarting the entire
        // activity when the keyboard is opened or orientation changes
        super.onConfigurationChanged(newConfig);

    }

    @Override
    public void onYes(String tag, String text, boolean isChecked, Object data) {
        switch (tag) {
            case DIALOG_TAG_SET_ID_BACKUP_PW:
                createIDBackup(text);
                break;
        }
    }

    @Override
    public void onNo(String tag) {
        finish();
    }
}
