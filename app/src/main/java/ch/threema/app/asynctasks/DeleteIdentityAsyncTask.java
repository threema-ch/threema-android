/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2018-2021 Threema GmbH
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

import com.google.firebase.iid.FirebaseInstanceId;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;

import androidx.fragment.app.FragmentManager;
import ch.threema.app.R;
import ch.threema.app.ThreemaApplication;
import ch.threema.app.dialogs.GenericProgressDialog;
import ch.threema.app.managers.ServiceManager;
import ch.threema.app.services.PassphraseService;
import ch.threema.app.utils.DialogUtil;
import ch.threema.app.utils.SecureDeleteUtil;
import ch.threema.app.webclient.services.SessionWakeUpServiceImpl;
import ch.threema.app.webclient.services.instance.DisconnectContext;
import ch.threema.base.ThreemaException;
import ch.threema.client.ProtocolDefines;
import ch.threema.client.ThreemaConnection;
import ch.threema.storage.DatabaseServiceNew;
import ch.threema.storage.NonceDatabaseBlobService;

public class DeleteIdentityAsyncTask extends AsyncTask<Void, Void, Exception> {
	private static final Logger logger = LoggerFactory.getLogger(DeleteIdentityAsyncTask.class);

	private static final String DIALOG_TAG_DELETING_ID = "di";

	private final ServiceManager serviceManager;
	private final FragmentManager fragmentManager;
	private final Runnable runOnCompletion;

	public DeleteIdentityAsyncTask(FragmentManager fragmentManager,
	                               Runnable runOnCompletion) {

		this.serviceManager = ThreemaApplication.getServiceManager();
		this.fragmentManager = fragmentManager;
		this.runOnCompletion = runOnCompletion;
	}

	@Override
	protected void onPreExecute() {
		GenericProgressDialog.newInstance(R.string.delete_id_title, R.string.please_wait).show(fragmentManager, DIALOG_TAG_DELETING_ID);
	}

	@Override
	protected Exception doInBackground(Void... params) {
		ThreemaConnection connection = serviceManager.getConnection();

		try {
			// clear push token
			FirebaseInstanceId.getInstance().deleteInstanceId();
			if (connection != null) {
				try {
					connection.setPushToken(ProtocolDefines.PUSHTOKEN_TYPE_GCM, "");
				} catch (ThreemaException e) {
					// ignore inability to clear push token due to missing connectivity
					logger.debug("Exception", e);
				}
			}

			serviceManager.getThreemaSafeService().unscheduleUpload();
			serviceManager.getMessageService().removeAll();
			serviceManager.getConversationService().reset();
			serviceManager.getGroupService().removeAll();
			serviceManager.getContactService().removeAll();
			serviceManager.getUserService().removeIdentity();
			serviceManager.getDistributionListService().removeAll();
			serviceManager.getBallotService().removeAll();
			serviceManager.getPreferenceService().clear();
			serviceManager.getFileService().removeAllAvatars();
			serviceManager.getWallpaperService().removeAll(ThreemaApplication.getAppContext(), true);

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

			try {
				ThreemaApplication.getMasterKey().setPassphrase(null);
			} catch (Exception e) {
				//
			}

			File aesFile = new File(ThreemaApplication.getAppContext().getFilesDir(), ThreemaApplication.AES_KEY_FILE);
			File databaseFile = ThreemaApplication.getAppContext().getDatabasePath(DatabaseServiceNew.DATABASE_NAME_V4);
			File nonceDatabaseFile = ThreemaApplication.getAppContext().getDatabasePath(NonceDatabaseBlobService.DATABASE_NAME_V4);
			File backupFile = ThreemaApplication.getAppContext().getDatabasePath(DatabaseServiceNew.DATABASE_NAME_V4 + DatabaseServiceNew.DATABASE_BACKUP_EXT);
			File cacheDirectory = ThreemaApplication.getAppContext().getCacheDir();
			File externalCacheDirectory = ThreemaApplication.getAppContext().getExternalCacheDir();

			secureDelete(aesFile);
			secureDelete(databaseFile);
			secureDelete(nonceDatabaseFile);
			secureDelete(backupFile);
			secureDelete(cacheDirectory);
			secureDelete(externalCacheDirectory);

			if (PassphraseService.isRunning()) {
				PassphraseService.stop(ThreemaApplication.getAppContext());
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
		DialogUtil.dismissDialog(fragmentManager, DIALOG_TAG_DELETING_ID, true);
		if (exception != null) {
			Toast.makeText(ThreemaApplication.getAppContext(), exception.getMessage(), Toast.LENGTH_LONG).show();
		} else {
			if (runOnCompletion != null) {
				runOnCompletion.run();
			}
		}
	}

	private void secureDelete(File file) {
		try {
			SecureDeleteUtil.secureDelete(file);
		} catch (IOException e) {
			logger.error("Exception", e);
		}
	}
}
