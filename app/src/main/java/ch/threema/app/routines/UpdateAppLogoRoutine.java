/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2016-2021 Threema GmbH
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
import java.net.URL;
import java.util.Calendar;
import java.util.Date;

import javax.net.ssl.HttpsURLConnection;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import ch.threema.app.services.FileService;
import ch.threema.app.services.PreferenceService;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.ConfigUtils.AppTheme;
import ch.threema.app.utils.FileUtil;
import ch.threema.app.utils.TestUtil;

import static android.provider.MediaStore.MEDIA_IGNORE_FILENAME;

/**
 * Update the app icon
 */
public class UpdateAppLogoRoutine implements Runnable {
	private static final Logger logger = LoggerFactory.getLogger(UpdateAppLogoRoutine.class);

	private FileService fileService;
	private final PreferenceService preferenceService;
	private final String lightUrl;
	private final String darkUrl;
	private boolean running = false;
	private boolean forceUpdate = false;

	public UpdateAppLogoRoutine(FileService fileService,
	                            PreferenceService preferenceService,
	                            @Nullable String lightUrl,
	                            @Nullable String darkUrl,
	                            boolean forceUpdate) {
		this.fileService = fileService;
		this.preferenceService = preferenceService;
		this.lightUrl = lightUrl;
		this.darkUrl = darkUrl;
		this.forceUpdate = forceUpdate;
	}

	@Override
	public void run() {
		logger.debug("start update app logo " + this.lightUrl + ", " + this.darkUrl);
		this.running = true;

		//validate instances
		if(!TestUtil.required(this.fileService, this.preferenceService)) {
			this.running = false;
			logger.error("Not all required instances defined");
			return;
		}

		this.downloadLogo(this.lightUrl, ConfigUtils.THEME_LIGHT);
		this.downloadLogo(this.darkUrl, ConfigUtils.THEME_DARK);
		this.running = false;
	}

	private void setLogo(@NonNull String url, @NonNull File file, @NonNull Date expires, @AppTheme int theme) {
		this.fileService.saveAppLogo(file, theme);
		this.preferenceService.setAppLogo(url, theme);
		this.preferenceService.setAppLogoExpiresAt(expires, theme);
	}

	private void clearLogo(@AppTheme int theme) {
		this.fileService.saveAppLogo(null, theme);
		this.preferenceService.clearAppLogo(theme);
	}

	private void downloadLogo(@Nullable String urlString, @AppTheme int theme) {

		logger.debug("Logo download forced = " + forceUpdate);

		Date now = new Date();
		//get expires date

		if(TestUtil.empty(urlString)) {
			this.clearLogo(theme);
			return;
		}
		//check expiry date only on force update
		if(!this.forceUpdate) {
			Date expiresAt = this.preferenceService.getAppLogoExpiresAt(theme);

			if (expiresAt != null && now.before(expiresAt)) {
				logger.debug("Logo not expired");
				//do nothing!
				return;
			}
		}

		//define default expiry date (now + 1day)
		Calendar tomorrowCalendar = Calendar.getInstance();
		tomorrowCalendar.setTime(now);
		tomorrowCalendar.add(Calendar.DATE, 1);
		Date tomorrow = tomorrowCalendar.getTime();

		try {
			logger.debug("Download " + urlString);

			URL url = new URL(urlString);
			HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
			connection.setSSLSocketFactory(ConfigUtils.getSSLSocketFactory(url.getHost()));

			try {
				// Warning: This may implicitly open an error stream in the 4xx/5xx case!
				connection.connect();
				final int responseCode = connection.getResponseCode();
				if (responseCode != HttpsURLConnection.HTTP_OK) {
					if (responseCode == HttpsURLConnection.HTTP_NOT_FOUND) {
						logger.debug("Logo not found");
					}
				} else {
					//cool, save avatar
					logger.debug("Logo found. Start download");

					File temporaryFile = this.fileService.createTempFile(MEDIA_IGNORE_FILENAME, "appicon");
					// this will be useful to display download percentage
					// might be -1: server did not report the length
					int fileLength = connection.getContentLength();
					logger.debug("size: " + fileLength);

					// download the file
					try (InputStream input = connection.getInputStream()) {
						Date expires = new Date(connection.getHeaderFieldDate("Expires", tomorrow.getTime()));
						logger.debug("expires " + expires);

						try (FileOutputStream output = new FileOutputStream(temporaryFile.getPath())) {
							byte[] data = new byte[4096];
							int count;

							while ((count = input.read(data)) != -1) {
								//write to file
								output.write(data, 0, count);
							}

							logger.debug("Logo downloaded");
							output.close();

							//ok, save the app logo
							this.setLogo(urlString, temporaryFile, expires, theme);

							//remove the temporary file
							FileUtil.deleteFileOrWarn(temporaryFile, "temporary file", logger);

						} catch (IOException x) {
							//failed to download
							//do nothing an try again later
							logger.error("Exception", x);
						}
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

	}
	protected boolean isRunning() {
		return this.running;
	}
}
