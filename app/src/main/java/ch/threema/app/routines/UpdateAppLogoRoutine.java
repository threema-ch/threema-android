/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2016-2024 Threema GmbH
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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.slf4j.Logger;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Calendar;
import java.util.Date;

import javax.net.ssl.HttpsURLConnection;

import ch.threema.app.services.FileService;
import ch.threema.app.services.PreferenceService;
import ch.threema.app.utils.ConfigUtils;
import ch.threema.app.utils.FileUtil;
import ch.threema.app.utils.TestUtil;
import ch.threema.base.utils.LoggingUtil;

import static android.provider.MediaStore.MEDIA_IGNORE_FILENAME;

/**
 * Update the app icon
 */
public class UpdateAppLogoRoutine implements Runnable {
    private static final Logger logger = LoggingUtil.getThreemaLogger("UpdateAppLogoRoutine");

    private final FileService fileService;
    private final PreferenceService preferenceService;
    private final String lightUrl;
    private final String darkUrl;
    private boolean running = false;
    private final boolean forceUpdate;

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
        logger.debug("start update app logo {}, {}", this.lightUrl, this.darkUrl);
        this.running = true;

        //validate instances
        if (!TestUtil.required(this.fileService, this.preferenceService)) {
            this.running = false;
            logger.error("Not all required instances defined");
            return;
        }

        this.updateLogo(this.lightUrl, ConfigUtils.THEME_LIGHT);
        this.updateLogo(this.darkUrl, ConfigUtils.THEME_DARK);
        this.running = false;
    }

    private void setLogo(@NonNull String url, @NonNull File file, @NonNull Date expires, @ConfigUtils.AppThemeSetting String theme) {
        this.fileService.saveAppLogo(file, theme);
        this.preferenceService.setAppLogo(url, theme);
        this.preferenceService.setAppLogoExpiresAt(expires, theme);
    }

    private void clearLogo(@ConfigUtils.AppThemeSetting String theme) {
        logger.info("Clearing app logo for (forcedUpdate={}, theme={})", forceUpdate, theme);
        this.fileService.saveAppLogo(null, theme);
        this.preferenceService.clearAppLogo(theme);
    }

    private void updateLogo(@Nullable String urlString, @ConfigUtils.AppThemeSetting String theme) {
        logger.info("Update app logo (forcedUpdate={}, theme={})", forceUpdate, theme);
        Date now = new Date();
        //get expires date

        if (TestUtil.isEmptyOrNull(urlString)) {
            this.clearLogo(theme);
            return;
        }
        // Check expiry date only if update is not forced
        if (!this.forceUpdate) {
            Date expiresAt = this.preferenceService.getAppLogoExpiresAt(theme);

            if (expiresAt != null && now.before(expiresAt)) {
                logger.info("Logo not expired");
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
            logger.info("Download {}", urlString);

            URL url = new URL(urlString);
            HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
            connection.setSSLSocketFactory(ConfigUtils.getSSLSocketFactory(url.getHost()));

            try {
                // Warning: This may implicitly open an error stream in the 4xx/5xx case!
                connection.connect();
                final int responseCode = connection.getResponseCode();
                if (responseCode != HttpsURLConnection.HTTP_OK) {
                    if (responseCode == HttpsURLConnection.HTTP_NOT_FOUND) {
                        logger.warn("Logo not found");
                    } else {
                        logger.warn("Connection failed with response code {}", responseCode);
                    }
                } else {
                    logger.debug("Logo found. Start download");

                    File temporaryFile = this.fileService.createTempFile(MEDIA_IGNORE_FILENAME, "appicon");
                    // this will be useful to display download percentage
                    // might be -1: server did not report the length
                    int fileLength = connection.getContentLength();
                    logger.debug("size: {}", fileLength);

                    // download the file
                    try (InputStream input = connection.getInputStream()) {
                        Date expires = new Date(connection.getHeaderFieldDate("Expires", tomorrow.getTime()));
                        logger.debug("expires {}", expires);

                        try (FileOutputStream output = new FileOutputStream(temporaryFile.getPath())) {
                            byte[] data = new byte[4096];
                            int count;

                            while ((count = input.read(data)) != -1) {
                                output.write(data, 0, count);
                            }

                            logger.info("Logo downloaded. Expires at {}.", expires);
                            output.close();

                            this.setLogo(urlString, temporaryFile, expires, theme);

                            FileUtil.deleteFileOrWarn(temporaryFile, "temporary file", logger);

                        } catch (IOException x) {
                            // Failed to download
                            // do nothing an try again later
                            logger.error("Download of app logo failed", x);
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
                    // Ignored
                }
            }
        } catch (Exception x) {
            logger.error("Update of app logo failed", x);
        }

    }

    protected boolean isRunning() {
        return this.running;
    }
}
