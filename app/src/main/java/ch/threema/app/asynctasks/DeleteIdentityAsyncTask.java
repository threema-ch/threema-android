/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2018-2025 Threema GmbH
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

package ch.threema.app.asynctasks;

import android.os.AsyncTask;
import android.widget.Toast;

import org.koin.java.KoinJavaComponent;
import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentManager;
import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.dialogs.GenericProgressDialog;
import ch.threema.app.managers.ServiceManager;
import ch.threema.app.push.PushService;
import ch.threema.app.services.PassphraseService;
import ch.threema.app.utils.DialogUtil;
import ch.threema.app.utils.SecureDeleteUtil;
import ch.threema.app.utils.ShortcutUtil;
import ch.threema.app.utils.executor.BackgroundExecutor;
import ch.threema.app.webclient.services.SessionWakeUpServiceImpl;
import ch.threema.app.webclient.services.instance.DisconnectContext;
import ch.threema.base.ThreemaException;
import static ch.threema.base.utils.LoggingKt.getThreemaLogger;
import ch.threema.domain.onprem.OnPremConfigStore;
import ch.threema.localcrypto.MasterKeyFileProvider;
import ch.threema.storage.DatabaseNonceStore;
import ch.threema.storage.DatabaseService;

public class DeleteIdentityAsyncTask extends AsyncTask<Void, Void, Exception> {
    private static final Logger logger = getThreemaLogger("DeleteIdentityAsyncTask");

    private static final String DIALOG_TAG_DELETING_ID = "di";

    private final ServiceManager serviceManager;
    private final FragmentManager fragmentManager;
    private final Runnable runOnCompletion;
    private final BackgroundExecutor backgroundExecutor = new BackgroundExecutor();

    public DeleteIdentityAsyncTask(@Nullable FragmentManager fragmentManager,
                                   @Nullable Runnable runOnCompletion) {

        this.serviceManager = ThreemaApplication.getServiceManager();
        this.fragmentManager = fragmentManager;
        this.runOnCompletion = runOnCompletion;
    }

    @Override
    protected void onPreExecute() {
        if (fragmentManager != null) {
            GenericProgressDialog.newInstance(R.string.delete_id_title, R.string.please_wait).show(fragmentManager, DIALOG_TAG_DELETING_ID);
        }
    }

    @Override
    protected Exception doInBackground(Void... params) {
        try {
            var context = ThreemaApplication.getAppContext();

            // clear push token
            PushService.deleteToken(context);

            serviceManager.getThreemaSafeService().unschedulePeriodicUpload();
            serviceManager.getMessageService().removeAll();
            serviceManager.getConversationService().reset();
            serviceManager.getGroupService().removeAll();
            backgroundExecutor.execute(getDeleteAllContactsTask());
            try {
                serviceManager.getUserService().removeIdentity();
            } catch (Exception ignored) {
            }
            serviceManager.getDistributionListService().removeAll();
            serviceManager.getBallotService().removeAll();
            serviceManager.getPreferenceService().clear();
            serviceManager.getFileService().removeAllAvatars();
            try {
                serviceManager.getWallpaperService().deleteAll();
            } catch (IOException e) {
                logger.error("Failed to deleted wallpapers", e);
            }
            ShortcutUtil.deleteAllShareTargetShortcuts(serviceManager.getPreferenceService());
            ShortcutUtil.deleteAllPinnedShortcuts();

            boolean interrupted = false;

            try {
                serviceManager.getConnection().stop();
            } catch (InterruptedException ignored) {
                // This is important, don't let ourselves be interrupted, otherwise
                // incomplete data may remain on the file system.
                interrupted = true;
            }

            //webclient cleanup
            serviceManager.getWebClientServiceManager().getSessionService().stopAll(
                DisconnectContext.byUs(DisconnectContext.REASON_SESSION_DELETED));
            SessionWakeUpServiceImpl.clear();

            MasterKeyFileProvider masterKeyFileProvider = KoinJavaComponent.get(MasterKeyFileProvider.class);
            File masterKeyFile = masterKeyFileProvider.getVersion2MasterKeyFile();
            File databaseFile = DatabaseService.getDatabaseFile(context);
            File nonceDatabaseFile = DatabaseNonceStore.getDatabaseFile(context);
            File backupFile = DatabaseService.getDatabaseBackupFile(context);
            File cacheDirectory = context.getCacheDir();
            File externalCacheDirectory = context.getExternalCacheDir();
            OnPremConfigStore onPremConfigStore = KoinJavaComponent.getOrNull(OnPremConfigStore.class);
            if (onPremConfigStore != null) {
                onPremConfigStore.reset();
            }

            secureDelete(masterKeyFile);
            secureDelete(databaseFile);
            secureDelete(nonceDatabaseFile);
            secureDelete(backupFile);
            secureDelete(cacheDirectory);
            secureDelete(externalCacheDirectory);

            if (PassphraseService.isRunning()) {
                PassphraseService.stop(context);
            }

            if (interrupted) {
                // An InterruptedException was caught. Re-set the interruption flag.
                Thread.currentThread().interrupt();
            }
        } catch (Exception e) {
            logger.error("Exception", e);
            return e;
        }

        return null;
    }

    @Override
    protected void onPostExecute(Exception exception) {
        if (fragmentManager != null) {
            DialogUtil.dismissDialog(fragmentManager, DIALOG_TAG_DELETING_ID, true);
        }
        if (exception != null) {
            Toast.makeText(ThreemaApplication.getAppContext(), exception.getMessage(), Toast.LENGTH_LONG).show();
        } else {
            if (runOnCompletion != null) {
                runOnCompletion.run();
            }
        }
    }

    @NonNull
    private DeleteAllContactsBackgroundTask getDeleteAllContactsTask() throws ThreemaException {
        return new DeleteAllContactsBackgroundTask(
            serviceManager.getModelRepositories().getContacts(),
            new DeleteContactServices(
                serviceManager.getUserService(),
                serviceManager.getContactService(),
                serviceManager.getConversationService(),
                serviceManager.getRingtoneService(),
                serviceManager.getConversationCategoryService(),
                serviceManager.getProfilePicRecipientsService(),
                serviceManager.getWallpaperService(),
                serviceManager.getFileService(),
                serviceManager.getExcludedSyncIdentitiesService(),
                serviceManager.getDHSessionStore(),
                serviceManager.getNotificationService(),
                serviceManager.getDatabaseService()
            )
        );
    }

    private void secureDelete(@Nullable File file) {
        try {
            SecureDeleteUtil.secureDelete(file);
        } catch (IOException e) {
            logger.error("Exception", e);
        }
    }
}
