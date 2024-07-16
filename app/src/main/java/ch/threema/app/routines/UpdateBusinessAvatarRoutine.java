/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2015-2024 Threema GmbH
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

package ch.threema.app.routines;

import org.slf4j.Logger;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import javax.net.ssl.HttpsURLConnection;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;
import ch.threema.app.services.ApiService;
import ch.threema.app.services.ContactService;
import ch.threema.app.services.FileService;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.ContactUtil;
import ch.threema.app.utils.FileUtil;
import ch.threema.base.utils.LoggingUtil;
import ch.threema.data.models.ContactModel;
import ch.threema.data.models.ContactModelData;

import static android.provider.MediaStore.MEDIA_IGNORE_FILENAME;

/**
 * Update avatars of the business account
 */
public class UpdateBusinessAvatarRoutine implements Runnable {
	private static final Logger logger = LoggingUtil.getThreemaLogger("UpdateBusinessAvatarRoutine");

	private final @NonNull ContactService contactService;
	private final @NonNull FileService fileService;
	private final @NonNull ContactModel contactModel;
	private final @NonNull ApiService apiService;
	private boolean running = false;
	private boolean forceUpdate = false;

	protected UpdateBusinessAvatarRoutine(
		@NonNull ContactService contactService,
		@NonNull FileService fileService,
		@NonNull ContactModel contactModel,
		@NonNull ApiService apiService
	) {
		this.contactService = contactService;
		this.fileService = fileService;
		this.contactModel = contactModel;
		this.apiService = apiService;
	}

	private void forceUpdate() {
		this.forceUpdate = true;
	}

	@Override
	public void run() {
		this.running = true;

		if (!ContactUtil.isGatewayContact(this.contactModel.getIdentity())) {
			logger.error("Contact is not a business account");
			this.running = false;
			return;

		}
		//validate expiry date
		if (!this.forceUpdate) {
			ContactModelData data = contactModel.getData().getValue();
			if (data == null) {
				logger.warn("Contact has been deleted");
				this.running = false;
				return;
			}
			if (!data.isAvatarExpired()) {
				logger.error("Avatar is not expired");
				this.running = false;
				return;
			}
		}

		//define default expiry date (now + 1day)
		Calendar tomorrowCalendar = Calendar.getInstance();
		tomorrowCalendar.setTime(new Date());
		tomorrowCalendar.add(Calendar.DATE, 1);
		Date tomorrow = tomorrowCalendar.getTime();

		try {
			logger.debug("Download Avatar");

			HttpsURLConnection connection = apiService.createAvatarURLConnection(contactModel.getIdentity());

			try {
				// Warning: This may implicitly open an error stream in the 4xx/5xx case!
				connection.connect();
				int responseCode = connection.getResponseCode();
				if (responseCode != HttpsURLConnection.HTTP_OK) {
					if (responseCode == HttpsURLConnection.HTTP_NOT_FOUND) {
						logger.debug("Avatar not found");
						//remove existing avatar
						this.fileService.removeContactAvatar(contactModel.getIdentity());

						//ok, no avatar set
						//add expires date = now + 1day
						this.contactModel.setLocalAvatarExpires(tomorrow);

						this.contactService.clearAvatarCache(contactModel.getIdentity());
					} else if (responseCode == HttpsURLConnection.HTTP_UNAUTHORIZED) {
						 logger.warn("Unauthorized access to avatar server");
						 if (ConfigUtils.isOnPremBuild()) {
							 logger.info("Invalidating auth token");
							 apiService.invalidateAuthToken();
						 }
					}
				} else {
					//cool, save avatar
					logger.debug("Avatar found start download");

					File temporaryFile = this.fileService.createTempFile(MEDIA_IGNORE_FILENAME, "avatardownload-" + this.contactModel.getIdentity().hashCode());
					// this will be useful to display download percentage
					// might be -1: server did not report the length
					int fileLength = connection.getContentLength();
					logger.debug("size: {}", fileLength);

					// download the file
					Date expires = new Date(connection.getHeaderFieldDate("Expires", tomorrow.getTime()));
					logger.debug("expires {}", expires);

					byte[] data = new byte[4096];
					int count;
					try (
						InputStream input = connection.getInputStream();
						FileOutputStream output = new FileOutputStream(temporaryFile.getPath())
					) {

						while ((count = input.read(data)) != -1) {
							//write to file
							output.write(data, 0, count);
						}

						logger.debug("Avatar downloaded");

						//define avatar
						this.contactService.setAvatar(contactModel.getIdentity(), temporaryFile);

						//set expires header
						this.contactModel.setLocalAvatarExpires(expires);
						this.contactService.clearAvatarCache(contactModel.getIdentity());

						//remove temporary file
						FileUtil.deleteFileOrWarn(temporaryFile, "temporaryFile", logger);
					} catch (IOException x) {
						//failed to download
						//do nothing an try again later
						logger.error("Failed to download", x);
					}
				}
			} finally {
				try {
					final InputStream errorStream = connection.getErrorStream();
					if (errorStream != null) {
						errorStream.close();
					}
				} catch (IOException e) {
					// empty
				}
			}
		} catch (Exception x) {
			logger.error("Exception", x);
		}
		this.running = false;
	}

	protected boolean isRunning() {
		return this.running;
	}

	/**
	 * routine states
	 */
	private static final Map<String, UpdateBusinessAvatarRoutine> runningUpdates = new HashMap<>();

	/**
	 * Update (if necessary) a business avatar
	 */
	public static void startUpdate(
		@NonNull ContactModel contactModel,
		@NonNull FileService fileService,
		@NonNull ContactService contactService,
		@NonNull ApiService apiService
	) {
		UpdateBusinessAvatarRoutine instance = createInstance(
			contactModel,
			fileService,
			contactService,
			apiService,
			false
		);
		if (instance != null) {
			//simple start thread!
			Thread thread = new Thread(instance);
			thread.setUncaughtExceptionHandler((thread1, throwable) -> {
				logger.error("Uncaught exception", throwable);
				synchronized (runningUpdates) {
					runningUpdates.remove(contactModel.getIdentity());
				}
			});
			thread.start();
		}
	}

	/**
	 * Update (if necessary) a business avatar
	 * IMPORTANT: this method runs the update routine in the same thread
	 *
	 * @param forceUpdate if true, the expiry date will be ignored
	 */
	@WorkerThread
	public static boolean start(
		@NonNull ContactModel contactModel,
		@NonNull FileService fileService,
		@NonNull ContactService contactService,
		@NonNull ApiService apiService,
		boolean forceUpdate
	) {
		UpdateBusinessAvatarRoutine instance = createInstance(
			contactModel,
			fileService,
			contactService,
			apiService,
			forceUpdate
		);
		if(instance != null) {
			instance.run();
			return true;
		}
		return false;
	}

	private static UpdateBusinessAvatarRoutine createInstance(
		@NonNull ContactModel contactModel,
		@NonNull FileService fileService,
		@NonNull ContactService contactService,
		@NonNull ApiService apiService,
		boolean forceUpdate
	) {
		synchronized (runningUpdates) {
			final String key = contactModel.getIdentity();
			//check if a update is running now
			if (!runningUpdates.containsKey(key)
					|| runningUpdates.get(key) == null
					|| !runningUpdates.get(key).isRunning()) {

				//check if necessary
				if (!forceUpdate) {
					ContactModelData data = contactModel.getData().getValue();
					if (data == null || !data.isAvatarExpired()) {
						logger.warn("Contact has been deleted or avatar is not expired");
						return null;
					}
				}

				logger.debug("Start update business avatar routine");
				UpdateBusinessAvatarRoutine newRoutine = new UpdateBusinessAvatarRoutine(
						contactService,
						fileService,
						contactModel,
						apiService);

				if (forceUpdate) {
					//set force update
					newRoutine.forceUpdate();
				}
				runningUpdates.put(key, newRoutine);

				return newRoutine;
			}
		}

		return null;
	}

}
