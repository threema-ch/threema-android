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

package ch.threema.app.threemasafe;

import android.app.Activity;
import android.text.format.DateUtils;

import java.io.IOException;
import java.util.ArrayList;

import androidx.annotation.Nullable;
import ch.threema.base.ThreemaException;

public interface ThreemaSafeService {

    int ERROR_CODE_OK = 0;
    int ERROR_CODE_SERVER_FAIL = 1;
    int ERROR_CODE_JSON_FAIL = 2;
    int ERROR_CODE_GZIP_FAIL = 3;
    int ERROR_CODE_UPLOAD_FAIL = 4;
    int ERROR_CODE_HASH_FAIL = 5;
    int ERROR_CODE_SIZE_EXCEEDED = 6;

    long SCHEDULE_PERIOD = DateUtils.DAY_IN_MILLIS;

    int BACKUP_ID_LENGTH = 32;

    /**
     * This exception is thrown if creating and uploading the Threema Safe backup fails.
     */
    class ThreemaSafeUploadException extends ThreemaException {
        private final boolean uploadNeeded;

        public ThreemaSafeUploadException(String msg, boolean uploadNeeded) {
            super(msg);
            this.uploadNeeded = uploadNeeded;
        }

        /**
         * Check whether the backup potentially should have been uploaded to the server.
         *
         * @return {@code false} if the backup should not be uploaded, {@code true} if it
         * potentially should be uploaded.
         */
        public boolean isUploadNeeded() {
            return uploadNeeded;
        }
    }

    @Nullable
    byte[] deriveMasterKey(@Nullable String password, @Nullable String identity);

    boolean storeMasterKey(byte[] masterKey);

    byte[] getThreemaSafeBackupId();

    byte[] getThreemaSafeEncryptionKey();

    byte[] getThreemaSafeMasterKey();

    ThreemaSafeServerTestResponse testServer(ThreemaSafeServerInfo serverInfo) throws ThreemaException;

    /**
     * Schedules the Threema Safe backup to run periodically. There is an initial delay of one
     * period, therefore the backup is not run immediately after calling this. This does not replace
     * existing periodic Threema Safe backup work. Therefore the periodic execution cycle is not
     * affected by this method. However, if the schedule period changes, this call cancels the
     * currently scheduled upload and schedules a new upload with the new schedule period and again
     * an initial delay.
     * <p>
     * Periodic Threema Safe backups are only uploaded if they are different than the last
     * successful backup.
     *
     * @return {@code true} if the backup has been scheduled successfully, {@code false} otherwise
     */
    boolean schedulePeriodicUpload();

    /**
     * Reschedule the Threema Safe backup to run with an initial delay of one period from now on.
     * Existing periodic work is canceled.
     *
     * @return {@code true} if the backup has been rescheduled successfully, {@code false} otherwise
     */
    boolean reschedulePeriodicUpload();

    /**
     * Cancel the periodic Threema Safe work.
     */
    void unschedulePeriodicUpload();

    /**
     * Enable or disable the Threema Safe backup. If the backup is being enabled, then this method
     * schedules the periodic work. See {@link #schedulePeriodicUpload()} for more details. If the backup is
     * being disabled, {@link #unschedulePeriodicUpload()} is also called.
     *
     * @param enabled {@code true} if the backup should be enabled, {@code false} otherwise
     */
    void setEnabled(boolean enabled);

    /**
     * Create a one time work request to create and upload a Threema Safe backup. This method
     * cancels already enqueued one time work requests. If a periodic work request is scheduled, it
     * gets rescheduled, so that the schedule period is still met.
     *
     * @param force If set to {@code true}, the backup is created even if the last backup was
     *              created within the last 24 hours or there are no new changes since the last
     *              backup. See {@link #createBackup(boolean)} for more details.
     */
    void uploadNow(boolean force);

    /**
     * Create and upload a Threema Safe backup.
     *
     * @param force If set to {@code true}, the backup is created and uploaded in any case. If this
     *              is {@code false}, then the backup is only uploaded if it is different than the
     *              last Threema Safe backup or the last backup is older than half of the server's
     *              retention time. The grace time of 23 hours is only respected when this parameter
     *              is {@code false}.
     * @throws ThreemaSafeUploadException If an error occurs with the backup or the connection.
     */
    void createBackup(boolean force) throws ThreemaSafeUploadException;

    void deleteBackup() throws ThreemaException;

    void restoreBackup(String identity, String password, ThreemaSafeServerInfo serverInfo) throws ThreemaException, IOException;

    /**
     * Search a Threema ID by phone number and/or email address.
     *
     * @return ArrayList of matching Threema IDs, null if none was found
     */
    @Nullable
    ArrayList<String> searchID(String phone, String email);

    /**
     * Launch the password dialog to setup Threema Safe.
     *
     * @param activity         the activity that starts the threema safe config activity
     * @param openHomeActivity if set to true, the home activity is started after successfully choosing a password
     */
    void launchForcedPasswordDialog(Activity activity, boolean openHomeActivity);
}
