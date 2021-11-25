/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2015-2021 Threema GmbH
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
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import javax.net.ssl.HttpsURLConnection;

import ch.threema.app.services.ApiService;
import ch.threema.app.services.ContactService;
import ch.threema.app.services.FileService;
import ch.threema.app.utils.ContactUtil;
import ch.threema.app.utils.FileUtil;
import ch.threema.app.utils.TestUtil;
import ch.threema.storage.models.ContactModel;

import static android.provider.MediaStore.MEDIA_IGNORE_FILENAME;

/**
 * Update avatars of the business account
 */
public class UpdateBusinessAvatarRoutine implements Runnable {
	private static final Logger logger = LoggerFactory.getLogger(UpdateBusinessAvatarRoutine.class);

	private final ContactService contactService;
	private FileService fileService;
	private ContactModel contactModel;
	private final ApiService apiService;
	private boolean running = false;
	private boolean forceUpdate = false;

	protected UpdateBusinessAvatarRoutine(ContactService contactService, FileService fileService, ContactModel contactModel, ApiService apiService) {
		this.contactService = contactService;
		this.fileService = fileService;
		this.contactModel = contactModel;
		this.apiService = apiService;
	}

	protected UpdateBusinessAvatarRoutine forceUpdate() {
		this.forceUpdate = true;
		return this;
	}

	@Override
	public void run() {
		this.running = true;

		//validate instances
		if (!TestUtil.required(this.contactModel, this.contactService, this.fileService)) {
			this.running = false;
			logger.error(": not all required instances defined");
			return;
		}

		if (!ContactUtil.isChannelContact(this.contactModel)) {
			logger.error(": contact is not a business account");
			this.running = false;
			return;

		}
		//validate expiry date
		if (!this.forceUpdate
			&& !ContactUtil.isAvatarExpired(this.contactModel)) {
			logger.error(": avatar is not expired");
			this.running = false;
			return;
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
				boolean avatarModified = false;
				int responseCode = connection.getResponseCode();
				if (responseCode != HttpsURLConnection.HTTP_OK) {
					if (responseCode == HttpsURLConnection.HTTP_NOT_FOUND) {
						logger.debug("Avatar not found");
						//remove existing avatar
						avatarModified = this.fileService.removeContactAvatar(contactModel);

						//ok, no avatar set
						//add expires date = now + 1day
						this.contactModel.setAvatarExpires(tomorrow);

						this.contactService.clearAvatarCache(this.contactModel);
						this.contactService.save(this.contactModel);
					}
				} else {
					//cool, save avatar
					logger.debug("Avatar found start download");

					File temporaryFile = this.fileService.createTempFile(MEDIA_IGNORE_FILENAME, "avatardownload-" + String.valueOf(this.contactModel.getIdentity()).hashCode());
					// this will be useful to display download percentage
					// might be -1: server did not report the length
					int fileLength = connection.getContentLength();
					logger.debug("size: " + fileLength);

					// download the file
					Date expires = new Date(connection.getHeaderFieldDate("Expires", tomorrow.getTime()));
					logger.debug("expires " + expires);

					byte data[] = new byte[4096];
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
						this.contactService.setAvatar(contactModel, temporaryFile);

						//set expires header
						this.contactModel.setAvatarExpires(expires);
						this.contactService.clearAvatarCache(this.contactModel);
						this.contactService.save(this.contactModel);

						//remove temporary file
						FileUtil.deleteFileOrWarn(temporaryFile, "temporaryFile", logger);

						avatarModified = true;
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
	 * Static Stuff
	 */

	/**
	 * routine states
	 */
	private static final Map<String, UpdateBusinessAvatarRoutine> runningUpdates = new HashMap<>();

	/**
	 * Update (if necessary) a business avatar
	 *
	 * @param contactModel
	 * @param fileService
	 * @param contactService
	 * @return
	 */
	public static final boolean startUpdate(ContactModel contactModel,
											FileService fileService,
											ContactService contactService,
											ApiService apiService) {
		return startUpdate(contactModel, fileService, contactService, apiService, false);
	}

	/**
	 * Update (if necessary) a business avatar
	 *
	 * @param contactModel
	 * @param fileService
	 * @param contactService
	 * @param forceUpdate if true, the expiry date will be ignored
	 * @return
	 */
	public static final boolean startUpdate(final ContactModel contactModel,
											FileService fileService,
											ContactService contactService,
											ApiService apiService,
											boolean forceUpdate) {
		UpdateBusinessAvatarRoutine instance = createInstance(contactModel, fileService, contactService, apiService, forceUpdate);
		if(instance != null) {
			//simple start thread!
			Thread thread = new Thread(instance);
			thread.setUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
				@Override
				public void uncaughtException(Thread thread, Throwable throwable) {
					logger.error("Uncaught exception", throwable);
					synchronized (runningUpdates) {
						runningUpdates.remove(contactModel.getIdentity());
					}
				}
			});
			thread.start();

			return thread != null;
		}


		return false;
	}


	/**
	 * Update (if necessary) a business avatar
	 * IMPORTANT: this method run the method in the same thread
	 *
	 * @param contactModel
	 * @param fileService
	 * @param contactService
	 * @param forceUpdate if true, the expiry date will be ignored
	 * @return
	 */
	public static final boolean start(ContactModel contactModel,
											FileService fileService,
											ContactService contactService,
											ApiService apiService,
											boolean forceUpdate) {
		UpdateBusinessAvatarRoutine instance = createInstance(contactModel, fileService, contactService, apiService, forceUpdate);
		if(instance != null) {
			instance.run();
			return true;
		}
		return false;
	}

	private static UpdateBusinessAvatarRoutine createInstance(ContactModel contactModel,
															  FileService fileService,
															  ContactService contactService,
															  ApiService apiService,
															  boolean forceUpdate) {
		synchronized (runningUpdates) {
			final String key = contactModel.getIdentity();
			//check if a update is running now
			if (!runningUpdates.containsKey(key)
					|| runningUpdates.get(key) == null
					|| !runningUpdates.get(key).isRunning()) {

				//check if necessary
				if (!forceUpdate) {
					if (ContactUtil.isAvatarExpired(contactModel)) {
						logger.debug("do not update avatar, not expired");
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
